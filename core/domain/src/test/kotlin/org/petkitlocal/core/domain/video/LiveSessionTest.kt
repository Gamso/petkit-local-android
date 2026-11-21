package org.petkitlocal.core.domain.video

import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason

class LiveSessionTest {
    private val busy = LiveFailure.StreamError("camera busy")
    private val network = LiveFailure.Network("reset")

    /** A session started once and failed with each of [failures] while starting, waiting in between. */
    private fun failedStarts(vararg failures: LiveFailure): LiveSession =
        failures.fold(LiveSession().start()) { session, failure ->
            val failed = session.fail(session.attempt, failure)
            if (failed.phase is LivePhase.Waiting) failed.retryDue() else failed
        }

    @Test
    fun `it starts on the WebSocket and plays`() {
        val started = LiveSession().start()
        assertEquals(LiveSession(LiveTransport.WebSocket, LivePhase.Starting, failedStarts = 0, attempt = 1), started)
        assertEquals(LivePhase.Playing, started.playing(attempt = 1).phase)
    }

    @Test
    fun `an error message waits a few seconds, then starts again with a new attempt`() {
        val waiting = LiveSession().start().fail(1, busy)
        assertEquals(LivePhase.Waiting(busy, 3.seconds), waiting.phase)
        assertEquals(1, waiting.failedStarts)
        val again = waiting.retryDue()
        assertEquals(LivePhase.Starting, again.phase)
        assertEquals(2, again.attempt)
        assertEquals(LiveTransport.WebSocket, again.transport)
    }

    @Test
    fun `two failed starts on the WebSocket move to HLS after the wait`() {
        val second = LiveSession().start().fail(1, network).retryDue().fail(2, busy)
        assertEquals(LiveTransport.Hls, second.transport)
        assertEquals(LivePhase.Waiting(busy, LiveSession.RETRY_DELAY), second.phase)
        assertEquals(0, second.failedStarts)
        assertEquals(LivePhase.Starting, second.retryDue().phase)
        assertEquals(LiveTransport.Hls, second.retryDue().transport)
    }

    @Test
    fun `playing in between resets the count of failed starts`() {
        val session = LiveSession().start().fail(1, network).retryDue().playing(2)
        val cut = session.fail(2, network)
        assertEquals(0, cut.failedStarts)
        assertEquals(LivePhase.Waiting(network, LiveSession.RETRY_DELAY), cut.phase)
        assertEquals(LiveTransport.WebSocket, cut.retryDue().fail(3, network).transport)
    }

    @Test
    fun `close code 1013 moves to HLS at once`() {
        val session = LiveSession().start().playing(1).fail(1, LiveFailure.TooSlow)
        assertEquals(LiveTransport.Hls, session.transport)
        assertEquals(LivePhase.Starting, session.phase)
        assertEquals(2, session.attempt)
    }

    @Test
    fun `three failed starts on HLS leave it to the user, whose retry starts from the WebSocket`() {
        val stopped = failedStarts(network, network, network, network, busy)
        assertEquals(LiveTransport.Hls, stopped.transport)
        assertEquals(LivePhase.Stopped(busy), stopped.phase)
        assertEquals(stopped, stopped.start())

        val retried = stopped.retry()
        assertEquals(LiveTransport.WebSocket, retried.transport)
        assertEquals(LivePhase.Starting, retried.phase)
        assertEquals(0, retried.failedStarts)
    }

    @Test
    fun `the stream ending and a replaced view stop until the user retries`() {
        for (failure in listOf(LiveFailure.StreamEnded, LiveFailure.Replaced)) {
            val stopped = LiveSession().start().playing(1).fail(1, failure)
            assertEquals(LivePhase.Stopped(failure), stopped.phase)
            assertEquals(stopped, stopped.start())
            assertEquals(stopped, stopped.retryDue())
            assertEquals(LivePhase.Starting, stopped.retry().phase)
        }
    }

    @Test
    fun `no stream and no room stop it, an unavailable stream is retried`() {
        val notFound = LiveFailure.Refused(DomainError.NotFound("stream_not_found", ""))
        val limit = LiveFailure.Refused(DomainError.RateLimited(5.seconds))
        val unavailable = LiveFailure.Refused(DomainError.Server(503, "live_unavailable", ""))
        assertEquals(LivePhase.Stopped(notFound), LiveSession().start().fail(1, notFound).phase)
        assertEquals(LivePhase.Stopped(limit), LiveSession().start().fail(1, limit).phase)
        val retried = LiveSession().start().fail(1, unavailable)
        assertEquals(LivePhase.Waiting(unavailable, LiveSession.RETRY_DELAY), retried.phase)
    }

    @Test
    fun `the session ending, by close code or by a refusal, ends it for good`() {
        val refused = LiveFailure.Refused(DomainError.SessionEnded(SessionEndReason.InvalidToken))
        for (failure in listOf(LiveFailure.SessionEnded, refused)) {
            val ended = LiveSession().start().fail(1, failure)
            assertEquals(LivePhase.SessionEnded, ended.phase)
            assertEquals(ended, ended.start())
            assertEquals(ended, ended.retry())
        }
    }

    @Test
    fun `a late event from a replaced player changes nothing`() {
        val second = LiveSession().start().fail(1, busy).retryDue()
        assertEquals(second, second.fail(attempt = 1, LiveFailure.StreamEnded))
        assertEquals(second, second.playing(attempt = 1))
    }

    @Test
    fun `stopping in the background keeps the transport, and coming back starts again`() {
        val onHls = LiveSession().start().fail(1, LiveFailure.TooSlow).playing(2)
        val stopped = onHls.stop()
        assertEquals(LivePhase.Idle, stopped.phase)
        assertEquals(stopped, stopped.fail(2, network))
        val resumed = stopped.start()
        assertEquals(LivePhase.Starting, resumed.phase)
        assertEquals(LiveTransport.Hls, resumed.transport)
        assertEquals(3, resumed.attempt)

        // A wait cut short by the background starts at once on return.
        val waiting = LiveSession().start().fail(1, busy).stop()
        assertEquals(LivePhase.Idle, waiting.phase)
    }

    @Test
    fun `a slow start is marked for its own attempt only, and goes with the start`() {
        val starting = LiveSession().start()
        val slow = starting.slowStart(attempt = 1)
        assertEquals(true, slow.startingSlowly)
        assertEquals(LivePhase.Starting, slow.phase)
        assertEquals(starting, starting.slowStart(attempt = 0), "an older attempt's clock changes nothing")
        assertEquals(false, slow.playing(1).startingSlowly)
        assertEquals(false, slow.fail(1, network).startingSlowly)
        assertEquals(false, slow.stop().startingSlowly)
        assertEquals(false, slow.retry().startingSlowly)
        val playing = starting.playing(1)
        assertEquals(playing, playing.slowStart(attempt = 1), "only a start can be slow")
    }

    @Test
    fun `released to another screen, it stops and its next start is a fresh one`() {
        val stopped = LiveSession().start().fail(1, LiveFailure.StreamEnded)
        assertEquals(LivePhase.Stopped(LiveFailure.StreamEnded), stopped.start().phase, "only the user restarts it")

        val released = stopped.release()
        assertEquals(LivePhase.Idle, released.phase)
        assertEquals(LivePhase.Starting, released.start().phase)
        assertEquals(2, released.start().attempt)

        val onHls = LiveSession().start().playing(1).fail(1, LiveFailure.TooSlow).release()
        assertEquals(LiveTransport.WebSocket, onHls.transport)
        assertEquals(LivePhase.Idle, onHls.phase)

        val ended = LiveSession().start().fail(1, LiveFailure.SessionEnded)
        assertEquals(ended, ended.release())
    }
}

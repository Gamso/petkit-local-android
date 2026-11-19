package org.petkitlocal.core.data.talk

import app.cash.turbine.test
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.petkitlocal.core.domain.talk.TalkEvent
import org.petkitlocal.core.domain.talk.TalkFailure
import org.petkitlocal.core.network.TalkConnection
import org.petkitlocal.core.network.TalkSocket
import org.petkitlocal.core.network.TalkSocketEvent

/** The talk's order of things: socket, `ready`, microphone, stop; and every way it ends. */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent, advanceTimeBy.
class TalkRepositoryImplTest {
    /** A scripted socket: the test says what the server sends; it records what the app did. */
    private class FakeConnection : TalkConnection {
        private val channel = Channel<TalkSocketEvent>(Channel.UNLIMITED)
        val log = mutableListOf<String>()
        var closed: TalkFailure? = null
        var isClosed = false

        override val events: Flow<TalkSocketEvent> = channel.receiveAsFlow()

        fun server(event: TalkSocketEvent) {
            channel.trySend(event)
            if (event is TalkSocketEvent.Ended) channel.close()
        }

        override fun send(pcm: ByteArray): Boolean {
            log += "audio:${pcm.size}"
            return !isClosed
        }

        override fun stop() {
            log += "stop"
        }

        override fun close(failure: TalkFailure?) {
            if (isClosed) return
            isClosed = true
            closed = failure
            log += "close"
            server(TalkSocketEvent.Ended(failure))
        }
    }

    /** Chunks the test pushes; it counts how often it was opened and released. */
    private class FakeMicrophone : Microphone {
        val chunks = MutableSharedFlow<ByteArray>()
        var opened = 0
        var released = 0
        var unavailable = false

        override fun record(): Flow<ByteArray> = flow {
            opened++
            try {
                if (unavailable) throw MicrophoneUnavailableException("in use")
                chunks.collect { emit(it) }
            } finally {
                released++
            }
        }
    }

    private val connection = FakeConnection()
    private val microphone = FakeMicrophone()
    private val opened = mutableListOf<Long>()
    private val socket = TalkSocket { id ->
        opened += id
        connection
    }
    private val repository = TalkRepositoryImpl(socket, microphone, 5.seconds)

    @Test
    fun `the microphone opens only once the camera is ready, and its chunks go to the socket`() = runTest {
        val talk = repository.open(1003)
        talk.events.test {
            runCurrent()
            assertEquals(listOf(1003L), opened)
            assertEquals(0, microphone.opened)

            connection.server(TalkSocketEvent.Ready)
            assertEquals(TalkEvent.Talking, awaitItem())
            runCurrent()
            assertEquals(1, microphone.opened)
            microphone.chunks.emit(ByteArray(1280))
            microphone.chunks.emit(ByteArray(640))
            assertEquals(listOf("audio:1280", "audio:640"), connection.log)

            talk.stop()
            runCurrent()
            assertEquals(1, microphone.released)
            assertEquals("stop", connection.log.last())
            connection.server(TalkSocketEvent.Stopped)
            connection.server(TalkSocketEvent.Ended(null))
            assertEquals(TalkEvent.Ended(null), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `a server that does not close after a stop is closed after the timeout`() = runTest {
        val talk = repository.open(1003)
        talk.events.test {
            connection.server(TalkSocketEvent.Ready)
            assertEquals(TalkEvent.Talking, awaitItem())
            talk.stop()
            advanceTimeBy(4.seconds)
            assertTrue(!connection.isClosed)
            advanceTimeBy(2.seconds)
            assertEquals(TalkEvent.Ended(null), awaitItem())
            awaitComplete()
        }
        assertEquals(listOf("stop", "close"), connection.log)
    }

    @Test
    fun `a stop before the camera accepted closes at once, with no microphone`() = runTest {
        val talk = repository.open(1003)
        talk.events.test {
            runCurrent()
            talk.stop()
            assertEquals(TalkEvent.Ended(null), awaitItem())
            awaitComplete()
        }
        assertEquals(listOf("close"), connection.log)
        assertEquals(0, microphone.opened)
    }

    @Test
    fun `the server's error ends the talk and releases the microphone`() = runTest {
        repository.open(1003).events.test {
            connection.server(TalkSocketEvent.Ready)
            assertEquals(TalkEvent.Talking, awaitItem())
            runCurrent()
            connection.server(TalkSocketEvent.Ended(TalkFailure.DeviceUnreachable))
            assertEquals(TalkEvent.Ended(TalkFailure.DeviceUnreachable), awaitItem())
            awaitComplete()
        }
        assertEquals(1, microphone.released)
    }

    @Test
    fun `a refusal before ready never opens the microphone`() = runTest {
        repository.open(1003).events.test {
            connection.server(TalkSocketEvent.Ended(TalkFailure.Busy))
            assertEquals(TalkEvent.Ended(TalkFailure.Busy), awaitItem())
            awaitComplete()
        }
        assertEquals(0, microphone.opened)
    }

    @Test
    fun `a microphone that cannot open ends the talk with that reason`() = runTest {
        microphone.unavailable = true
        repository.open(1003).events.test {
            connection.server(TalkSocketEvent.Ready)
            assertEquals(TalkEvent.Talking, awaitItem())
            assertEquals(TalkEvent.Ended(TalkFailure.MicrophoneUnavailable), awaitItem())
            awaitComplete()
        }
        assertEquals(TalkFailure.MicrophoneUnavailable, connection.closed)
    }

    @Test
    fun `leaving cancels the collection, which closes the socket and releases the microphone`() = runTest {
        repository.open(1003).events.test {
            connection.server(TalkSocketEvent.Ready)
            assertEquals(TalkEvent.Talking, awaitItem())
            runCurrent()
            cancel()
        }
        runCurrent()
        assertTrue(connection.isClosed)
        assertEquals(1, microphone.released)
    }
}

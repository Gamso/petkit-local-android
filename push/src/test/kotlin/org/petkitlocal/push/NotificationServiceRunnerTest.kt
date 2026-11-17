package org.petkitlocal.push

import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.notifications.NotificationConnection
import org.petkitlocal.core.domain.notifications.NotificationFeed
import org.petkitlocal.core.domain.notifications.NotificationFeedEvent
import org.petkitlocal.core.domain.notifications.NotificationSubject
import org.petkitlocal.core.domain.notifications.ServerNotification

/**
 * The service's logic with a scripted feed: it holds the feed only while wanted, shows each alert
 * with what it is about, makes one sound for a batch of replayed alerts, and returns (the service
 * then stops) when it is no longer wanted or the feed ends for good.
 */
@OptIn(ExperimentalCoroutinesApi::class) // runCurrent
class NotificationServiceRunnerTest {
    private val wanted = MutableStateFlow<Boolean?>(null)
    private val feed = ScriptedFeed()
    private val surface = RecordingSurface()
    private val runner = NotificationServiceRunner(
        wanted = wanted.filterNotNull(),
        feed = feed,
        describe = { NotificationSubject(device = null, petName = "Mochi".takeIf { _ -> it.petId == 1L }) },
        surface = surface,
    )

    @Test
    fun `not wanted, it returns at once without opening anything`() = runTest {
        wanted.value = false
        runner.run(emptyFlow())

        assertEquals(0, feed.runs)
    }

    @Test
    fun `it waits for the session to be read before deciding`() = runTest {
        var done = false
        launch {
            runner.run(emptyFlow())
            done = true
        }
        runCurrent()
        assertFalse(done)
        assertEquals(0, feed.runs)

        wanted.value = true
        runCurrent()
        assertEquals(1, feed.runs)
        wanted.value = false
        runCurrent()
        assertTrue(done)
    }

    @Test
    fun `alerts are shown with what they are about, and the connection's state is said`() = runTest {
        wanted.value = true
        launch { runner.run(emptyFlow()) }
        runCurrent()

        feed.send(NotificationFeedEvent.Status(NotificationConnection.Connected(gap = false)))
        feed.send(NotificationFeedEvent.Show(notification(1, replay = false, petId = 1)))
        runCurrent()

        assertEquals(listOf<NotificationConnection>(NotificationConnection.Connected(gap = false)), surface.statuses)
        assertEquals(listOf(Shown(1, "Mochi", silent = false)), surface.shown)
        wanted.value = false
    }

    @Test
    fun `a batch of replayed alerts makes one sound, and the next connection's batch one more`() = runTest {
        wanted.value = true
        launch { runner.run(emptyFlow()) }
        runCurrent()

        feed.send(NotificationFeedEvent.Show(notification(1, replay = true)))
        feed.send(NotificationFeedEvent.Show(notification(2, replay = true)))
        feed.send(NotificationFeedEvent.Show(notification(3, replay = false)))
        feed.send(NotificationFeedEvent.Status(NotificationConnection.Waiting(DomainError.Network(), 1.seconds)))
        feed.send(NotificationFeedEvent.Status(NotificationConnection.Connecting))
        feed.send(NotificationFeedEvent.Show(notification(4, replay = true)))
        runCurrent()

        assertEquals(listOf(false, true, false, false), surface.shown.map { it.silent })
        wanted.value = false
    }

    @Test
    fun `turned off or signed out, the feed is closed and it returns`() = runTest {
        wanted.value = true
        var done = false
        launch {
            runner.run(emptyFlow())
            done = true
        }
        runCurrent()
        assertTrue(feed.open)

        wanted.value = false
        runCurrent()

        assertFalse(feed.open)
        assertTrue(done)
    }

    @Test
    fun `the feed ending for good ends the run`() = runTest {
        wanted.value = true
        var done = false
        launch {
            runner.run(emptyFlow())
            done = true
        }
        runCurrent()

        feed.send(NotificationFeedEvent.Status(NotificationConnection.Ended(sessionEnded = true)))
        feed.end()
        runCurrent()

        assertTrue(done)
    }

    @Test
    fun `an alert whose subject cannot be read is still shown`() = runTest {
        val failing = NotificationServiceRunner(wanted.filterNotNull(), feed, { error("no cache") }, surface)
        wanted.value = true
        launch { failing.run(emptyFlow()) }
        runCurrent()

        feed.send(NotificationFeedEvent.Show(notification(5, replay = false, petId = 1)))
        runCurrent()

        assertEquals(listOf(Shown(5, null, silent = false)), surface.shown)
        wanted.value = false
    }

    private fun notification(id: Long, replay: Boolean, petId: Long? = null) =
        ServerNotification(id, replay, "toilet", deviceId = 1, petId = petId, code = "10", time = Instant.EPOCH)

    private data class Shown(val id: Long, val pet: String?, val silent: Boolean)

    private class RecordingSurface : NotificationSurface {
        val statuses = mutableListOf<NotificationConnection>()
        val shown = mutableListOf<Shown>()

        override fun connection(status: NotificationConnection) {
            statuses += status
        }

        override fun show(notification: ServerNotification, subject: NotificationSubject, silent: Boolean) {
            shown += Shown(notification.id, subject.petName, silent)
        }
    }

    /** Each run is fed by [send] and ended by [end]; [open] while one is collected. */
    private class ScriptedFeed : NotificationFeed {
        private var channel = Channel<NotificationFeedEvent>(Channel.UNLIMITED)
        var runs = 0
            private set
        var open = false
            private set

        override fun run(networkBack: Flow<Unit>): Flow<NotificationFeedEvent> {
            runs++
            channel = Channel(Channel.UNLIMITED)
            return channel.consumeAsFlow()
                .onStart { open = true }
                .onCompletion { open = false }
        }

        fun send(event: NotificationFeedEvent) {
            channel.trySend(event)
        }

        fun end() {
            channel.close()
        }
    }
}

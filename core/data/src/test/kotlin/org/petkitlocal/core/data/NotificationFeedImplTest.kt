package org.petkitlocal.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.petkitlocal.core.datastore.NotificationStore
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ServerAddress
import org.petkitlocal.core.domain.auth.ServerInfo
import org.petkitlocal.core.domain.auth.Session
import org.petkitlocal.core.domain.notifications.NotificationConnection
import org.petkitlocal.core.domain.notifications.NotificationFeedEvent
import org.petkitlocal.core.network.NotificationSocket
import org.petkitlocal.core.network.NotificationSocketEvent
import org.petkitlocal.core.network.dto.NotificationSocketMessageDto
import org.petkitlocal.core.network.dto.PushMessageDto

/**
 * The notification feed against a scripted socket, a real [NotificationStore] and the test
 * clock: what is shown once and only once across replays and reconnections, the `after` each
 * socket is opened with, the backoff, the network cutting a wait short, and the close codes that
 * end it for good.
 */
@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy, runCurrent
class NotificationFeedImplTest {
    @TempDir
    lateinit var dir: File

    private val socket = ScriptedSocket()
    private val auth = StubAuth()
    private val networkBack = MutableSharedFlow<Unit>()

    // No jitter: a wait is exactly the exponential ceiling, 1 s, 2 s, 4 s...
    private val backoff = LiveUpdatesBackoff(base = 1.seconds, cap = 30.seconds, random = NoJitter)

    private lateinit var store: NotificationStore
    private val events = mutableListOf<NotificationFeedEvent>()

    private fun TestScope.start() {
        store =
            NotificationStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) {
                    File(dir, "n.preferences_pb")
                },
            )
        val feed = NotificationFeedImpl(socket, store, auth, backoff)
        backgroundScope.launch { feed.run(networkBack).collect { events += it } }
        runCurrent()
    }

    private val shown get() = events.filterIsInstance<NotificationFeedEvent.Show>().map { it.notification.id }
    private val statuses get() = events.filterIsInstance<NotificationFeedEvent.Status>().map { it.status }

    @Test
    fun `the first connection starts from now, and each live notification is shown once`() = runTest {
        start()

        assertEquals(listOf<Long?>(null), socket.afters)
        socket.send(hello(lastId = 7))
        socket.send(delivery(8, replay = false, kind = "toilet", deviceId = 100, petId = 2, code = "10"))
        runCurrent()

        assertEquals(listOf(8L), shown)
        val notification = (events.last { it is NotificationFeedEvent.Show } as NotificationFeedEvent.Show).notification
        assertEquals("toilet", notification.kind)
        assertEquals(100L, notification.deviceId)
        assertEquals(2L, notification.petId)
        assertEquals("10", notification.code)
        assertEquals(TS, notification.time.epochSecond)
        assertEquals(8L, store.lastId())
        assertEquals(
            listOf(NotificationConnection.Connecting, NotificationConnection.Connected(gap = false)),
            statuses,
        )
    }

    @Test
    fun `a dropped socket is reopened after the backoff with the last id, and the replay is not shown twice`() =
        runTest {
            start()
            socket.send(hello(lastId = 7))
            socket.send(delivery(8, replay = false))
            socket.close(DomainError.Network("reset"))
            runCurrent()
            assertEquals(NotificationConnection.Waiting(DomainError.Network("reset"), 1.seconds), statuses.last())

            advanceTimeBy(999)
            runCurrent()
            assertEquals(1, socket.afters.size)
            advanceTimeBy(1)
            runCurrent()

            assertEquals(listOf(null, 8L), socket.afters)
            // 8 again (already shown), then two missed, then ids up to 12 that were not for this phone.
            socket.send(hello(lastId = 12, replayed = 3))
            socket.send(delivery(8, replay = true))
            socket.send(delivery(9, replay = true))
            socket.send(delivery(10, replay = true))
            runCurrent()

            assertEquals(listOf(8L, 9L, 10L), shown)
            assertEquals(12L, store.lastId())
        }

    @Test
    fun `the backoff grows while connections fail and starts again after a hello`() = runTest {
        start()
        socket.close(DomainError.Network("down"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        socket.close(DomainError.Network("down"))
        runCurrent()

        assertEquals(2.seconds, (statuses.last() as NotificationConnection.Waiting).retryIn)

        advanceTimeBy(2_000)
        runCurrent()
        socket.send(hello(lastId = 0))
        socket.close(DomainError.Network("down"))
        runCurrent()

        assertEquals(1.seconds, (statuses.last() as NotificationConnection.Waiting).retryIn)
    }

    @Test
    fun `the network coming back cuts the wait short`() = runTest {
        start()
        socket.close(DomainError.Network("down"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        socket.close(DomainError.Network("down"))
        runCurrent()
        assertEquals(2, socket.afters.size)

        advanceTimeBy(100)
        networkBack.emit(Unit)
        runCurrent()

        assertEquals(3, socket.afters.size)
    }

    @Test
    fun `4401 ends the session and the feed, with no reconnection`() = runTest {
        start()
        socket.send(hello(lastId = 1))
        socket.close(DomainError.SessionEnded(SessionEndReason.ClosedByServer))
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(listOf(SessionEndReason.ClosedByServer), auth.ended)
        assertEquals(NotificationConnection.Ended(sessionEnded = true), statuses.last())
        assertEquals(1, socket.afters.size)
    }

    @Test
    fun `a socket replaced by another of the session ends the feed without signing out`() = runTest {
        start()
        socket.close(null, replaced = true)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(NotificationConnection.Ended(sessionEnded = false), statuses.last())
        assertTrue(auth.ended.isEmpty())
        assertEquals(1, socket.afters.size)
    }

    @Test
    fun `a server whose log was lost replays from its first id, and all of it is new`() = runTest {
        start()
        store.saveLastId(50)
        socket.close(DomainError.Network("restart"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(50L, socket.afters.last())

        socket.send(hello(lastId = 2, replayed = 2, gap = true))
        socket.send(delivery(1, replay = true))
        socket.send(delivery(2, replay = true))
        runCurrent()

        assertEquals(listOf(1L, 2L), shown)
        assertEquals(2L, store.lastId())
        assertTrue(NotificationConnection.Connected(gap = true) in statuses)
    }

    @Test
    fun `a refused after is forgotten, and the next socket starts from now`() = runTest {
        start()
        store.saveLastId(9)
        socket.close(DomainError.Validation(null, "bad_parameter", "after must be a non-negative integer"))
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(null, socket.afters.last())
    }

    @Test
    fun `signed out, the feed ends at once`() = runTest {
        auth.authState.value = AuthState.SignedOut(null)
        start()

        assertEquals(listOf<NotificationConnection>(NotificationConnection.Ended(sessionEnded = true)), statuses)
        assertTrue(socket.afters.isEmpty())
    }

    private fun hello(lastId: Long, replayed: Long = 0, gap: Boolean = false) =
        NotificationSocketMessageDto.Hello(1.0, 45.0, lastId, replayed, gap)

    @Suppress("LongParameterList") // A message's fields.
    private fun delivery(
        id: Long,
        replay: Boolean,
        kind: String = "error",
        deviceId: Long? = 1,
        petId: Long? = null,
        code: String? = null,
    ) = NotificationSocketMessageDto.Delivery(id, replay, PushMessageDto(1, kind, deviceId, petId, code, TS))

    private object NoJitter : Random() {
        override fun nextBits(bitCount: Int): Int = Int.MAX_VALUE ushr (Int.SIZE_BITS - bitCount)

        override fun nextDouble(): Double = 1.0
    }

    /** Each `open` is a new socket, fed by [send] and ended by [close]; [afters] lists each `after`. */
    private class ScriptedSocket : NotificationSocket {
        private var current = Channel<NotificationSocketEvent>(Channel.UNLIMITED)
        val afters = mutableListOf<Long?>()

        override fun open(after: Long?): Flow<NotificationSocketEvent> {
            afters += after
            current = Channel(Channel.UNLIMITED)
            return current.consumeAsFlow().transformWhile {
                emit(it)
                it !is NotificationSocketEvent.Closed
            }
        }

        fun send(message: NotificationSocketMessageDto) {
            current.trySend(NotificationSocketEvent.Message(message))
        }

        fun close(error: DomainError?, replaced: Boolean = false) {
            current.trySend(NotificationSocketEvent.Closed(error, replaced))
        }
    }

    private class StubAuth : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.SignedIn("https://pets.example.com"))
        val ended = mutableListOf<SessionEndReason>()

        override suspend fun lastServerBaseUrl(): String? = null

        override suspend fun probe(server: ServerAddress): Outcome<ServerInfo> = error("not used")

        override suspend fun signIn(server: ServerAddress, password: String, deviceName: String?): Outcome<Session> =
            error("not used")

        override suspend fun signOut() = error("not used")

        override suspend fun serverInfo(): Outcome<ServerInfo> = error("not used")

        override suspend fun changePassword(current: String, new: String): Outcome<Int> = error("not used")

        override suspend fun validateSession() = Unit

        override suspend fun endSession(reason: SessionEndReason) {
            ended += reason
            authState.value = AuthState.SignedOut(reason)
        }
    }

    private companion object {
        const val TS = 1_763_150_000L
    }
}

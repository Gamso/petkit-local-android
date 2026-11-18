package org.petkitlocal.core.data

import app.cash.turbine.test
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ServerAddress
import org.petkitlocal.core.domain.auth.ServerInfo
import org.petkitlocal.core.domain.auth.Session
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.core.network.LiveUpdate
import org.petkitlocal.core.network.LiveUpdatesSocket
import org.petkitlocal.core.network.dto.DeviceDetailDto
import org.petkitlocal.core.network.dto.DeviceSummaryDto
import org.petkitlocal.core.network.dto.EntityChangeDto
import org.petkitlocal.core.network.dto.EntityDto
import org.petkitlocal.core.network.dto.TimelineItemDto
import org.petkitlocal.core.network.dto.UpdateMessageDto
import org.robolectric.RobolectricTestRunner

/**
 * The live-updates repository against a scripted socket, a scripted server (for the detail of a
 * device that appears) and a real Room database: each message lands in the cache, the socket is
 * reopened with backoff, 4401 ends the session, and nothing is open without a collector.
 */
@OptIn(ExperimentalCoroutinesApi::class) // advanceTimeBy, runCurrent
@RunWith(RobolectricTestRunner::class)
class LiveUpdatesRepositoryImplTest {
    @get:Rule
    val server = ScriptedServer()

    private val socket = ScriptedSocket()
    private val auth = StubAuth()
    private val zone = ZoneId.of("Europe/Paris")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 15, 10, 0, 0, 0, zone).toInstant(), zone)

    // No jitter: a wait is exactly the exponential ceiling, 1 s, 2 s, 4 s...
    private val backoff = LiveUpdatesBackoff(base = 1.seconds, cap = 30.seconds, random = NoJitter)

    private val devices by lazy { DeviceRepositoryImpl(server.api, server.db.deviceDao()) }
    private val timeline by lazy { TimelineRepositoryImpl(server.api, server.db.timelineDao(), clock) { server.base } }

    private fun TestScope.repository() = LiveUpdatesRepositoryImpl(
        socket = socket,
        auth = auth,
        api = server.api,
        devices = server.db.deviceDao(),
        timeline = server.db.timelineDao(),
        clock = clock,
        backoff = backoff,
        scope = backgroundScope,
    )

    @Test
    fun `hello replaces the devices, and the status says live`() = runTest {
        val repository = repository()
        // A device cached earlier that the server no longer has.
        server.db.deviceDao().upsertSummary(summary(9).toRow(0, null))

        repository.status.test(timeout = ROOM_TIMEOUT) {
            assertEquals(LiveUpdatesStatus.Connecting, awaitItem())
            socket.send(hello(detail(1, entities = listOf(entity("volume", JsonPrimitive(5))))))
            assertEquals(LiveUpdatesStatus.Live, awaitItem())

            val cached = devices.observeDevices().first()
            assertEquals(listOf(1L), cached.map { it.id })
            assertEquals(EntityValue.Number(5.0), cached.single().entity("volume")!!.value)
            assertEquals("SN-1", cached.single().serial)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state, device and device_removed patch the cache`() = runTest {
        val repository = repository()
        server.on("devices/7", """{"device":${detailJson(7)}}""")

        repository.status.test(timeout = ROOM_TIMEOUT) {
            skipItems(1)
            socket.send(hello(detail(1, entities = listOf(entity("volume", JsonPrimitive(5)))), detail(2)))
            skipItems(1)

            socket.send(UpdateMessageDto.State(1, 0.0, listOf(EntityChangeDto("volume", JsonPrimitive(7), null))))
            socket.send(UpdateMessageDto.Device(summary(1, online = false)))
            // A new device: its detail is fetched, since the message has only its summary.
            socket.send(UpdateMessageDto.Device(summary(7)))
            socket.send(UpdateMessageDto.DeviceRemoved(2))
            socket.awaitDelivered()

            val cached = devices.observeDevices().first()
            assertEquals(listOf(1L, 7L), cached.map { it.id })
            with(cached[0]) {
                assertEquals(EntityValue.Number(7.0), entity("volume")!!.value)
                assertEquals(false, online)
                assertEquals("SN-1", serial)
            }
            assertEquals(listOf("cleaning_start"), cached[1].actions.map { it.key })
            assertEquals("SN-7", cached[1].serial)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a timeline card lands on its local day and drops the cards it replaces`() = runTest {
        val repository = repository()
        val today = clock.instant().atZone(zone).toLocalDate()

        repository.status.test(timeout = ROOM_TIMEOUT) {
            skipItems(1)
            socket.send(hello())
            skipItems(1)
            // 09:00 and 09:05 in Paris on the 15th; then 00:30 on the 15th, which is the 14th in UTC.
            socket.send(UpdateMessageDto.Timeline(item(100, epoch(9, 0)), replaces = emptyList()))
            socket.send(UpdateMessageDto.Timeline(item(101, epoch(9, 5)), replaces = listOf(100)))
            socket.send(UpdateMessageDto.Timeline(item(102, epoch(0, 30)), replaces = emptyList()))
            socket.awaitDelivered()

            assertEquals(listOf(101L, 102L), timeline.observeDay(today).first().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a socket that closes is reopened with a growing wait, reset by the next hello`() = runTest {
        val repository = repository()

        repository.status.test(timeout = ROOM_TIMEOUT) {
            assertEquals(LiveUpdatesStatus.Connecting, awaitItem())
            socket.close(DomainError.Network("reset"))
            assertEquals(LiveUpdatesStatus.Waiting(DomainError.Network("reset"), 1.seconds), awaitItem())
            assertEquals(LiveUpdatesStatus.Connecting, awaitItem())
            socket.close(DomainError.Network("reset"))
            assertEquals(LiveUpdatesStatus.Waiting(DomainError.Network("reset"), 2.seconds), awaitItem())
            assertEquals(LiveUpdatesStatus.Connecting, awaitItem())
            socket.send(hello())
            assertEquals(LiveUpdatesStatus.Live, awaitItem())
            // The server going away (1001) is a reconnect like any other, from 1 s again.
            socket.close(DomainError.Network("WebSocket closed with code 1001"))
            assertEquals(
                LiveUpdatesStatus.Waiting(DomainError.Network("WebSocket closed with code 1001"), 1.seconds),
                awaitItem(),
            )
            assertEquals(LiveUpdatesStatus.Connecting, awaitItem())
            assertEquals(4, socket.opened)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `close code 4401 ends the session and stops reconnecting`() = runTest {
        val repository = repository()

        repository.status.test(timeout = ROOM_TIMEOUT) {
            skipItems(1)
            socket.send(hello())
            skipItems(1)
            socket.close(DomainError.SessionEnded(SessionEndReason.ClosedByServer))

            assertEquals(LiveUpdatesStatus.Stopped, awaitItem())
            assertEquals(listOf(SessionEndReason.ClosedByServer), auth.ended)
            advanceTimeBy(60.seconds)
            assertEquals(1, socket.opened)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signed out, no socket is opened`() = runTest {
        auth.authState.value = AuthState.SignedOut(null)
        val repository = repository()

        repository.status.test(timeout = ROOM_TIMEOUT) {
            assertEquals(LiveUpdatesStatus.Stopped, awaitItem())
            runCurrent()
            assertEquals(0, socket.opened)

            auth.authState.value = SIGNED_IN
            assertEquals(LiveUpdatesStatus.Connecting, awaitItem())
            assertEquals(1, socket.opened)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the socket is shared, and closed five seconds after the last collector leaves`() = runTest {
        val repository = repository()

        repository.status.test(timeout = ROOM_TIMEOUT) {
            skipItems(1)
            repository.status.test(timeout = ROOM_TIMEOUT) { cancelAndIgnoreRemainingEvents() }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, socket.opened)
        advanceTimeBy(4.seconds)
        assertEquals(0, socket.closed)
        advanceTimeBy(2.seconds)
        assertEquals(1, socket.closed)
    }

    // --- fakes and fixtures

    private object NoJitter : Random() {
        override fun nextBits(bitCount: Int): Int = Int.MAX_VALUE ushr (Int.SIZE_BITS - bitCount)

        override fun nextDouble(): Double = 1.0
    }

    /** Each `updates()` is a new socket, fed by [send] and ended by [close]. */
    private class ScriptedSocket : LiveUpdatesSocket {
        private var current = Channel<LiveUpdate>(Channel.UNLIMITED)
        private val pending = MutableStateFlow(0)
        var opened = 0
            private set
        var closed = 0
            private set

        override fun updates(): Flow<LiveUpdate> {
            opened++
            val channel = Channel<LiveUpdate>(Channel.UNLIMITED).also { current = it }
            return flow {
                try {
                    for (update in channel) {
                        emit(update)
                        // emit returns once the repository has applied the message.
                        if (update is LiveUpdate.Message) pending.update { it - 1 }
                    }
                } finally {
                    closed++
                }
            }
        }

        fun send(message: UpdateMessageDto) {
            pending.update { it + 1 }
            current.trySend(LiveUpdate.Message(message))
        }

        fun close(error: DomainError?) {
            current.trySend(LiveUpdate.Closed(error))
            current.close()
        }

        /** Until every message sent has been applied. */
        suspend fun awaitDelivered() {
            pending.first { it == 0 }
        }
    }

    private class StubAuth : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(SIGNED_IN)
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

    private fun epoch(hour: Int, minute: Int): Double =
        ZonedDateTime.of(2026, 11, 15, hour, minute, 0, 0, zone).toEpochSecond().toDouble()

    private fun hello(vararg devices: DeviceDetailDto) = UpdateMessageDto.Hello(0.0, 25.0, devices.toList())

    private fun summary(id: Long, online: Boolean = true) = DeviceSummaryDto(
        id = id,
        type = "w5",
        model = "EverSweet 3 Pro",
        kind = "fountain",
        online = online,
        transport = "http",
        lastSeen = 1.0,
        firmware = "1.0",
        isCamera = false,
        connection = "wifi",
        parentId = null,
        accessories = emptyList(),
    )

    private fun detail(id: Long, entities: List<EntityDto> = emptyList()) = DeviceDetailDto(
        id = id,
        type = "w5",
        model = "EverSweet 3 Pro",
        kind = "fountain",
        online = true,
        transport = "http",
        lastSeen = 1.0,
        firmware = "1.0",
        isCamera = false,
        connection = "wifi",
        parentId = null,
        accessories = emptyList(),
        serial = "SN-$id",
        entities = entities,
        actions = emptyList(),
    )

    private fun detailJson(id: Long) = """{"id":$id,"type":"w5","model":"EverSweet 3 Pro","kind":"fountain",
        "online":true,"transport":"http","last_seen":1.0,"firmware":"1.0","is_camera":false,"connection":"wifi","parent_id":null,
        "accessories":[],"serial":"SN-$id","entities":[],"actions":[{"key":"cleaning_start","name":"Clean","destructive":false}]}"""

    private fun entity(key: String, value: JsonElement = JsonNull) =
        EntityDto(key, key, "number", value, null, null, null, null, null, true, null, null, null)

    private fun item(id: Long, ts: Double) = TimelineItemDto(
        id = id,
        kind = "visit",
        filters = listOf("toileting"),
        deviceId = 1,
        deviceType = "w5",
        ts = ts,
        displayTs = ts,
        eventType = null,
        eventKind = null,
        label = null,
        pet = null,
        durationSec = null,
        weightG = null,
        served = null,
        facts = emptyList(),
        media = null,
        steps = emptyList(),
    )

    private companion object {
        /**
         * Turbine's own 3 s is wall-clock, and a busy build machine can take longer than that to
         * write a hello into Room on Robolectric.
         */
        val ROOM_TIMEOUT = 30.seconds

        val SIGNED_IN = AuthState.SignedIn("https://pets.example.com/")
    }
}

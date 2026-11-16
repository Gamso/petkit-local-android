package org.petkitlocal.core.data

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.JsonElement
import org.petkitlocal.core.data.di.LiveUpdatesCoroutineScope
import org.petkitlocal.core.database.dao.DeviceDao
import org.petkitlocal.core.database.dao.EntityValuePatch
import org.petkitlocal.core.database.dao.TimelineDao
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.repository.LiveUpdatesRepository
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.core.network.LiveUpdate
import org.petkitlocal.core.network.LiveUpdatesSocket
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.dto.UpdateMessageDto
import org.petkitlocal.core.network.error.apiCall

/**
 * The one `/ws` socket of the process, shared by every collector of [status] (ARCHITECTURE.md §4):
 * opened by the first, while signed in, and closed [STOP_TIMEOUT] after the last one leaves.
 * Each message is applied to Room as it arrives, in order. A socket that closes is reopened after
 * [LiveUpdatesBackoff]'s delay, and the `hello` of the new one replaces the devices, so nothing
 * missed in between stays missed. Close code 4401 ends the session instead.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@Singleton
class LiveUpdatesRepositoryImpl @Inject constructor(
    private val socket: LiveUpdatesSocket,
    private val auth: AuthRepository,
    private val api: PetkitApi,
    private val devices: DeviceDao,
    private val timeline: TimelineDao,
    private val clock: Clock,
    private val backoff: LiveUpdatesBackoff,
    @LiveUpdatesCoroutineScope scope: CoroutineScope,
) : LiveUpdatesRepository {
    override val status: Flow<LiveUpdatesStatus> = auth.authState
        .map { it as? AuthState.SignedIn }
        .distinctUntilChanged()
        .flatMapLatest { session -> if (session == null) flowOf(LiveUpdatesStatus.Stopped) else connect(session) }
        .distinctUntilChanged()
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT.inWholeMilliseconds), replay = 1)

    /** Socket after socket for [session], until it ends. */
    private fun connect(session: AuthState.SignedIn): Flow<LiveUpdatesStatus> = flow {
        var failures = 0
        while (true) {
            emit(LiveUpdatesStatus.Connecting)
            val error = listen { failures = 0 }
            if (error is DomainError.SessionEnded) {
                // Only the session this socket was opened for: a later one is not ended by it.
                if (auth.authState.value == session) auth.endSession(error.reason)
                emit(LiveUpdatesStatus.Stopped)
                return@flow
            }
            val wait = backoff.delayBefore(attempt = failures++)
            emit(LiveUpdatesStatus.Waiting(error, wait))
            delay(wait)
        }
    }

    /**
     * Applies one socket's messages until it closes, and returns why it closed. A close nobody
     * asked for reads as a network failure: the socket is reopened either way.
     */
    private suspend fun FlowCollector<LiveUpdatesStatus>.listen(onHello: () -> Unit): DomainError {
        var closedWith: DomainError? = null
        socket.updates().collect { update ->
            when (update) {
                is LiveUpdate.Message -> {
                    apply(update.message)
                    if (update.message is UpdateMessageDto.Hello) {
                        onHello()
                        emit(LiveUpdatesStatus.Live)
                    }
                }

                is LiveUpdate.Closed -> closedWith = update.error
            }
        }
        return closedWith ?: DomainError.Network("The live updates socket closed")
    }

    private suspend fun apply(message: UpdateMessageDto) {
        when (message) {
            is UpdateMessageDto.Hello -> devices.replaceAll(
                message.devices.mapIndexed { index, device -> device.toRow(index) },
                message.devices.associate { it.id to it.toDetailRows() },
            )

            is UpdateMessageDto.State -> devices.patchEntityValues(
                message.deviceId,
                message.entities.map { EntityValuePatch(it.key, it.value.toJsonText(), it.display) },
            )

            is UpdateMessageDto.Device -> {
                val isNew = devices.getDevice(message.device.id) == null
                devices.upsertSummary(message.device.toRow(position = 0, serial = null))
                if (isNew) fetchDetail(message.device.id)
            }

            is UpdateMessageDto.DeviceRemoved -> devices.deleteDevice(message.deviceId)

            is UpdateMessageDto.Timeline -> {
                val item = message.item
                val day = item.localDay(clock.zone, LocalDate.now(clock))
                timeline.upsertLive(item.toRow(day.toString()), message.replaces)
            }

            UpdateMessageDto.Pong -> Unit
        }
    }

    /** A device that just appeared: its entities and actions are not in the message. */
    private suspend fun fetchDetail(id: Long) {
        val detail = (apiCall { api.getDevice(id) } as? Outcome.Success)?.value?.device ?: return
        devices.replaceDetail(id, detail.serial, detail.toDetailRows())
    }

    private companion object {
        val STOP_TIMEOUT = 5.seconds
    }
}

private fun JsonElement.toJsonText(): String = PetkitJson.encodeToString(JsonElement.serializer(), this)

/**
 * How long to wait before reopening the socket: exponential from [base], capped at [cap], with
 * jitter so that phones cut off together do not all come back in the same second.
 */
class LiveUpdatesBackoff(
    private val base: Duration = 1.seconds,
    private val cap: Duration = 30.seconds,
    private val random: Random = Random.Default,
) {
    @Inject
    constructor() : this(base = 1.seconds)

    /** The wait before attempt [attempt] + 1, after [attempt] failures in a row (0 first). */
    fun delayBefore(attempt: Int): Duration {
        val exponential = base * (1L shl attempt.coerceIn(0, MAX_SHIFT)).toDouble()
        val ceiling = minOf(exponential, cap)
        // Between half the ceiling and the ceiling itself.
        return ceiling * (HALF + HALF * random.nextDouble())
    }

    private companion object {
        const val MAX_SHIFT = 16
        const val HALF = 0.5
    }
}

package org.petkitlocal.core.data

import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import org.petkitlocal.core.datastore.NotificationStore
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.SessionScopedCache
import org.petkitlocal.core.domain.notifications.NotificationConnection
import org.petkitlocal.core.domain.notifications.NotificationFeed
import org.petkitlocal.core.domain.notifications.NotificationFeedEvent
import org.petkitlocal.core.domain.notifications.ServerNotification
import org.petkitlocal.core.network.NotificationSocket
import org.petkitlocal.core.network.NotificationSocketEvent
import org.petkitlocal.core.network.dto.NotificationSocketMessageDto

/**
 * The notification socket, socket after socket (ARCHITECTURE.md §5). Each one is opened with the
 * last id received (`?after=`), so the server replays, oldest first, what this phone missed; each
 * notification is emitted once, then its id is stored, so neither a replay nor a reconnection
 * shows one twice. A socket that closes is reopened after [LiveUpdatesBackoff]'s delay, or at once
 * when the network comes back. Close code 4401 ends the session, 4409 (another socket of this
 * session took over) ends the run; neither is retried.
 */
class NotificationFeedImpl @Inject constructor(
    private val socket: NotificationSocket,
    private val store: NotificationStore,
    private val auth: AuthRepository,
    private val backoff: LiveUpdatesBackoff,
) : NotificationFeed {
    override fun run(networkBack: Flow<Unit>): Flow<NotificationFeedEvent> = flow {
        val session = auth.authState.value as? AuthState.SignedIn
        if (session == null) {
            emit(NotificationFeedEvent.Status(NotificationConnection.Ended(sessionEnded = true)))
            return@flow
        }
        var failures = 0
        while (true) {
            emit(NotificationFeedEvent.Status(NotificationConnection.Connecting))
            val closed = listen { failures = 0 }
            val error = closed.error
            if (error is DomainError.SessionEnded) {
                // Only the session this socket was opened for: a later one is not ended by it.
                if (auth.authState.value == session) auth.endSession(error.reason)
                emit(NotificationFeedEvent.Status(NotificationConnection.Ended(sessionEnded = true)))
                return@flow
            }
            if (closed.replaced) {
                emit(NotificationFeedEvent.Status(NotificationConnection.Ended(sessionEnded = false)))
                return@flow
            }
            // A refused `after` (400): this phone's last id is wrong somehow; start from now.
            if (error is DomainError.Validation) forgetLastId()
            val wait = backoff.delayBefore(attempt = failures++)
            val reason = error ?: DomainError.Network("The notification socket closed")
            emit(NotificationFeedEvent.Status(NotificationConnection.Waiting(reason, wait)))
            // The network back cuts the wait short: what was missed is replayed on reconnection.
            withTimeoutOrNull(wait) { networkBack.first() }
        }
    }

    /** One socket, until it closes; returns how it closed. */
    private suspend fun FlowCollector<NotificationFeedEvent>.listen(
        onHello: () -> Unit,
    ): NotificationSocketEvent.Closed {
        val cursor = NotificationCursor(after = store.lastId())
        var closed: NotificationSocketEvent.Closed? = null
        socket.open(cursor.after).collect { event ->
            when (event) {
                is NotificationSocketEvent.Closed -> closed = event

                is NotificationSocketEvent.Message -> when (val message = event.message) {
                    is NotificationSocketMessageDto.Hello -> {
                        onHello()
                        cursor.hello(message.lastId, message.replayed)?.let { save(it) }
                        emit(NotificationFeedEvent.Status(NotificationConnection.Connected(gap = message.gap)))
                    }

                    is NotificationSocketMessageDto.Delivery -> {
                        if (cursor.isNew(message.id)) emit(NotificationFeedEvent.Show(message.toDomain()))
                        cursor.delivered(message.id, message.replay)?.let { save(it) }
                    }

                    NotificationSocketMessageDto.Pong -> Unit
                }
            }
        }
        return closed ?: NotificationSocketEvent.Closed(DomainError.Network("The notification socket closed"))
    }

    private suspend fun save(id: Long) {
        try {
            store.saveLastId(id)
        } catch (_: IOException) {
            // Kept in memory for this socket; the next one may replay it, and posting it again
            // replaces the notification with the same id rather than adding one.
        }
    }

    private suspend fun forgetLastId() {
        try {
            store.clearLastId()
        } catch (_: IOException) {
            // The next attempt sends the same id and is refused again, after a longer wait.
        }
    }
}

private fun NotificationSocketMessageDto.Delivery.toDomain() = ServerNotification(
    id = id,
    replay = replay,
    kind = message.kind,
    deviceId = message.deviceId,
    petId = message.petId,
    code = message.code,
    time = Instant.ofEpochSecond(message.ts),
)

/**
 * What one socket has shown, by id: a notification at or below [floor] was shown already. Each
 * call answers the id to store when the floor moved, null otherwise.
 *
 * @property after the id this phone held when the socket opened, null on its first connection.
 */
internal class NotificationCursor(val after: Long?) {
    var floor: Long = after ?: 0L
        private set
    private var serverLastId = 0L
    private var replayLeft = 0L

    /**
     * The `hello`: nothing before now is owed on a first connection; after the server lost its
     * log (its last id below the one this phone holds), ids start again and all replayed is new.
     */
    fun hello(lastId: Long, replayed: Long): Long? {
        serverLastId = lastId
        replayLeft = replayed
        when {
            after == null -> floor = lastId
            lastId < after -> floor = 0L
        }
        if (replayLeft > 0) return null
        floor = maxOf(floor, serverLastId)
        return floor
    }

    fun isNew(id: Long): Boolean = id > floor

    /** After the last replayed one, every id up to the hello's was replayed or not for this phone. */
    fun delivered(id: Long, replay: Boolean): Long? {
        var moved = false
        if (id > floor) {
            floor = id
            moved = true
        }
        if (replay && replayLeft > 0) replayLeft--
        if (replay && replayLeft == 0L && serverLastId > floor) {
            floor = serverLastId
            moved = true
        }
        return floor.takeIf { moved }
    }
}

/**
 * The last id and Home's banner put away belong to the session: the next one starts from its own
 * first connection, and shows the banner again.
 */
class NotificationCursorCache @Inject constructor(private val store: NotificationStore) : SessionScopedCache {
    override suspend fun clear() = store.clearSession()
}

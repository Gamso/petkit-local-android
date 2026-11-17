package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.MockClock
import io.github.gamso.petkitlocal.mock.api.MockJson
import io.github.gamso.petkitlocal.mock.api.NotificationDelivery
import io.github.gamso.petkitlocal.mock.api.NotificationHello
import io.github.gamso.petkitlocal.mock.api.PushMessage

/** One logged notification: its monotonic id, when it was logged, and its push message. */
class NotifyEntry(
    val id: Long,
    val at: Double,
    val message: PushMessage,
)

/**
 * The notification socket and its catch-up log, as petkit-local's `app_api/notify/channel.py`:
 *
 * - one log shared by every phone, ids monotonic, bounded to [capacity] entries of at most
 *   [retentionSec];
 * - a notification is logged when a session that ever opened the socket ([AppSession.notifyWs],
 *   connected or not) wants its kind, and sent live to each connected one that does;
 * - a socket opened with `after` is sent, after its `hello`, every logged entry above it that its
 *   kinds allow and that is not older than its session; an `after` above the last id comes from a
 *   log that is gone: everything held is replayed, with `gap`;
 * - one socket per session: a new one replaces the old with 4409.
 *
 * Not thread-safe on its own; [World] holds the lock.
 */
class NotifyChannel(
    private val clock: MockClock,
    /** The protocol ping interval, as `hello.heartbeat_sec` says it (Ktor pings every socket alike). */
    private val heartbeatSec: Double,
    private val capacity: Int = LOG_CAPACITY,
    private val retentionSec: Double = LOG_RETENTION_SEC,
) {
    private val entries = ArrayDeque<NotifyEntry>()
    private var nextId = 1L
    private val sockets = HashMap<String, Subscriber>()

    /** The newest id ever logged; 0 before the first. */
    val lastId: Long get() = nextId - 1

    val size: Int get() = sockets.size

    fun connected(session: AppSession): Boolean = sockets[session.id] != null

    /**
     * Logs [message] if a session using the socket wants it and sends it live to the connected
     * ones that do: [targets] are the live sessions. Returns the id, or null when nobody wanted it.
     */
    fun deliver(
        message: PushMessage,
        targets: List<AppSession>,
    ): Long? {
        val wanting = targets.filter { it.notifyWs != null && it.allows(message.kind) }
        if (wanting.isEmpty()) return null
        val now = clock.now()
        val entry = NotifyEntry(nextId++, now, message)
        entries.addLast(entry)
        prune(now)
        val text = encode(entry, replay = false)
        wanting.forEach { sockets[it.id]?.push(text) }
        return entry.id
    }

    /** Opens [session]'s socket: `hello`, then what it missed after [after], then live. */
    fun attach(
        session: AppSession,
        after: Long?,
    ): Subscriber {
        val now = clock.now()
        prune(now)
        if (session.notifyWs == null) session.notifyWs = now
        sockets.remove(session.id)?.end(CLOSE_REPLACED, "replaced by a newer notification socket")
        val sub = Subscriber(session, entries.size + LIVE_QUEUE + 1)
        var replay = emptyList<NotifyEntry>()
        var gap = false
        if (after != null) {
            var from = after
            if (from > lastId) {
                // An id from a log that no longer exists.
                from = 0
                gap = true
            }
            val floor = entries.firstOrNull()?.id ?: nextId
            gap = gap || from < floor - 1
            replay = entries.filter { it.id > from && it.at >= session.created && session.allows(it.message.kind) }
        }
        val hello = NotificationHello(serverTime = now, heartbeatSec = heartbeatSec, lastId = lastId, replayed = replay.size, gap = gap)
        sub.push(MockJson.encodeToString(NotificationHello.serializer(), hello))
        replay.forEach { sub.push(encode(it, replay = true)) }
        sockets[session.id] = sub
        return sub
    }

    /** Forgets [sub] unless another socket of its session replaced it already. */
    fun detach(sub: Subscriber) {
        if (sockets[sub.session.id] === sub) sockets.remove(sub.session.id)
    }

    /** Closes the socket of [session], which ended (4401). */
    fun endSession(session: AppSession) {
        sockets.remove(session.id)?.end(Subscriber.CLOSE_SESSION_ENDED, "session ended")
    }

    /** Closes every socket with [code]; returns how many. */
    fun end(
        code: Short,
        reason: String,
    ): Int {
        val all = sockets.values.toList()
        sockets.clear()
        all.forEach { it.end(code, reason) }
        return all.size
    }

    /** The log file lost: ids start again from 1, which the next `hello` reports as a gap. */
    fun loseLog() {
        entries.clear()
        nextId = 1
    }

    fun reset() {
        end(Subscriber.CLOSE_GOING_AWAY, "mock reset")
        loseLog()
    }

    private fun prune(now: Double) {
        while (entries.size > capacity) entries.removeFirst()
        while (entries.isNotEmpty() && now - entries.first().at > retentionSec) entries.removeFirst()
    }

    private fun encode(
        entry: NotifyEntry,
        replay: Boolean,
    ): String =
        MockJson.encodeToString(
            NotificationDelivery.serializer(),
            NotificationDelivery(id = entry.id, replay = replay, message = entry.message),
        )

    companion object {
        /** petkit-local's `LOG_CAPACITY` and `LOG_RETENTION`. */
        const val LOG_CAPACITY = 500
        const val LOG_RETENTION_SEC = 3 * 86400.0

        /** Live messages waiting for one phone, beyond a full replay of the log. */
        const val LIVE_QUEUE = 64

        /** This session opened another notification socket, which replaced this one. */
        const val CLOSE_REPLACED: Short = 4409
    }
}

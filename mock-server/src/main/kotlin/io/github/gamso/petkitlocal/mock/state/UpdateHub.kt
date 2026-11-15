package io.github.gamso.petkitlocal.mock.state

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

/** What a `/ws` writer takes from its queue: a text frame, or the close that ends it. */
sealed interface Outgoing {
    data class Text(
        val text: String,
    ) : Outgoing

    data class Close(
        val code: Short,
        val reason: String,
    ) : Outgoing
}

/**
 * One open `/ws` socket's outbox, bounded as petkit-local's (`app_api/updates.py::Subscriber`):
 * a client that stops reading is closed with 1013 when [limit] messages wait, never allowed to
 * grow memory.
 */
class Subscriber(
    val session: AppSession,
    private val limit: Int,
) {
    // One slot more than the limit, so the close always fits.
    val channel = Channel<Outgoing>(limit + 1)
    private val pending = AtomicInteger(0)

    @Volatile var closeCode: Short? = null
        private set

    fun push(text: String): Boolean {
        if (closeCode != null) return false
        if (pending.incrementAndGet() > limit) {
            end(CLOSE_TOO_SLOW, "too slow: messages were not being read")
            return false
        }
        channel.trySend(Outgoing.Text(text))
        return true
    }

    /** The writer took one message off the queue. */
    fun taken() {
        pending.decrementAndGet()
    }

    /** Drops what is queued and hands the writer the close. */
    @Synchronized
    fun end(
        code: Short,
        reason: String,
    ) {
        if (closeCode != null) return
        closeCode = code
        while (channel.tryReceive().isSuccess) {
            pending.decrementAndGet()
        }
        channel.trySend(Outgoing.Close(code, reason))
    }

    companion object {
        const val CLOSE_GOING_AWAY: Short = 1001
        const val CLOSE_TOO_SLOW: Short = 1013
        const val CLOSE_SESSION_ENDED: Short = 4401
    }
}

/** Every open `/ws` socket. Messages are encoded once and shared by every subscriber. */
class UpdateHub {
    private val subs = CopyOnWriteArraySet<Subscriber>()

    val size: Int get() = subs.size

    fun add(sub: Subscriber) {
        subs.add(sub)
    }

    fun remove(sub: Subscriber) {
        subs.remove(sub)
    }

    fun broadcast(text: String) {
        subs.forEach { it.push(text) }
    }

    fun end(
        code: Short,
        reason: String,
        predicate: (Subscriber) -> Boolean = { true },
    ): Int {
        val matching = subs.filter(predicate)
        matching.forEach { it.end(code, reason) }
        return matching.size
    }

    fun subscribers(): List<Subscriber> = subs.toList()
}

package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.media.LivePattern
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicLong

/** What a live socket's writer takes from its queue. */
sealed interface LiveOut {
    data class Text(
        val text: String,
    ) : LiveOut

    class Binary(
        val bytes: ByteArray,
    ) : LiveOut

    data class Close(
        val code: Short,
        val reason: String,
    ) : LiveOut
}

/**
 * One open `/devices/{id}/live` socket. Its outbox is bounded in bytes as petkit-local bounds it:
 * a client that lets more than [LiveHub.MAX_QUEUED_BYTES] wait is closed with 1013.
 */
class LiveViewer(
    val session: AppSession,
    val deviceId: Int,
) {
    val outbox = Channel<LiveOut>(Channel.UNLIMITED)
    private val queued = AtomicLong(0)

    @Volatile var closeCode: Short? = null
        private set

    /** Streaming stops after an `error`: the client is expected to close and retry. */
    @Volatile var streaming = true

    fun text(text: String) {
        if (closeCode == null) outbox.trySend(LiveOut.Text(text))
    }

    fun binary(bytes: ByteArray) {
        if (closeCode != null) return
        if (queued.addAndGet(bytes.size.toLong()) > LiveHub.MAX_QUEUED_BYTES) {
            end(CLOSE_TOO_SLOW, "too slow: more than 4 MiB queued")
            return
        }
        outbox.trySend(LiveOut.Binary(bytes))
    }

    /** The writer sent [bytes] bytes. */
    fun sent(bytes: Int) {
        queued.addAndGet(-bytes.toLong())
    }

    /** Drops what is queued and hands the writer the close. */
    @Synchronized
    fun end(
        code: Short,
        reason: String,
    ) {
        if (closeCode != null) return
        closeCode = code
        streaming = false
        do {
            val drained = outbox.tryReceive().isSuccess
        } while (drained)
        queued.set(0)
        outbox.trySend(LiveOut.Close(code, reason))
    }

    companion object {
        const val CLOSE_GOING_AWAY: Short = 1001
        const val CLOSE_STREAM_ENDED: Short = 1011
        const val CLOSE_TOO_SLOW: Short = 1013
        const val CLOSE_SESSION_ENDED: Short = 4401
        const val CLOSE_REPLACED: Short = 4409
    }
}

/** One HLS view: opened by `playlist.m3u8` without `id`, lapsed after [LiveHub.HLS_IDLE_NANOS] unused. */
class HlsView(
    val id: String,
    val session: AppSession,
    val deviceId: Int,
    @Volatile var lastUsedNanos: Long,
)

/**
 * The live video of every camera: one endless test pattern ([LivePattern]) on a clock shared by
 * every viewer, so a viewer joining late gets the stream where it is, as go2rtc does. Fragment
 * `n` is complete (and so sent) at `start + (n + 1)` fragment durations.
 *
 * Viewers are capped like petkit-local's: [PER_CAMERA] per camera and [TOTAL] in all, sockets
 * and HLS views together, and one per session and camera — opening the camera again replaces
 * the older view (its socket closes with 4409). Thread-safe on its own lock.
 */
class LiveHub(
    val pattern: LivePattern = LivePattern.bundled,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val lock = Any()
    private val startNanos = nanoTime()
    private val viewers = LinkedHashSet<LiveViewer>()
    private val hls = LinkedHashMap<String, HlsView>()
    private val random = SecureRandom()

    /** `error` messages the next opens answer instead of the stream (control API). */
    private var pendingErrors = ArrayDeque<String>()

    val sockets: Int get() = synchronized(lock) { viewers.size }

    val hlsViews: Int get() = synchronized(lock) { pruneHls().let { hls.size } }

    /** The newest fragment that is complete now (-1 before the first one is). */
    fun newestFragment(): Long = (nanoTime() - startNanos) / (pattern.fragmentMillis * NANOS_PER_MILLI) - 1

    /** Nanoseconds from now until fragment [n] is complete. */
    fun nanosUntil(n: Long): Long = startNanos + (n + 1) * pattern.fragmentMillis * NANOS_PER_MILLI - nanoTime()

    /**
     * Refuses a new view before it opens (429 `live_limit_reached`), counting what [session]
     * already holds on [deviceId] as gone, since the new view replaces it.
     */
    fun checkCapacity(
        session: AppSession,
        deviceId: Int,
    ) = synchronized(lock) {
        pruneHls()
        val views = viewers.map { it.session to it.deviceId } + hls.values.map { it.session to it.deviceId }
        val others = views.filterNot { (s, d) -> s.id == session.id && d == deviceId }
        if (others.count { it.second == deviceId } >= PER_CAMERA || others.size >= TOTAL) {
            throw ApiException(
                429,
                "live_limit_reached",
                "this camera ($PER_CAMERA) or the server ($TOTAL) already has as many live viewers as it serves",
                mapOf(HttpHeaders.RetryAfter to RETRY_AFTER_SEC.toString()),
            )
        }
    }

    /** A socket that just opened: the session's older view of this camera is replaced. */
    fun open(viewer: LiveViewer) =
        synchronized(lock) {
            replace(viewer.session, viewer.deviceId)
            viewers += viewer
        }

    fun remove(viewer: LiveViewer) {
        synchronized(lock) { viewers.remove(viewer) }
    }

    /** A new HLS view, replacing the session's older view of this camera. */
    fun openHls(
        session: AppSession,
        deviceId: Int,
    ): HlsView =
        synchronized(lock) {
            replace(session, deviceId)
            val id = HexFormat.of().formatHex(ByteArray(HLS_ID_BYTES).also(random::nextBytes))
            HlsView(id, session, deviceId, nanoTime()).also { hls[id] = it }
        }

    /** The HLS view [id], if [session] opened it for [deviceId]; 404 otherwise. */
    fun hlsView(
        id: String,
        session: AppSession,
        deviceId: Int,
    ): HlsView =
        synchronized(lock) {
            val view = hls[id]
            if (view == null || view.session.id != session.id || view.deviceId != deviceId) {
                throw ApiException(404, "hls_session_not_found", "no such HLS session for this device")
            }
            if (nanoTime() - view.lastUsedNanos > HLS_IDLE_NANOS) {
                hls.remove(id)
                throw ApiException(404, "hls_session_expired", "the HLS session lapsed; start again from playlist.m3u8")
            }
            view.lastUsedNanos = nanoTime()
            view
        }

    /** The next open answers an `error` message instead of the stream (control API). */
    fun failNextOpens(
        times: Int,
        message: String,
    ) = synchronized(lock) { repeat(times) { pendingErrors.addLast(message) } }

    /** The `error` the control API queued for this open, if any. */
    fun takePendingError(): String? = synchronized(lock) { pendingErrors.removeFirstOrNull() }

    /** Sends an `error` to every open socket, which then stops streaming; returns how many. */
    fun errorAll(message: String): Int =
        synchronized(lock) {
            viewers.forEach {
                it.streaming = false
                it.text(errorMessage(message))
            }
            viewers.size
        }

    /** Closes the sockets [predicate] picks with [code]; returns how many. */
    fun end(
        code: Short,
        reason: String,
        predicate: (LiveViewer) -> Boolean = { true },
    ): Int =
        synchronized(lock) {
            val matching = viewers.filter(predicate)
            matching.forEach { it.end(code, reason) }
            matching.size
        }

    /** Sessions that ended: their sockets close with 4401, their HLS views go. */
    fun endSessions(ended: (AppSession) -> Boolean) =
        synchronized(lock) {
            viewers.filter { ended(it.session) }.forEach { it.end(LiveViewer.CLOSE_SESSION_ENDED, "session ended") }
            hls.values.removeIf { ended(it.session) }
        }

    /** Back to no views and no queued errors (the sockets close as the stream ending). */
    fun reset() =
        synchronized(lock) {
            pendingErrors.clear()
            hls.clear()
            viewers.toList().forEach { it.end(LiveViewer.CLOSE_STREAM_ENDED, "mock reset") }
        }

    private fun replace(
        session: AppSession,
        deviceId: Int,
    ) {
        viewers
            .filter { it.session.id == session.id && it.deviceId == deviceId }
            .forEach { it.end(LiveViewer.CLOSE_REPLACED, "replaced by a newer view of this session") }
        hls.values.removeIf { it.session.id == session.id && it.deviceId == deviceId }
    }

    private fun pruneHls() {
        val now = nanoTime()
        hls.values.removeIf { now - it.lastUsedNanos > HLS_IDLE_NANOS }
    }

    companion object {
        const val PER_CAMERA = 2
        const val TOTAL = 4
        const val MAX_QUEUED_BYTES = 4L * 1024 * 1024
        const val RETRY_AFTER_SEC = 5
        private const val HLS_ID_BYTES = 8
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val HLS_IDLE_NANOS = 5_000_000_000L

        /** An `error` text message: the stream could not be opened, or a client message was refused. */
        fun errorMessage(message: String): String =
            JsonObject(mapOf("type" to JsonPrimitive("error"), "value" to JsonPrimitive(message))).toString()
    }
}

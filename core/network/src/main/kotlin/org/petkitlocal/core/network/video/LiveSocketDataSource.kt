package org.petkitlocal.core.network.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.network.CloseCodes
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.auth.AuthInterceptor
import org.petkitlocal.core.network.auth.SessionEndListener
import org.petkitlocal.core.network.decodeLiveVideoMessage
import org.petkitlocal.core.network.dto.LiveVideoMessageDto
import org.petkitlocal.core.network.error.HttpErrorMapper

/**
 * `GET /devices/{id}/live` as a Media3 [androidx.media3.datasource.DataSource]: go2rtc's MSE
 * protocol over a WebSocket on the app's one OkHttp client (ADR 0005), so the upgrade carries the
 * bearer header and reaches the current server like every request.
 *
 * [open] connects, sends the single `{"type":"mse","value":<codecs>}` request and returns once
 * the server named the stream's MIME type; [read] then hands Media3 the binary messages in order,
 * the init segment first and a fragment after another, as one endless byte stream for the
 * fragmented MP4 extractor. The messages wait in a pipe of at most [maxBufferedBytes]: past that
 * the socket's reader waits too, so a player that falls behind makes the server's queue grow
 * until it closes with 1013, which is the server's way of saying "use HLS".
 *
 * Every way the stream stops is thrown as a [LiveStreamException] carrying its [LiveFailure]: an
 * `error` message, a close code, a refused upgrade, no answer within [startTimeout]. Bytes
 * already received are read first. Close code 4401 also ends the session, as the auth interceptor
 * does for a 401, for the token this socket was opened with only.
 */
@OptIn(UnstableApi::class) // BaseDataSource, DataSpec.
class LiveSocketDataSource(
    private val client: OkHttpClient,
    private val codecs: String,
    private val sessionEnded: SessionEndListener,
    private val startTimeout: Duration = START_TIMEOUT,
    private val maxBufferedBytes: Long = MAX_BUFFERED_BYTES,
) : BaseDataSource(true) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    // Guarded by lock: written by the socket's thread, read by Media3's loader.
    private val chunks = ArrayDeque<ByteString>()
    private var buffered = 0L
    private var mimeType: String? = null
    private var failure: LiveStreamException? = null
    private var closed = false

    // The loader's own.
    private var socket: WebSocket? = null
    private var uri: Uri? = null
    private var current: ByteString? = null
    private var position = 0
    private var transferring = false

    /** The MIME type the server announced, e.g. `video/mp4; codecs="avc1.640029"`; null before. */
    val streamMimeType: String? get() = lock.withLock { mimeType }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        lock.withLock { closed = false }
        transferInitializing(dataSpec)
        socket = client.newWebSocket(Request.Builder().url(dataSpec.uri.toString()).build(), Listener())
        lock.withLock {
            var left = startTimeout.inWholeNanoseconds
            while (mimeType == null && failure == null) {
                if (left <= 0) fail(LiveFailure.Network("no answer from the live stream within $startTimeout"))
                left = interruptibly { changed.awaitNanos(left) }
            }
            // Started: what stopped it since is thrown once the bytes before it are read.
            if (mimeType == null) failure?.let { throw it }
        }
        transferring = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val chunk = current?.takeIf { position < it.size } ?: nextChunk()
        val count = minOf(length, chunk.size - position)
        chunk.copyInto(position, buffer, offset, count)
        position += count
        bytesTransferred(count)
        return count
    }

    /** The next binary message, waiting for it; what stopped the stream once none is left. */
    private fun nextChunk(): ByteString = lock.withLock {
        while (chunks.isEmpty()) {
            failure?.let { throw it }
            if (closed) throw InterruptedIOException("the live stream was closed")
            interruptibly { changed.await() }
        }
        val chunk = chunks.removeFirst()
        buffered -= chunk.size
        changed.signalAll()
        current = chunk
        position = 0
        chunk
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        lock.withLock {
            closed = true
            chunks.clear()
            buffered = 0
            changed.signalAll()
        }
        current = null
        socket?.close(CloseCodes.NORMAL, null)
        socket = null
        if (transferring) {
            transferring = false
            transferEnded()
        }
    }

    /** Records why the stream stopped (the first reason wins) and throws it. Holding [lock]. */
    private fun fail(reason: LiveFailure): Nothing {
        val first = failure ?: LiveStreamException(reason).also { failure = it }
        changed.signalAll()
        throw first
    }

    /** Records why the stream stopped, unless it already had or was closed; true if this was it. */
    private fun stopped(reason: LiveFailure): Boolean = lock.withLock {
        val first = failure == null && !closed
        if (first) failure = LiveStreamException(reason)
        changed.signalAll()
        first
    }

    private inline fun <T> interruptibly(block: () -> T): T = try {
        block()
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw InterruptedIOException(e.message)
    }

    private inner class Listener : WebSocketListener() {
        /** The token the upgrade went out with: 4401 ends that session, not a newer one. */
        private var token: String? = null

        override fun onOpen(webSocket: WebSocket, response: Response) {
            token = response.request.header(AuthInterceptor.AUTHORIZATION)?.removePrefix(BEARER)
            webSocket.send(PetkitJson.encodeToString(LiveVideoMessageDto.serializer(), LiveVideoMessageDto.Mse(codecs)))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val message = decodeLiveVideoMessage(text)) {
                is LiveVideoMessageDto.Mse -> lock.withLock {
                    if (mimeType == null) mimeType = message.value
                    changed.signalAll()
                }

                is LiveVideoMessageDto.Error -> stopped(LiveFailure.StreamError(message.value))

                // A message type this app does not know yet.
                null -> Unit
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            lock.withLock {
                // Holding this thread holds the socket's reading: the server sees a slow client.
                while (open() && isFull(bytes.size)) changed.await(PIPE_POLL_MS, TimeUnit.MILLISECONDS)
                if (!open()) return
                chunks.addLast(bytes)
                buffered += bytes.size
                changed.signalAll()
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(CloseCodes.NORMAL, null)
            closedWith(code)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closedWith(code)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            stopped(response?.let(::refusal) ?: LiveFailure.Network(t.message ?: t.javaClass.simpleName))
        }

        /** Still read: neither stopped by the server nor closed by the player. Holding [lock]. */
        private fun open() = failure == null && !closed

        /** No room for [size] more bytes; a message alone is always let in. Holding [lock]. */
        private fun isFull(size: Int) = buffered > 0 && buffered + size > maxBufferedBytes

        private fun closedWith(code: Int) {
            val reason = liveFailureOfClose(code)
            if (stopped(reason) && reason == LiveFailure.SessionEnded) {
                token?.let { sessionEnded.onSessionEnded(SessionEndReason.ClosedByServer, it) }
            }
        }

        /** A refused upgrade, read like any refused request; a 401 already ended the session. */
        private fun refusal(response: Response): LiveFailure {
            val error = HttpErrorMapper.map(
                status = response.code,
                body = runCatching { response.peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrNull(),
                retryAfter = response.header(RETRY_AFTER),
                hadSession = response.request.header(AuthInterceptor.AUTHORIZATION) != null,
            )
            return if (error is DomainError.SessionEnded) LiveFailure.SessionEnded else LiveFailure.Refused(error)
        }
    }

    companion object {
        /** The init segment of a high-bitrate camera and a few seconds of fragments. */
        const val MAX_BUFFERED_BYTES = 2L * 1024 * 1024

        /** Opening the camera's stream takes go2rtc a moment; this is far beyond it. */
        val START_TIMEOUT: Duration = 15.seconds

        private const val PIPE_POLL_MS = 200L
        private const val MAX_ERROR_BODY_BYTES = 16L * 1024
        private const val RETRY_AFTER = "Retry-After"
        private const val BEARER = "Bearer "
    }
}

/** What a close code of the live socket means for the next start (api/openapi.yaml). */
fun liveFailureOfClose(code: Int): LiveFailure = when (code) {
    CloseCodes.NORMAL, CloseCodes.GOING_AWAY, CloseCodes.STREAM_ENDED -> LiveFailure.StreamEnded
    CloseCodes.TOO_SLOW -> LiveFailure.TooSlow
    CloseCodes.REPLACED -> LiveFailure.Replaced
    CloseCodes.SESSION_ENDED -> LiveFailure.SessionEnded
    else -> LiveFailure.Network("the live stream closed with code $code")
}

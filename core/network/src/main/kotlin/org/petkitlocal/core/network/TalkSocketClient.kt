package org.petkitlocal.core.network

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.talk.TalkFailure
import org.petkitlocal.core.network.auth.AuthInterceptor
import org.petkitlocal.core.network.auth.SessionEndListener
import org.petkitlocal.core.network.dto.TalkErrorCode
import org.petkitlocal.core.network.dto.TalkMessageDto
import org.petkitlocal.core.network.error.HttpErrorMapper

/** What the talk socket says, in order: [Ready] once, [Stopped] after a stop, [Ended] last. */
sealed interface TalkSocketEvent {
    /** The camera accepted: send the microphone now. */
    data object Ready : TalkSocketEvent

    /** The server played what it received, answering the stop; [Ended] follows. */
    data object Stopped : TalkSocketEvent

    /** The socket is closed: [failure] says why, or is null for a talk that ended normally. */
    data class Ended(val failure: TalkFailure?) : TalkSocketEvent
}

/** One open talk socket. */
interface TalkConnection {
    /** The socket's events, [TalkSocketEvent.Ended] last; it completes after it. */
    val events: Flow<TalkSocketEvent>

    /**
     * Sends [pcm] (s16le, 16 kHz, mono) as one binary message; false once the socket is going.
     * Audio is real time: a chunk that would wait behind more than [TalkSocketClient.MAX_QUEUED_BYTES]
     * is dropped rather than queued, so a slow network loses a moment rather than falling behind.
     */
    fun send(pcm: ByteArray): Boolean

    /** Asks the server to end the talk: it plays what it received, answers `stopped`, and closes. */
    fun stop()

    /**
     * Ends the talk now: says `stop` (the server still plays what it received), closes the socket,
     * and ends [events] with [failure]. Anything the socket says after is ignored.
     */
    fun close(failure: TalkFailure? = null)
}

/** `GET /devices/{id}/talk`. */
fun interface TalkSocket {
    fun open(deviceId: Long): TalkConnection
}

/**
 * The talk socket on the app's one OkHttp client, so the upgrade carries the bearer header to the
 * current server like every request, and a refused one (401) ends the session as a REST call
 * would. Close code 4401 ends the session too, for the token this socket was opened with only,
 * as the live socket does.
 */
class TalkSocketClient(private val client: OkHttpClient, private val sessionEnded: SessionEndListener) : TalkSocket {
    override fun open(deviceId: Long): TalkConnection {
        val connection = Connection()
        connection.socket = client.newWebSocket(Request.Builder().url(url(deviceId)).build(), connection.listener)
        return connection
    }

    private inner class Connection : TalkConnection {
        private val channel = Channel<TalkSocketEvent>(Channel.UNLIMITED)
        private val ended = AtomicBoolean(false)
        lateinit var socket: WebSocket

        /** The first `error` the server sent: why it closed, whatever the close code. */
        @Volatile private var error: TalkFailure? = null

        /** The token the upgrade went out with: 4401 ends that session, not a newer one. */
        @Volatile private var token: String? = null

        override val events: Flow<TalkSocketEvent> = channel.receiveAsFlow()

        override fun send(pcm: ByteArray): Boolean {
            if (ended.get()) return false
            if (socket.queueSize() + pcm.size > MAX_QUEUED_BYTES) return true
            return socket.send(pcm.toByteString())
        }

        override fun stop() {
            if (!ended.get()) socket.send(STOP)
        }

        override fun close(failure: TalkFailure?) {
            if (!finish(failure)) return
            socket.send(STOP)
            socket.close(CloseCodes.NORMAL, null)
        }

        /** Ends [events] with [failure], unless they already ended; true if this was it. */
        fun finish(failure: TalkFailure?): Boolean {
            if (!ended.compareAndSet(false, true)) return false
            channel.trySend(TalkSocketEvent.Ended(failure))
            channel.close()
            return true
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                token = response.request.header(AuthInterceptor.AUTHORIZATION)?.removePrefix(BEARER)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (ended.get()) return
                when (val message = decodeTalkMessage(text)) {
                    is TalkMessageDto.Ready -> channel.trySend(TalkSocketEvent.Ready)

                    TalkMessageDto.Stopped -> channel.trySend(TalkSocketEvent.Stopped)

                    is TalkMessageDto.Error -> if (error == null) error = failureOf(message)

                    // The client's own message, or a type this app does not know yet.
                    TalkMessageDto.Stop, null -> Unit
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(CloseCodes.NORMAL, null)
                closedWith(code)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = closedWith(code)

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(error ?: response?.let(::refusal) ?: TalkFailure.Network(t.message ?: t.javaClass.simpleName))
            }

            private fun closedWith(code: Int) {
                val failure = error ?: closeFailure(code)
                if (finish(failure) && failure == TalkFailure.SessionEnded) {
                    token?.let { sessionEnded.onSessionEnded(SessionEndReason.ClosedByServer, it) }
                }
            }
        }
    }

    /** A refused upgrade, read like any refused request; a 401 already ended the session. */
    private fun refusal(response: Response): TalkFailure {
        val error = HttpErrorMapper.map(
            status = response.code,
            body = runCatching { response.peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrNull(),
            retryAfter = response.header(RETRY_AFTER),
            hadSession = response.request.header(AuthInterceptor.AUTHORIZATION) != null,
        )
        return if (error is DomainError.SessionEnded) TalkFailure.SessionEnded else TalkFailure.Refused(error)
    }

    companion object {
        /** Two seconds of audio: past that, a new chunk is dropped rather than queued. */
        const val MAX_QUEUED_BYTES = 64L * 1024

        private const val MAX_ERROR_BODY_BYTES = 16L * 1024
        private const val RETRY_AFTER = "Retry-After"
        private const val BEARER = "Bearer "
        private val STOP = PetkitJson.encodeToString(TalkMessageDto.serializer(), TalkMessageDto.Stop)

        /** `GET /devices/{id}/talk`, on the placeholder the server-URL interceptor rewrites. */
        fun url(deviceId: Long): HttpUrl = PetkitHttpClient.PLACEHOLDER_BASE_URL.resolve("devices/$deviceId/talk")!!
    }
}

/** One talk text frame, or null for one this app does not understand. */
fun decodeTalkMessage(text: String): TalkMessageDto? = try {
    PetkitJson.decodeFromString(TalkMessageDto.serializer(), text)
} catch (_: IllegalArgumentException) { // SerializationException is one
    null
}

/** What an `error` message means; a code this app does not know keeps the server's words. */
internal fun failureOf(error: TalkMessageDto.Error): TalkFailure = when (error.code) {
    TalkErrorCode.NOT_A_CAMERA -> TalkFailure.NotACamera
    TalkErrorCode.NEEDS_PATCHER -> TalkFailure.NeedsPatcher
    TalkErrorCode.DEVICE_UNREACHABLE -> TalkFailure.DeviceUnreachable
    TalkErrorCode.BUSY -> TalkFailure.Busy
    TalkErrorCode.TRANSCODER_FAILED -> TalkFailure.TranscoderFailed(error.message)
    TalkErrorCode.IDLE_TIMEOUT -> TalkFailure.IdleTimeout
    else -> TalkFailure.Other(error.code, error.message)
}

/** What a close code means when no `error` came before it (api/openapi.yaml, the talk route). */
internal fun closeFailure(code: Int): TalkFailure? = when (code) {
    CloseCodes.NORMAL -> null
    CloseCodes.GOING_AWAY -> TalkFailure.ServerStopping
    CloseCodes.SESSION_ENDED -> TalkFailure.SessionEnded
    CloseCodes.TOO_BIG -> TalkFailure.Network("the server refused an audio frame as too big")
    else -> TalkFailure.Network("the talk closed with code $code")
}

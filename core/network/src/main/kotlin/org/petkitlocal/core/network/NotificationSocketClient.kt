package org.petkitlocal.core.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.network.auth.AuthInterceptor
import org.petkitlocal.core.network.dto.NotificationSocketMessageDto
import org.petkitlocal.core.network.error.HttpErrorMapper

/** What the notification socket yields while it is collected. */
sealed interface NotificationSocketEvent {
    data class Message(val message: NotificationSocketMessageDto) : NotificationSocketEvent

    /**
     * The socket closed. [error] is null for a close the app asked for; [replaced] when this
     * session opened another notification socket, which took over (4409): do not reconnect.
     */
    data class Closed(val error: DomainError?, val replaced: Boolean = false) : NotificationSocketEvent
}

/**
 * `GET /notifications/ws`: notifications only, never device state (ARCHITECTURE.md §5). One
 * socket per collection; reconnecting is the data layer's.
 */
interface NotificationSocket {
    /** Opens the socket; [after] is the highest id already received, null on the first connection. */
    fun open(after: Long?): Flow<NotificationSocketEvent>
}

/**
 * The notification socket on the app's one OkHttp client, as [LiveUpdatesClient] for `/ws`: the
 * upgrade carries the bearer header to the current server, and a refused one (401
 * `invalid_token`) ends the session as a REST call would.
 *
 * The server pings every 45 s, which OkHttp answers. The client pings as often and fails the
 * socket when a ping of its own goes unanswered until the next one: the contract's "two missed
 * pings", and a connection that died without a close (the phone changed network) is noticed.
 */
class NotificationSocketClient(client: OkHttpClient) : NotificationSocket {
    private val client = client.newBuilder().pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS).build()

    override fun open(after: Long?): Flow<NotificationSocketEvent> = callbackFlow {
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                // A type this app does not know yet is skipped, as the API allows.
                decodeNotificationMessage(text)?.let { trySend(NotificationSocketEvent.Message(it)) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(CloseCodes.NORMAL, null)
                finish(closed(code))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(closed(code))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(NotificationSocketEvent.Closed(response?.let(::refusal) ?: DomainError.Network(t.message)))
            }

            private fun finish(closed: NotificationSocketEvent.Closed) {
                trySend(closed)
                channel.close()
            }
        }
        val socket = client.newWebSocket(Request.Builder().url(url(after)).build(), listener)
        awaitClose { socket.close(CloseCodes.NORMAL, null) }
    }

    private companion object {
        val URL = PetkitHttpClient.PLACEHOLDER_BASE_URL.resolve("notifications/ws")!!
        const val PING_INTERVAL_SECONDS = 45L
        const val MAX_ERROR_BODY_BYTES = 16L * 1024

        fun url(after: Long?) = if (after == null) {
            URL
        } else {
            URL.newBuilder().addQueryParameter("after", after.toString()).build()
        }

        /** 4409 is the replaced socket's own; the other codes read as they do on `/ws`. */
        fun closed(code: Int) = when (code) {
            CloseCodes.REPLACED -> NotificationSocketEvent.Closed(error = null, replaced = true)

            CloseCodes.NORMAL -> NotificationSocketEvent.Closed(error = null)

            CloseCodes.SESSION_ENDED -> NotificationSocketEvent.Closed(
                DomainError.SessionEnded(SessionEndReason.ClosedByServer),
            )

            else -> NotificationSocketEvent.Closed(DomainError.Network("WebSocket closed with code $code"))
        }
    }

    /** A refused upgrade, read like any refused request; its body may already be gone. */
    private fun refusal(response: Response): DomainError = HttpErrorMapper.map(
        status = response.code,
        body = runCatching { response.peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrNull(),
        retryAfter = response.header("Retry-After"),
        hadSession = response.request.header(AuthInterceptor.AUTHORIZATION) != null,
    )
}

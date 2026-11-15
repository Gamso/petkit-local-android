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
import org.petkitlocal.core.network.auth.AuthInterceptor
import org.petkitlocal.core.network.error.HttpErrorMapper

/**
 * `GET /ws` on the app's one OkHttp client: the upgrade goes through [ServerUrlInterceptor] and
 * [AuthInterceptor] like any request, so it reaches the current server with the bearer header,
 * and a refused upgrade (401 `invalid_token`) ends the session as a REST call would.
 *
 * Each collection opens one socket and completes when it closes, after a final
 * [LiveUpdate.Closed]; reconnecting is the caller's job. Cancelling the collection closes the
 * socket normally (1000).
 */
class LiveUpdatesClient(client: OkHttpClient) : LiveUpdatesSocket {
    // After the upgrade OkHttp drops the read timeout, so a dead connection would never be noticed;
    // protocol pings find it. The server pings too (every 25 s), which keeps proxies awake.
    private val client = client.newBuilder().pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS).build()

    override fun updates(): Flow<LiveUpdate> = callbackFlow {
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                // A type this app does not know yet is skipped, as the API allows.
                decodeUpdateMessage(text)?.let { trySend(LiveUpdate.Message(it)) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(CloseCodes.NORMAL, null)
                finish(LiveUpdate.Closed(closeCodeToDomainError(code)))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(LiveUpdate.Closed(closeCodeToDomainError(code)))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(LiveUpdate.Closed(response?.let(::refusal) ?: DomainError.Network(t.message)))
            }

            private fun finish(closed: LiveUpdate.Closed) {
                trySend(closed)
                channel.close()
            }
        }
        val socket = client.newWebSocket(Request.Builder().url(URL).build(), listener)
        awaitClose { socket.close(CloseCodes.NORMAL, null) }
    }

    private companion object {
        val URL = PetkitHttpClient.PLACEHOLDER_BASE_URL.resolve("ws")!!
        const val PING_INTERVAL_SECONDS = 30L
        const val MAX_ERROR_BODY_BYTES = 16L * 1024
    }

    /** A refused upgrade, read like any refused request; its body may already be gone. */
    private fun refusal(response: Response): DomainError = HttpErrorMapper.map(
        status = response.code,
        body = runCatching { response.peekBody(MAX_ERROR_BODY_BYTES).string() }.getOrNull(),
        retryAfter = response.header("Retry-After"),
        hadSession = response.request.header(AuthInterceptor.AUTHORIZATION) != null,
    )
}

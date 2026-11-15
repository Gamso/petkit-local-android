package org.petkitlocal.core.network

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.network.dto.UpdateMessageDto

/**
 * Integration: the `/ws` client on the app's real OkHttp client against a scripted server that
 * upgrades to a WebSocket, through the server-URL and auth interceptors.
 */
class LiveUpdatesClientTest {
    private val server = MockWebServer()
    private val ended = mutableListOf<Pair<SessionEndReason, String>>()
    private lateinit var base: HttpUrl
    private lateinit var client: LiveUpdatesClient

    @BeforeEach
    fun setUp() {
        server.start()
        base = server.url("/petkit/app/v1/")
        val http = PetkitHttpClient.okHttpClient({
            base
        }, { "secret-token" }, { reason, sent -> ended += reason to sent })
        client = LiveUpdatesClient(http)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    /** Upgrades the next request and runs [script] on the server's side of the socket. */
    private fun upgrade(script: (WebSocket) -> Unit) {
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) = script(webSocket)
                },
            ).build(),
        )
    }

    @Test
    fun `frames become messages, unknown ones are skipped, and 4401 ends with the session`() = runTest {
        upgrade { socket ->
            socket.send("""{"type":"hello","server_time":1.0,"heartbeat_sec":25,"devices":[]}""")
            socket.send("""{"type":"something_new","x":1}""")
            socket.send("""{"type":"device_removed","device_id":7}""")
            socket.close(CloseCodes.SESSION_ENDED, "session ended")
        }

        val updates = client.updates().toList()

        assertEquals(
            listOf(
                LiveUpdate.Message(UpdateMessageDto.Hello(1.0, 25.0, emptyList())),
                LiveUpdate.Message(UpdateMessageDto.DeviceRemoved(7)),
                LiveUpdate.Closed(DomainError.SessionEnded(SessionEndReason.ClosedByServer)),
            ),
            updates,
        )
        val request = server.takeRequest()
        // Rewritten to the server, below its prefix, with the bearer header.
        assertEquals("/petkit/app/v1/ws", request.url.encodedPath)
        assertEquals("Bearer secret-token", request.headers["Authorization"])
    }

    @Test
    fun `the server going away is a network failure to recover from`() = runTest {
        upgrade { socket -> socket.close(CloseCodes.GOING_AWAY, "shutting down") }

        val closed = client.updates().toList().single()

        assertEquals(LiveUpdate.Closed(DomainError.Network("WebSocket closed with code 1001")), closed)
    }

    @Test
    fun `a refused upgrade with invalid_token ends the session`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(401)
                .addHeader("Content-Type", "application/json")
                .body("""{"error":"invalid_token","message":"Sign in again"}""")
                .build(),
        )

        val closed = client.updates().toList().single() as LiveUpdate.Closed

        assertEquals(DomainError.SessionEnded(SessionEndReason.InvalidToken), closed.error)
        assertEquals(listOf(SessionEndReason.InvalidToken to "secret-token"), ended)
    }

    @Test
    fun `an unreachable server is a network failure`() = runTest {
        server.close()

        val closed = client.updates().toList().single() as LiveUpdate.Closed

        assertInstanceOf(DomainError.Network::class.java, closed.error)
    }

    @Test
    fun `cancelling the collection closes the socket normally`() = runTest {
        val serverSaw = LinkedBlockingQueue<Int>()
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send("""{"type":"pong"}""")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        serverSaw.put(code)
                        webSocket.close(code, null)
                    }
                },
            ).build(),
        )

        assertEquals(LiveUpdate.Message(UpdateMessageDto.Pong), client.updates().first())

        assertEquals(CloseCodes.NORMAL, serverSaw.poll(5, TimeUnit.SECONDS))
    }
}

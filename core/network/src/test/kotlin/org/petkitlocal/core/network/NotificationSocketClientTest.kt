package org.petkitlocal.core.network

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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.network.dto.NotificationSocketMessageDto
import org.petkitlocal.core.network.dto.PushMessageDto

/**
 * Integration: the notification socket on the app's real OkHttp client against a scripted server
 * that upgrades to a WebSocket: the `after` it passes, the messages it reads, and its close codes.
 */
class NotificationSocketClientTest {
    private val server = MockWebServer()
    private val ended = mutableListOf<Pair<SessionEndReason, String>>()
    private lateinit var base: HttpUrl
    private lateinit var client: NotificationSocketClient

    @BeforeEach
    fun setUp() {
        server.start()
        base = server.url("/petkit/app/v1/")
        val http = PetkitHttpClient.okHttpClient({
            base
        }, { "secret-token" }, { reason, sent -> ended += reason to sent })
        client = NotificationSocketClient(http)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

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
    fun `hello, then notifications, an unknown type skipped, and 4401 ends the session`() = runTest {
        upgrade { socket ->
            socket.send(
                """{"type":"hello","server_time":1.5,"heartbeat_sec":45,"last_id":12,"replayed":1,"gap":false}""",
            )
            socket.send(
                """{"type":"notification","id":12,"replay":true,"message":""" +
                    """{"v":1,"kind":"toilet","device_id":100,"pet_id":2,"code":"10","ts":1763150000,"new":"x"}}""",
            )
            socket.send("""{"type":"something_new"}""")
            socket.close(CloseCodes.SESSION_ENDED, "session ended")
        }

        val events = client.open(after = 11).toList()

        assertEquals(
            listOf(
                NotificationSocketEvent.Message(NotificationSocketMessageDto.Hello(1.5, 45.0, 12, 1, gap = false)),
                NotificationSocketEvent.Message(
                    NotificationSocketMessageDto.Delivery(
                        id = 12,
                        replay = true,
                        message = PushMessageDto(1, "toilet", 100, 2, "10", 1_763_150_000),
                    ),
                ),
                NotificationSocketEvent.Closed(DomainError.SessionEnded(SessionEndReason.ClosedByServer)),
            ),
            events,
        )
        val request = server.takeRequest()
        assertEquals("/petkit/app/v1/notifications/ws", request.url.encodedPath)
        assertEquals("11", request.url.queryParameter("after"))
        assertEquals("Bearer secret-token", request.headers["Authorization"])
    }

    @Test
    fun `the first connection passes no after`() = runTest {
        upgrade { socket -> socket.close(CloseCodes.GOING_AWAY, "shutting down") }

        val closed = client.open(after = null).toList().single()

        assertEquals(NotificationSocketEvent.Closed(DomainError.Network("WebSocket closed with code 1001")), closed)
        assertNull(server.takeRequest().url.queryParameter("after"))
    }

    @Test
    fun `a socket replaced by another of the session says so`() = runTest {
        upgrade { socket -> socket.close(CloseCodes.REPLACED, "replaced") }

        assertEquals(NotificationSocketEvent.Closed(null, replaced = true), client.open(after = 3).toList().single())
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

        val closed = client.open(after = null).toList().single() as NotificationSocketEvent.Closed

        assertEquals(DomainError.SessionEnded(SessionEndReason.InvalidToken), closed.error)
        assertEquals(listOf(SessionEndReason.InvalidToken to "secret-token"), ended)
    }
}

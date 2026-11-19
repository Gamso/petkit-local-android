package org.petkitlocal.core.network

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.talk.TalkFailure
import org.robolectric.RobolectricTestRunner

/**
 * Integration: the talk socket on the app's real OkHttp client against a scripted server that
 * upgrades to a WebSocket, through the server-URL and auth interceptors.
 */
@RunWith(RobolectricTestRunner::class)
class TalkSocketClientTest {
    private val server = MockWebServer()
    private val ended = LinkedBlockingQueue<Pair<SessionEndReason, String>>()
    private lateinit var base: HttpUrl
    private val client by lazy {
        PetkitHttpClient.okHttpClient({ base }, { "secret-token" }, { reason, token -> ended += reason to token })
    }
    private val socket by lazy { TalkSocketClient(client) { reason, token -> ended += reason to token } }

    /** What the client sent: `text:<json>`, `bytes:<size>` and `close:<code>`, in order. */
    private val received = LinkedBlockingQueue<String>()

    @Before
    fun setUp() {
        server.start()
        base = server.url("/petkit/app/v1/")
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Upgrades the next request; [onOpen] runs on the server's side once it is open. */
    private fun upgrade(onText: (WebSocket, String) -> Unit = { _, _ -> }, onOpen: (WebSocket) -> Unit) {
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) = onOpen(webSocket)

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        received += "text:$text"
                        onText(webSocket, text)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        received += "bytes:${bytes.size}"
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        received += "close:$code"
                        webSocket.close(code, null)
                    }
                },
            ).build(),
        )
    }

    private fun TalkConnection.all(): List<TalkSocketEvent> = runBlocking {
        withTimeout(TIMEOUT_MS) { events.toList() }
    }

    private fun next(): String = received.poll(2, TimeUnit.SECONDS) ?: "nothing"

    @Test
    fun `ready, audio, stop answered stopped, and a normal close`() {
        upgrade(onText = { ws, text ->
            if (text == STOP) {
                ws.send("""{"type":"stopped"}""")
                ws.close(1000, null)
            }
        }) { it.send(READY) }
        val connection = socket.open(1003)
        val events = mutableListOf<TalkSocketEvent>()
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                connection.events.collect { event ->
                    events += event
                    if (event == TalkSocketEvent.Ready) {
                        assertTrue(connection.send(ByteArray(640)))
                        connection.stop()
                    }
                }
            }
        }

        assertEquals(listOf(TalkSocketEvent.Ready, TalkSocketEvent.Stopped, TalkSocketEvent.Ended(null)), events)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/petkit/app/v1/devices/1003/talk", request.url.encodedPath)
        assertEquals("Bearer secret-token", request.headers["Authorization"])
        assertEquals(listOf("bytes:640", "text:$STOP", "close:1000"), listOf(next(), next(), next()))
    }

    @Test
    fun `each error code is a failure, whatever the close that follows`() {
        val expected = mapOf(
            "not_a_camera" to TalkFailure.NotACamera,
            "needs_patcher" to TalkFailure.NeedsPatcher,
            "device_unreachable" to TalkFailure.DeviceUnreachable,
            "busy" to TalkFailure.Busy,
            "transcoder_failed" to TalkFailure.TranscoderFailed("ffmpeg: not found"),
            "idle_timeout" to TalkFailure.IdleTimeout,
            "on_fire" to TalkFailure.Other("on_fire", "ffmpeg: not found"),
        )
        for ((code, failure) in expected) {
            upgrade {
                it.send("""{"type":"error","code":"$code","message":"ffmpeg: not found"}""")
                it.close(4000, code)
            }
            assertEquals(listOf(TalkSocketEvent.Ended(failure)), socket.open(1003).all())
        }
    }

    @Test
    fun `each close code without an error has its meaning, and 4401 ends the session`() {
        val expected = mapOf(
            1001 to TalkFailure.ServerStopping,
            1009 to TalkFailure.Network("the server refused an audio frame as too big"),
            4000 to TalkFailure.Network("the talk closed with code 4000"),
            4401 to TalkFailure.SessionEnded,
        )
        for ((code, failure) in expected) {
            upgrade { it.close(code, "bye") }
            assertEquals(listOf(TalkSocketEvent.Ended(failure)), socket.open(1003).all())
        }
        assertEquals(SessionEndReason.ClosedByServer to "secret-token", ended.poll(1, TimeUnit.SECONDS))
        assertTrue(ended.isEmpty())
    }

    @Test
    fun `a refused upgrade is read like a refused request`() {
        server.enqueue(
            MockResponse.Builder().code(404)
                .addHeader("Content-Type", "application/json")
                .body("""{"error":"device_not_found","message":"no such device"}""")
                .build(),
        )
        val ended = socket.open(99).all().single() as TalkSocketEvent.Ended
        val refused = ended.failure as TalkFailure.Refused
        assertEquals(DomainError.NotFound("device_not_found", "no such device"), refused.error)
    }

    @Test
    fun `closing says stop, closes normally and ends the events at once with its failure`() {
        upgrade { it.send(READY) }
        val connection = socket.open(1003)
        val events = runBlocking {
            withTimeout(TIMEOUT_MS) {
                val collected = mutableListOf<TalkSocketEvent>()
                connection.events.collect {
                    collected += it
                    if (it == TalkSocketEvent.Ready) connection.close(TalkFailure.MicrophoneUnavailable)
                }
                collected
            }
        }

        assertEquals(listOf(TalkSocketEvent.Ready, TalkSocketEvent.Ended(TalkFailure.MicrophoneUnavailable)), events)
        assertEquals(listOf("text:$STOP", "close:1000"), listOf(next(), next()))
        assertEquals(false, connection.send(ByteArray(2)))
    }

    @Test
    fun `messages decode, and an unknown one is skipped`() {
        assertEquals(
            org.petkitlocal.core.network.dto.TalkMessageDto.Ready("s16le", 16000, 1),
            decodeTalkMessage(READY),
        )
        assertEquals(null, decodeTalkMessage("""{"type":"later"}"""))
        assertEquals(null, decodeTalkMessage("not json"))
    }

    private companion object {
        const val READY = """{"type":"ready","format":"s16le","sample_rate":16000,"channels":1}"""
        const val STOP = """{"type":"stop"}"""
        const val TIMEOUT_MS = 5_000L
    }
}

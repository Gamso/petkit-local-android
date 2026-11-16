package org.petkitlocal.core.network.video

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.network.PetkitHttpClient
import org.robolectric.RobolectricTestRunner

/**
 * Integration: the live socket as Media3 reads it, on the app's real OkHttp client against a
 * scripted server that upgrades to a WebSocket, through the server-URL and auth interceptors.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveSocketDataSourceTest {
    private val server = MockWebServer()
    private val ended = LinkedBlockingQueue<Pair<SessionEndReason, String>>()
    private lateinit var base: HttpUrl
    private val client by lazy {
        PetkitHttpClient.okHttpClient({ base }, { "secret-token" }, { reason, token -> ended += reason to token })
    }

    /** What the client sent on the socket, text messages and the close it ended with. */
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

    private fun source(
        startTimeout: kotlin.time.Duration = 5.seconds,
        maxBuffered: Long = LiveSocketDataSource.MAX_BUFFERED_BYTES,
    ) = LiveSocketDataSource(client, CODECS, { reason, token -> ended += reason to token }, startTimeout, maxBuffered)

    private fun spec(deviceId: Long = 1003) =
        DataSpec.Builder().setUri(LiveVideoRoutes.socket(deviceId).toString()).build()

    /** Upgrades the next request; [onRequest] runs on the server's side when the client's first text arrives. */
    private fun upgrade(onRequest: (WebSocket, String) -> Unit) {
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(
                object : WebSocketListener() {
                    private var first = true

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        received += text
                        if (first) onRequest(webSocket, text)
                        first = false
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        received += "close:$code"
                        webSocket.close(code, null)
                    }
                },
            ).build(),
        )
    }

    private fun LiveSocketDataSource.readExactly(count: Int): ByteArray {
        val bytes = ByteArray(count)
        var read = 0
        while (read < count) read += read(bytes, read, count - read)
        return bytes
    }

    private fun failureOf(block: () -> Unit): LiveFailure {
        try {
            block()
        } catch (e: LiveStreamException) {
            return e.failure
        }
        fail("no LiveStreamException")
        error("unreachable")
    }

    @Test
    fun `it asks for the codecs, then reads the init segment and the fragments in order`() {
        upgrade { socket, _ ->
            socket.send("""{"type":"mse","value":"video/mp4; codecs=\"avc1.42C01E\""}""")
            socket.send(INIT)
            socket.send(FRAGMENT)
        }
        val source = source()

        assertEquals(C.LENGTH_UNSET.toLong(), source.open(spec()))
        assertEquals("video/mp4; codecs=\"avc1.42C01E\"", source.streamMimeType)
        assertArrayEquals(INIT.toByteArray() + FRAGMENT.toByteArray(), source.readExactly(INIT.size + FRAGMENT.size))
        source.close()

        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/petkit/app/v1/devices/1003/live", request.url.encodedPath)
        assertNull(request.url.encodedQuery)
        assertEquals("Bearer secret-token", request.headers["Authorization"])
        assertEquals("""{"type":"mse","value":"$CODECS"}""", received.poll(1, TimeUnit.SECONDS))
        // Closing the source closes the socket normally.
        assertEquals("close:1000", received.poll(2, TimeUnit.SECONDS))
    }

    @Test
    fun `an error message instead of the stream fails the start`() {
        upgrade { socket, _ -> socket.send("""{"type":"error","value":"camera busy"}""") }
        val source = source()

        assertEquals(LiveFailure.StreamError("camera busy"), failureOf { source.open(spec()) })
        source.close()
    }

    @Test
    fun `each close code ends the stream with its meaning, after the bytes already received`() {
        val expected = mapOf(
            1011 to LiveFailure.StreamEnded,
            1001 to LiveFailure.StreamEnded,
            1013 to LiveFailure.TooSlow,
            4409 to LiveFailure.Replaced,
            4401 to LiveFailure.SessionEnded,
            4000 to LiveFailure.Network("the live stream closed with code 4000"),
        )
        for ((code, failure) in expected) {
            upgrade { socket, _ ->
                socket.send("""{"type":"mse","value":"video/mp4"}""")
                socket.send(INIT)
                socket.close(code, "bye")
            }
            val source = source()
            source.open(spec())

            assertArrayEquals(INIT.toByteArray(), source.readExactly(INIT.size))
            assertEquals("close code $code", failure, failureOf { source.readExactly(1) })
            source.close()
        }
        // 4401 ended the session it was opened with, once.
        assertEquals(SessionEndReason.ClosedByServer to "secret-token", ended.poll(1, TimeUnit.SECONDS))
        assertNull(ended.poll(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a refused upgrade fails the start with the server's error`() {
        server.enqueue(
            MockResponse.Builder()
                .code(429)
                .addHeader("Retry-After", "5")
                .body("""{"error":"live_limit_reached","message":"too many viewers"}""")
                .build(),
        )
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"invalid_token","message":"gone"}""").build())

        assertEquals(LiveFailure.Refused(DomainError.RateLimited(5.seconds)), failureOf { source().open(spec()) })
        assertEquals(LiveFailure.SessionEnded, failureOf { source().open(spec()) })
        // The 401 ended the session through the auth interceptor, as for any request.
        assertEquals(SessionEndReason.InvalidToken to "secret-token", ended.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun `no answer to the request fails the start after the timeout`() {
        upgrade { _, _ -> }
        val source = source(startTimeout = 300.milliseconds)

        val failure = failureOf { source.open(spec()) }

        assertTrue(failure.toString(), failure is LiveFailure.Network)
        source.close()
    }

    @Test
    fun `a full pipe holds the socket until the player reads`() {
        val sent = CountDownLatch(1)
        upgrade { socket, _ ->
            socket.send("""{"type":"mse","value":"video/mp4"}""")
            repeat(PIPE_CHUNKS) { socket.send(ByteArray(CHUNK) { i -> (it + i).toByte() }.toByteString()) }
            sent.countDown()
        }
        val source = source(maxBuffered = 2L * CHUNK)
        source.open(spec())
        assertTrue(sent.await(2, TimeUnit.SECONDS))

        // Everything arrives, in order, although the pipe holds two chunks at most.
        repeat(PIPE_CHUNKS) { n ->
            assertArrayEquals(ByteArray(CHUNK) { i -> (n + i).toByte() }, source.readExactly(CHUNK))
        }
        source.close()
    }

    @Test
    fun `an HLS file refused by the server reads as its error, a lapsed view as a reason to start again`() {
        fun refused(status: Int, body: String, headers: Map<String, List<String>> = emptyMap()) = liveFailureOf(
            PlaybackException(
                "load failed",
                HttpDataSource.InvalidResponseCodeException(
                    status,
                    null,
                    IOException(),
                    headers,
                    spec(),
                    body.toByteArray(),
                ),
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            ),
        )

        assertEquals(
            LiveFailure.Network("hls_session_expired"),
            refused(404, """{"error":"hls_session_expired","message":"lapsed"}"""),
        )
        assertEquals(
            LiveFailure.Refused(DomainError.NotFound("stream_not_found", "no stream")),
            refused(404, """{"error":"stream_not_found","message":"no stream"}"""),
        )
        assertEquals(
            LiveFailure.Refused(DomainError.RateLimited(5.seconds)),
            refused(429, """{"error":"live_limit_reached","message":"full"}""", mapOf("retry-after" to listOf("5"))),
        )
        assertEquals(LiveFailure.SessionEnded, refused(401, """{"error":"invalid_token","message":"gone"}"""))
    }

    @Test
    fun `a stream the phone cannot decode is a playback failure, a broken connection a network one`() {
        val decoding = PlaybackException("no decoder", null, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        val parsing = PlaybackException("bad box", null, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)
        val network =
            PlaybackException("reset", IOException("reset"), PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        val thrown =
            PlaybackException(
                "closed",
                LiveStreamException(LiveFailure.TooSlow),
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            )

        assertTrue(liveFailureOf(decoding) is LiveFailure.Playback)
        assertTrue(liveFailureOf(parsing) is LiveFailure.Playback)
        assertEquals(LiveFailure.Network("reset"), liveFailureOf(network))
        assertEquals(LiveFailure.TooSlow, liveFailureOf(thrown))
    }

    @Test
    fun `the codec list offers what the phone decodes, H264 always`() {
        assertEquals("avc1.640029,avc1.64002A,avc1.640033", LiveCodecs.mse(emptySet()))
        assertEquals(
            "avc1.640029,avc1.64002A,avc1.640033,hvc1.1.6.L153.B0,mp4a.40.2,mp4a.40.5,opus",
            LiveCodecs.mse(setOf("video/avc", "video/hevc", "audio/mp4a-latm", "audio/opus")),
        )
    }

    @Test
    fun `the HLS fallback starts from the playlist without an id`() {
        assertEquals(
            "http://petkit-local.invalid/app/v1/devices/7/live/hls/playlist.m3u8",
            LiveVideoRoutes.hlsPlaylist(7).toString(),
        )
        val sources = LiveMediaSources(client, { _, _ -> }) { CODECS }
        assertEquals(
            LiveVideoRoutes.hlsPlaylist(7).toString(),
            sources.create(7, LiveTransport.Hls).mediaItem.localConfiguration!!.uri.toString(),
        )
        assertEquals(
            LiveVideoRoutes.socket(7).toString(),
            sources.create(7, LiveTransport.WebSocket).mediaItem.localConfiguration!!.uri.toString(),
        )
    }

    private companion object {
        const val CODECS = "avc1.640029,mp4a.40.2"
        const val CHUNK = 1000
        const val PIPE_CHUNKS = 10
        val INIT: ByteString = "ftypmoov-init".encodeToByteArray().toByteString()
        val FRAGMENT: ByteString = "moofmdat-fragment".encodeToByteArray().toByteString()
    }
}

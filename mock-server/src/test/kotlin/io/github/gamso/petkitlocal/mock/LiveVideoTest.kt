package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.http.checkSessions
import io.github.gamso.petkitlocal.mock.media.LivePattern
import io.github.gamso.petkitlocal.mock.seed.Seed
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer

class LiveVideoTest {
    private val pattern = LivePattern.bundled
    private val socketPath = "/app/v1/devices/${Seed.T5}/live"

    private suspend fun Api.liveSocket(
        token: String? = this.token,
        block: suspend DefaultClientWebSocketSession.() -> Unit,
    ) {
        client.webSocket(socketPath, request = { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }) { block() }
    }

    private suspend fun DefaultClientWebSocketSession.request() =
        send(Frame.Text("""{"type":"mse","value":"avc1.640029,avc1.42E01E,mp4a.40.2"}"""))

    private suspend fun DefaultClientWebSocketSession.next(): Frame = withTimeout(5000) { incoming.receive() }

    private suspend fun DefaultClientWebSocketSession.nextText(): Pair<String, String> {
        val frame = next()
        assertTrue(frame is Frame.Text, "expected a text message, got $frame")
        val message = Json.parseToJsonElement((frame as Frame.Text).readText()).jsonObject
        return message["type"]!!.jsonPrimitive.content to message["value"]!!.jsonPrimitive.content
    }

    private suspend fun DefaultClientWebSocketSession.nextBinary(): ByteArray {
        val frame = next()
        assertTrue(frame is Frame.Binary, "expected a binary message, got $frame")
        return frame.readBytes()
    }

    private suspend fun DefaultClientWebSocketSession.closeCode(): Short? =
        withTimeout(5000) {
            runCatching { while (true) incoming.receive() }
            closeReason.await()?.code
        }

    private fun boxType(bytes: ByteArray) = String(bytes, 4, 4, Charsets.US_ASCII)

    /** The `tfdt` decode time of a fragment from the pattern (moof, mfhd, traf, tfhd, tfdt). */
    private fun decodeTime(fragment: ByteArray): Long {
        var at = 8
        while (boxType(fragment.copyOfRange(at, at + 8)) != "traf") at += ByteBuffer.wrap(fragment, at, 4).int
        at += 8
        while (boxType(fragment.copyOfRange(at, at + 8)) != "tfdt") at += ByteBuffer.wrap(fragment, at, 4).int
        return ByteBuffer.wrap(fragment, at + 12, 8).long
    }

    @Test
    fun `the pattern is twelve half-second H264 fragments that loop with continuous timestamps`() {
        assertEquals(12, pattern.size)
        assertEquals(500, pattern.fragmentMillis)
        assertEquals("avc1.42C01E", pattern.codec)
        assertEquals("video/mp4; codecs=\"avc1.42C01E\"", pattern.mime)
        assertEquals("ftyp", boxType(pattern.init))

        val first = pattern.fragment(0)
        val looped = pattern.fragment(12)
        assertEquals("moof", boxType(looped))
        assertEquals(first.size, looped.size)
        assertEquals(0, decodeTime(first))
        assertEquals(12 * pattern.fragmentTicks, decodeTime(looped))
        // Only the sequence number and the decode time differ.
        val differing = first.indices.filter { first[it] != looped[it] }
        assertTrue(differing.size <= 12, "$differing")
    }

    @Test
    fun `a socket answers the MIME type, then the init segment and fragments from the live edge, in order`() =
        mockTest { _, api ->
            api.login()
            api.liveSocket {
                request()
                assertEquals("mse" to pattern.mime, nextText())
                assertArrayEquals(pattern.init, nextBinary())
                val first = nextBinary()
                val second = nextBinary()
                assertEquals("moof", boxType(first))
                assertEquals(decodeTime(first) + pattern.fragmentTicks, decodeTime(second))
            }
            Coverage.record("GET", "/devices/{id}/live", 101)
        }

    @Test
    fun `anything but one mse request is answered with an error and not acted on`() =
        mockTest { _, api ->
            api.login()
            api.liveSocket {
                send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
                send(Frame.Text("""{"type":"hello"}"""))
                assertEquals("error", nextText().first)
                request()
                assertEquals("mse", nextText().first)
                request()
                // The init segment may come before the refusal; the refusal comes.
                var refused = false
                repeat(3) { if (!refused && next().let { it is Frame.Text && "error" in it.readText() }) refused = true }
                assertTrue(refused)
            }
        }

    @Test
    fun `the same session opening the camera again replaces its older view with 4409`() =
        mockTest { _, api ->
            api.login()
            api.liveSocket {
                request()
                nextText()
                api.liveSocket {
                    request()
                    assertEquals("mse", nextText().first)
                }
                assertEquals(4409.toShort(), closeCode())
            }
        }

    @Test
    fun `a camera serves two viewers and the server four, HLS views included`() =
        mockTest { _, api ->
            val phones = (1..3).map { Api(api.client).apply { login(name = "phone $it") } }
            val hls = "/devices/{id}/live/hls/{file}"
            assertEquals(200, phones[0].get(hls, "/devices/${Seed.T5}/live/hls/playlist.m3u8").status)
            phones[1].liveSocket {
                request()
                nextText()
                val refused = phones[2].get(hls, "/devices/${Seed.T5}/live/hls/playlist.m3u8")
                assertEquals(429, refused.status)
                assertEquals("live_limit_reached", refused.error)
                assertNotNull(refused.headers[HttpHeaders.RetryAfter])
                // Refused before the upgrade (429): the client sees no socket.
                assertThrows<Exception> { phones[2].liveSocket {} }
                // Another camera still has room, up to the server's four.
                assertEquals(200, phones[2].get(hls, "/devices/${Seed.T6}/live/hls/playlist.m3u8").status)
                assertEquals(200, phones[2].get(hls, "/devices/${Seed.T7}/live/hls/playlist.m3u8").status)
                assertEquals(429, phones[0].get(hls, "/devices/${Seed.D4SH}/live/hls/playlist.m3u8").status)
            }
        }

    @Test
    fun `the control API closes sockets with a code and makes the next opens fail with an error`() =
        mockTest { _, api ->
            api.login()
            api.liveSocket {
                request()
                nextText()
                api.client.control(HttpMethod.Post, "/live/close", """{"code":1013}""")
                assertEquals(1013.toShort(), closeCode())
            }
            api.client.control(HttpMethod.Post, "/live/error", """{"message":"camera busy","next":1}""")
            api.liveSocket {
                request()
                assertEquals("error" to "camera busy", nextText())
            }
            api.liveSocket {
                request()
                assertEquals("mse", nextText().first)
                nextBinary()
                assertEquals(
                    1,
                    Json
                        .parseToJsonElement(
                            api.client.control(HttpMethod.Post, "/live/error").text,
                        ).jsonObject["sent"]!!
                        .jsonPrimitive.content
                        .toInt(),
                )
                // Fragments already queued may come first; the error comes.
                var error: String? = null
                repeat(4) { if (error == null) (next() as? Frame.Text)?.let { error = it.readText() } }
                assertTrue("the camera did not answer" in error.orEmpty())
            }
        }

    @Test
    fun `a socket closes with 4401 when its session is revoked`() =
        mockTest { world, api ->
            api.login()
            api.liveSocket {
                request()
                nextText()
                api.client.control(HttpMethod.Delete, "/sessions")
                world.checkSessions()
                assertEquals(4401.toShort(), closeCode())
            }
        }

    @Test
    fun `HLS opens a view from a master playlist and serves its media playlist, init and segments`() =
        mockTest { _, api ->
            api.login()
            val hls = "/devices/{id}/live/hls/{file}"
            val base = "/devices/${Seed.T5}/live/hls"
            val master = api.get(hls, "$base/playlist.m3u8")
            assertEquals(200, master.status)
            val variant = master.text.lines().single { it.startsWith("playlist.m3u8?id=") }
            val id = variant.substringAfter("id=")
            assertTrue("CODECS=\"avc1.42c01e\"" in master.text)

            // The first fragment is complete half a second after the stream started.
            Thread.sleep(pattern.fragmentMillis + 100)
            val media = api.get(hls, "$base/$variant")
            assertEquals(200, media.status)
            assertTrue("#EXT-X-MAP:URI=\"init.mp4?id=$id\"" in media.text, media.text)
            val segment = media.text.lines().last { it.startsWith("segment.m4s") }

            val init = api.get(hls, "$base/init.mp4?id=$id")
            assertArrayEquals(pattern.init, init.bytes)
            val fragment = api.get(hls, "$base/$segment")
            assertEquals(200, fragment.status)
            assertEquals("moof", boxType(fragment.bytes))

            assertEquals("hls_session_not_found", api.get(hls, "$base/init.mp4?id=nope").error)
            assertEquals("hls_session_not_found", api.get(hls, "$base/init.mp4").error)
            assertEquals("not_found", api.get(hls, "$base/segment.m4s?id=$id&n=999999").error)
            val other = Api(api.client).apply { login(name = "other phone") }
            assertEquals("hls_session_not_found", other.get(hls, "$base/init.mp4?id=$id").error)
        }

    @Test
    fun `refusals happen before the upgrade`() =
        mockTest { _, api ->
            api.login()
            val live = "/devices/{id}/live"
            assertEquals(426, api.get(live, "/devices/${Seed.T5}/live").status)
            assertEquals("stream_not_found", api.get(live, "/devices/${Seed.T4}/live").error)
            assertEquals("device_not_found", api.get(live, "/devices/99/live").error)
            assertEquals(401, api.get(live, "/devices/${Seed.T5}/live", token = null).status)
            assertThrows<Exception> { api.liveSocket(token = null) {} }
        }
}

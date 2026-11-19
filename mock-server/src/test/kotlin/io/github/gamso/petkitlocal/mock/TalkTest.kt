package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.http.checkSessions
import io.github.gamso.petkitlocal.mock.seed.Seed
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `/devices/{id}/talk`: ready, audio counted, stop, the refusals on the open socket and the control API. */
class TalkTest {
    private suspend fun Api.talk(
        deviceId: Int = Seed.T5,
        token: String? = this.token,
        block: suspend DefaultClientWebSocketSession.() -> Unit,
    ) {
        client.webSocket("/app/v1/devices/$deviceId/talk", request = { token?.let { header(HttpHeaders.Authorization, "Bearer $it") } }) {
            block()
        }
    }

    private suspend fun DefaultClientWebSocketSession.nextJson(): JsonObject {
        val frame = withTimeout(5000) { incoming.receive() }
        assertTrue(frame is Frame.Text, "expected a text message, got $frame")
        return Json.parseToJsonElement((frame as Frame.Text).readText()).jsonObject
    }

    private suspend fun DefaultClientWebSocketSession.closeCode(): Short? =
        withTimeout(5000) {
            runCatching { while (true) incoming.receive() }
            closeReason.await()?.code
        }

    private suspend fun Api.mockState(): JsonObject = client.control(HttpMethod.Get, "").json

    @Test
    fun `ready, then the audio is counted, and stop is answered stopped and 1000`() =
        mockTest { _, api ->
            api.login()
            api.talk {
                val ready = nextJson()
                assertEquals("ready", ready.type)
                assertEquals("s16le", ready.str("format"))
                assertEquals(16000, ready["sample_rate"]!!.jsonPrimitive.int)
                assertEquals(1, ready["channels"]!!.jsonPrimitive.int)
                assertEquals(1, api.mockState()["talk_sockets"]!!.jsonPrimitive.int)

                send(Frame.Binary(true, ByteArray(640)))
                send(Frame.Binary(true, ByteArray(1280)))
                send(Frame.Text("""{"type":"hello"}""")) // ignored
                send(Frame.Text("""{"type":"stop"}"""))
                assertEquals("stopped", nextJson().type)
                assertEquals(1000.toShort(), closeCode())
            }
            assertEquals(1920L, api.mockState()["talk_bytes"]!!.jsonPrimitive.long)
            assertEquals(0, api.mockState()["talk_sockets"]!!.jsonPrimitive.int)
        }

    @Test
    fun `a device that cannot talk says why on the socket, then closes 4000`() =
        mockTest { _, api ->
            api.login()
            for ((device, code) in listOf(Seed.T6 to "needs_patcher", Seed.T7 to "device_unreachable", Seed.T4 to "not_a_camera")) {
                api.talk(device) {
                    val error = nextJson()
                    assertEquals("error", error.type)
                    assertEquals(code, error.str("code"))
                    assertTrue(error.str("message")!!.isNotBlank())
                    assertEquals(4000.toShort(), closeCode())
                }
            }
        }

    @Test
    fun `a second talk on a device is busy, and the first goes on`() =
        mockTest { _, api ->
            api.login()
            api.talk {
                assertEquals("ready", nextJson().type)
                api.talk {
                    val error = nextJson()
                    assertEquals("busy", error.str("code"))
                    assertEquals(4000.toShort(), closeCode())
                }
                send(Frame.Binary(true, ByteArray(10)))
                send(Frame.Text("""{"type":"stop"}"""))
                assertEquals("stopped", nextJson().type)
            }
        }

    @Test
    fun `a frame over 64 KiB closes with 1009`() =
        mockTest { _, api ->
            api.login()
            api.talk {
                assertEquals("ready", nextJson().type)
                send(Frame.Binary(true, ByteArray(64 * 1024 + 1)))
                assertEquals(1009.toShort(), closeCode())
            }
        }

    @Test
    fun `the control API ends open talks with an error, and refuses the next opens`() =
        mockTest { _, api ->
            api.login()
            api.talk {
                assertEquals("ready", nextJson().type)
                val sent = api.client.control(HttpMethod.Post, "/talk/error", """{"code":"idle_timeout","next":1}""")
                assertEquals(1, sent["sent"].jsonPrimitive.int)
                val error = nextJson()
                assertEquals("idle_timeout", error.str("code"))
                assertEquals(4000.toShort(), closeCode())
            }
            api.talk {
                assertEquals("idle_timeout", nextJson().str("code"))
                assertEquals(4000.toShort(), closeCode())
            }
            api.talk { assertEquals("ready", nextJson().type) }
            assertEquals(400, api.client.control(HttpMethod.Post, "/talk/error", """{"code":"nope"}""").status)
        }

    @Test
    fun `the control API closes talks with a code, and a revoked session closes them with 4401`() =
        mockTest { world, api ->
            api.login()
            api.talk {
                assertEquals("ready", nextJson().type)
                api.client.control(HttpMethod.Post, "/talk/close", """{"code":1001}""")
                assertEquals(1001.toShort(), closeCode())
            }
            api.talk {
                assertEquals("ready", nextJson().type)
                api.client.control(HttpMethod.Delete, "/sessions")
                world.checkSessions()
                assertEquals(4401.toShort(), closeCode())
            }
        }

    @Test
    fun `a device's talk is changed by the control API and announced on the socket`() =
        mockTest { _, api ->
            val token = api.login()
            api.client.webSocket("/app/v1/ws", request = { header(HttpHeaders.Authorization, "Bearer $token") }) {
                assertEquals("hello", incoming.nextMessage().type)
                api.client.control(HttpMethod.Put, "/devices/${Seed.T6}/talk", """{"talk":"available"}""")
                val device = incoming.until { it.type == "device" }.last()["device"]!!.jsonObject
                assertEquals(Seed.T6, device["id"]!!.jsonPrimitive.int)
                assertEquals("available", device.str("talk"))
            }
            api.talk(Seed.T6) { assertEquals("ready", nextJson().type) }
            assertEquals(400, api.client.control(HttpMethod.Put, "/devices/${Seed.T6}/talk", """{"talk":"maybe"}""").status)
        }
}

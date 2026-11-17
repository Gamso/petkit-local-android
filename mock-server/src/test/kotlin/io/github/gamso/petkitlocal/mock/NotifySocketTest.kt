package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.seed.Seed
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/** `GET /notifications/ws`: the hello, live notifications, replay with `after`, and the close codes. */
class NotifySocketTest {
    private suspend fun Api.notifications(
        after: Long? = null,
        block: suspend DefaultClientWebSocketSession.() -> Unit,
    ) {
        val t = token ?: login()
        val query = after?.let { "?after=$it" }.orEmpty()
        client.webSocket("/app/v1/notifications/ws$query", request = { header(HttpHeaders.Authorization, "Bearer $t") }) { block() }
    }

    /** The next text frame, checked against `NotificationSocketMessage`. */
    private suspend fun ReceiveChannel<Frame>.next(): JsonObject =
        withTimeout(5000) {
            var message: JsonObject? = null
            while (message == null) {
                val frame = receive()
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val problems = Contract.checkNotificationMessage(text)
                    if (problems.isNotEmpty()) {
                        fail<Unit>(
                            "message violates NotificationSocketMessage:\n${problems.joinToString("\n")}\n$text",
                        )
                    }
                    message = Json.parseToJsonElement(text).jsonObject
                }
            }
            message
        }

    private suspend fun DefaultClientWebSocketSession.closeCode(): Short? =
        withTimeout(5000) {
            runCatching { while (true) incoming.receive() }
            closeReason.await()?.code
        }

    private suspend fun Api.send(
        kind: String = "toilet",
        deviceId: Int = Seed.T4,
    ): Long? {
        val r = client.control(HttpMethod.Post, "/notifications", """{"kind":"$kind","device_id":$deviceId,"pet_id":1,"code":"10"}""")
        assertEquals(200, r.status, r.text)
        return r.json["id"]
            ?.jsonPrimitive
            ?.content
            ?.toLongOrNull()
    }

    private val JsonObject.id: Long get() = this["id"]!!.jsonPrimitive.long

    @Test
    fun `hello first, then each notification as it is sent, and the settings say connected`() =
        mockTest { _, api ->
            api.notifications {
                val hello = incoming.next()
                assertEquals("hello", hello.type)
                assertEquals(0, hello["last_id"]!!.jsonPrimitive.int)
                assertEquals(0, hello["replayed"]!!.jsonPrimitive.int)
                assertEquals(false, hello["gap"]!!.jsonPrimitive.boolean)

                assertEquals(1L, api.send())
                val delivery = incoming.next()
                assertEquals("notification", delivery.type)
                assertEquals(1L, delivery.id)
                assertEquals(false, delivery["replay"]!!.jsonPrimitive.boolean)
                val message = delivery["message"]!!.jsonObject
                assertEquals("toilet", message["kind"]!!.jsonPrimitive.content)
                assertEquals(Seed.T4, message["device_id"]!!.jsonPrimitive.int)

                val settings = api.get("/notifications")
                assertEquals(true, settings["connected"].jsonPrimitive.boolean)
                assertEquals(1, settings["last_id"].jsonPrimitive.int)

                send("""{"type":"ping"}""")
                assertEquals("pong", incoming.next().type)
            }
            Coverage.record("GET", "/notifications/ws", 101)
        }

    @Test
    fun `what was sent while away is replayed after the id passed back, oldest first, then live`() =
        mockTest { _, api ->
            api.notifications { incoming.next() } // the session is now owed catch-up
            api.send()
            api.send(kind = "error")
            api.send(kind = "offline")

            api.notifications(after = 1) {
                val hello = incoming.next()
                assertEquals(3, hello["last_id"]!!.jsonPrimitive.int)
                assertEquals(2, hello["replayed"]!!.jsonPrimitive.int)
                val replayed = listOf(incoming.next(), incoming.next())
                assertEquals(listOf(2L, 3L), replayed.map { it.id })
                assertTrue(replayed.all { it["replay"]!!.jsonPrimitive.boolean })
                api.send()
                val live = incoming.next()
                assertEquals(4L, live.id)
                assertEquals(false, live["replay"]!!.jsonPrimitive.boolean)
            }
        }

    @Test
    fun `the session's kinds and the panel's switches choose what is logged and sent`() =
        mockTest { _, api ->
            api.login()
            api.put("/notifications", body = """{"kinds":["error","toilet"]}""")
            api.client.control(HttpMethod.Put, "/notifications", """{"disabled":["toilet"]}""")
            api.notifications {
                incoming.next()
                assertEquals(null, api.send(kind = "offline"))
                assertEquals(null, api.send(kind = "toilet"))
                assertEquals(1L, api.send(kind = "error"))
                assertEquals(1L, incoming.next().id)
            }
        }

    @Test
    fun `nothing is logged before a session opened the socket once`() =
        mockTest { _, api ->
            api.login()
            assertEquals(null, api.send())
        }

    @Test
    fun `an id from a log that is gone replays everything held, with a gap`() =
        mockTest { _, api ->
            api.notifications { incoming.next() }
            api.send()
            api.client.control(HttpMethod.Delete, "/notifications/log")
            api.send(kind = "error")

            api.notifications(after = 7) {
                val hello = incoming.next()
                assertEquals(1, hello["last_id"]!!.jsonPrimitive.int)
                assertEquals(true, hello["gap"]!!.jsonPrimitive.boolean)
                assertEquals(1, hello["replayed"]!!.jsonPrimitive.int)
                assertEquals(
                    "error",
                    incoming
                        .next()["message"]!!
                        .jsonObject["kind"]!!
                        .jsonPrimitive.content,
                )
            }
        }

    @Test
    fun `a second socket of the session replaces the first with 4409`() =
        mockTest { _, api ->
            api.notifications {
                incoming.next()
                api.notifications {
                    incoming.next()
                }
                assertEquals(4409.toShort(), closeCode())
            }
        }

    @Test
    fun `a session that ends closes its socket with 4401`() =
        mockTest { world, api ->
            api.notifications {
                incoming.next()
                val id =
                    world.auth
                        .sessions()
                        .single()
                        .id
                api.client.control(HttpMethod.Delete, "/sessions/$id")
                assertEquals(4401.toShort(), closeCode())
            }
        }

    @Test
    fun `the control API closes every notification socket with the code given`() =
        mockTest { _, api ->
            api.notifications {
                incoming.next()
                api.client.control(HttpMethod.Post, "/notifications/close", """{"code":1013}""")
                assertEquals(1013.toShort(), closeCode())
            }
        }

    @Test
    fun `a device trigger sends the notification petkit-local would`() =
        mockTest { _, api ->
            api.notifications {
                incoming.next()
                api.client.control(HttpMethod.Post, "/devices/${Seed.T4}/offline")
                val message = incoming.next()["message"]!!.jsonObject
                assertEquals("offline", message["kind"]!!.jsonPrimitive.content)
                assertEquals(Seed.T4, message["device_id"]!!.jsonPrimitive.int)
            }
        }

    @Test
    fun `a bad after, a plain GET and no session are refused before any upgrade`() =
        mockTest { _, api ->
            assertEquals(401, api.get("/notifications/ws").status)
            api.login()
            assertEquals(400, api.get("/notifications/ws").status)
            val refused = api.get("/notifications/ws", "/notifications/ws?after=-1")
            assertEquals(400, refused.status)
            assertEquals("bad_parameter", refused.error)
        }
}

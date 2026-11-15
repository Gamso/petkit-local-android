package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.seed.Seed
import io.github.gamso.petkitlocal.mock.state.AppSession
import io.github.gamso.petkitlocal.mock.state.Subscriber
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebSocketTest {
    private suspend fun Api.live(block: suspend DefaultClientWebSocketSession.() -> Unit) {
        val t = token ?: login()
        client.webSocket("/app/v1/ws", request = { header(HttpHeaders.Authorization, "Bearer $t") }) { block() }
    }

    private suspend fun DefaultClientWebSocketSession.closeCode(): Short? =
        withTimeout(5000) {
            // Drain until the server's close arrives.
            runCatching { while (true) incoming.receive() }
            closeReason.await()?.code
        }

    @Test
    fun `hello carries every device in full, first`() =
        mockTest { _, api ->
            api.live {
                val hello = incoming.nextMessage()
                assertEquals("hello", hello.type)
                assertEquals(25.0, hello["heartbeat_sec"]!!.jsonPrimitive.content.toDouble())
                val devices = hello["devices"]!!.jsonArray
                assertEquals(13, devices.size)
                assertTrue(
                    devices.all {
                        it.jsonObject["entities"]!!.jsonArray.isNotEmpty() ||
                            it.jsonObject["type"]!!.jsonPrimitive.content == "k3"
                    },
                )
            }
        }

    @Test
    fun `an action plays out as state changes and a timeline card that replaces its progress card`() =
        mockTest { _, api ->
            api.login()
            api.live {
                incoming.nextMessage()
                api.post("/devices/{id}/actions/{action}", "/devices/${Seed.T4}/actions/cleaning_start")
                val seen = incoming.until { it.type == "timeline" && it["replaces"]!!.jsonArray.isNotEmpty() }
                val states = seen.filter { it.type == "state" }
                // Busy, then idle: only the entities whose value changed each time.
                val first = states.first()
                assertEquals(Seed.T4, first["device_id"]!!.jsonPrimitive.int)
                assertEquals(listOf("device_status"), first["entities"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content })
                assertEquals(
                    "cleaning",
                    first["entities"]!!
                        .jsonArray[0]
                        .jsonObject["display"]!!
                        .jsonPrimitive.content,
                )
                val progress = seen.first { it.type == "timeline" }["item"]!!.jsonObject
                assertEquals("Mechanism started", progress["label"]!!.jsonPrimitive.content)
                val done = seen.last()
                assertEquals(listOf(progress["id"]), done["replaces"]!!.jsonArray.toList())
                assertEquals("Manual cleaning completed", done["item"]!!.jsonObject["label"]!!.jsonPrimitive.content)
                val idle = states.last()["entities"]!!.jsonArray.associate { it.jsonObject["key"]!!.jsonPrimitive.content to it.jsonObject }
                assertEquals("idle", idle["device_status"]!!["display"]!!.jsonPrimitive.content)
                assertTrue("last_clean" in idle && "litter_percent" in idle)
            }
        }

    @Test
    fun `an entity write sends a state message with that entity only`() =
        mockTest { _, api ->
            api.login()
            api.live {
                incoming.nextMessage()
                api.put("/devices/{id}/entities/{key}", "/devices/${Seed.T4}/entities/child_lock", """{"value":true}""")
                val state = incoming.nextMessage()
                assertEquals("state", state.type)
                assertEquals(1, state["entities"]!!.jsonArray.size)
                val e = state["entities"]!!.jsonArray[0].jsonObject
                assertEquals("child_lock", e["key"]!!.jsonPrimitive.content)
                assertEquals(true, e["value"]!!.jsonPrimitive.content.toBoolean())
            }
        }

    @Test
    fun `a visit is an in-progress card replaced by its summary, then its video and its cleaning`() =
        mockTest { _, api ->
            api.live {
                incoming.nextMessage()
                api.client.control(HttpMethod.Post, "/devices/${Seed.T5}/visit", """{"pet_id":2,"weight_g":6050,"duration_sec":75}""")
                // The recording and the cleaning both follow the summary, in either order.
                var cleaned = false
                var recorded = false
                val seen =
                    incoming.until(10_000) {
                        if (it.type == "timeline") {
                            val item = it["item"]!!.jsonObject
                            if (item["steps"]!!.jsonArray.size >= 3) cleaned = true
                            val media = item["media"] as? kotlinx.serialization.json.JsonObject
                            if (media != null && media["playback"] !is kotlinx.serialization.json.JsonNull) recorded = true
                        }
                        cleaned && recorded
                    }
                val cards = seen.filter { it.type == "timeline" }.map { it["item"]!!.jsonObject }
                val started = cards.first()
                assertEquals("9", started["event_type"]!!.jsonPrimitive.content)
                val summary = seen.first { it.type == "timeline" && it["replaces"]!!.jsonArray.isNotEmpty() }
                assertEquals(listOf(started["id"]), summary["replaces"]!!.jsonArray.toList())
                val item = summary["item"]!!.jsonObject
                assertEquals(6050, item["weight_g"]!!.jsonPrimitive.int)
                assertEquals(75, item["duration_sec"]!!.jsonPrimitive.int)
                assertEquals("Tigrou", item["pet"]!!.jsonObject["name"]!!.jsonPrimitive.content)
                assertTrue(
                    item["media"]!!
                        .jsonObject["video_pending"]!!
                        .jsonPrimitive.content
                        .toBoolean(),
                )
                // Same id each time the card changes.
                assertTrue(cards.drop(1).all { it["id"] == item["id"] })
                assertTrue(
                    cards.any {
                        (it["media"] as? kotlinx.serialization.json.JsonObject)?.get("video_pending")?.jsonPrimitive?.content ==
                            "false"
                    },
                )
                val pet = seen.filter { it.type == "state" }.flatMap { it["entities"]!!.jsonArray }.map { it.jsonObject }
                assertTrue(pet.any { it["key"]!!.jsonPrimitive.content == "pet_occupied" && it["value"]!!.jsonPrimitive.content == "true" })
            }
            // `state` carries only what changed, and the seeded history is laid out back from the
            // current time: depending on the hour, its last visit may already be Tigrou's, and then
            // `last_pet` is never sent. The stored value is what must be right.
            val detail = Json.parseToJsonElement(api.get("/devices/{id}", "/devices/${Seed.T5}").text).jsonObject
            val lastPet =
                detail["device"]!!
                    .jsonObject["entities"]!!
                    .jsonArray
                    .map { it.jsonObject }
                    .first { it["key"]!!.jsonPrimitive.content == "last_pet" }
            assertEquals("Tigrou", lastPet["value"]!!.jsonPrimitive.content)
        }

    @Test
    fun `offline, online and removal are device messages`() =
        mockTest { _, api ->
            api.live {
                incoming.nextMessage()
                api.client.control(HttpMethod.Post, "/devices/${Seed.T5}/offline")
                val off = incoming.nextMessage()
                assertEquals("device", off.type)
                assertFalse(
                    off["device"]!!
                        .jsonObject["online"]!!
                        .jsonPrimitive.content
                        .toBoolean(),
                )
                assertEquals("http", off["device"]!!.jsonObject["transport"]!!.jsonPrimitive.content)
                api.client.control(HttpMethod.Post, "/devices/${Seed.T5}/online")
                val on = incoming.nextMessage()
                assertEquals("mqtt", on["device"]!!.jsonObject["transport"]!!.jsonPrimitive.content)
                api.client.control(HttpMethod.Post, "/devices/${Seed.K3}/remove")
                val removed = incoming.nextMessage()
                assertEquals("device_removed", removed.type)
                assertEquals(Seed.K3, removed["device_id"]!!.jsonPrimitive.int)
            }
        }

    @Test
    fun `an accessory follows its parent online and offline, and its parent hears of its removal`() =
        mockTest { _, api ->
            api.live {
                incoming.nextMessage()
                api.client.control(HttpMethod.Post, "/devices/${Seed.D4SH}/offline")
                val messages = listOf(incoming.nextMessage(), incoming.nextMessage())
                val w5 = messages.single { it["device"]!!.jsonObject["id"]!!.jsonPrimitive.int == Seed.W5 }
                assertFalse(
                    w5["device"]!!
                        .jsonObject["online"]!!
                        .jsonPrimitive.content
                        .toBoolean(),
                )
                api.client.control(HttpMethod.Post, "/devices/${Seed.D4SH}/online")
                repeat(2) { incoming.nextMessage() }
                api.client.control(HttpMethod.Post, "/devices/${Seed.K3}/remove")
                assertEquals("device_removed", incoming.nextMessage().type)
                val parent = incoming.nextMessage()
                assertEquals(Seed.T4, parent["device"]!!.jsonObject["id"]!!.jsonPrimitive.int)
                assertEquals(0, parent["device"]!!.jsonObject["accessories"]!!.jsonArray.size)
            }
        }

    @Test
    fun `an application ping is answered with a pong, anything else is ignored`() =
        mockTest { _, api ->
            api.live {
                incoming.nextMessage()
                send(Frame.Text("""{"type":"hello?"}"""))
                send(Frame.Text("not json"))
                send(Frame.Text("""{"type":"ping"}"""))
                assertEquals("pong", incoming.nextMessage().type)
            }
        }

    @Test
    fun `logout from another socket's session closes it with 4401`() =
        mockTest { _, api ->
            val token = api.login()
            api.live {
                incoming.nextMessage()
                api.post("/auth/logout", token = token)
                assertEquals(Subscriber.CLOSE_SESSION_ENDED, closeCode())
            }
        }

    @Test
    fun `revocation, a password change and expiry close the socket with 4401`() =
        mockTest { world, api ->
            api.login()
            api.live {
                incoming.nextMessage()
                api.client.control(HttpMethod.Delete, "/sessions")
                assertEquals(Subscriber.CLOSE_SESSION_ENDED, closeCode())
            }
            api.login()
            api.live {
                incoming.nextMessage()
                api.client.control(HttpMethod.Put, "/password", """{"password":null}""")
                assertEquals(Subscriber.CLOSE_SESSION_ENDED, closeCode())
            }
            api.client.control(HttpMethod.Put, "/password", """{"password":"demo"}""")
            api.login()
            api.live {
                incoming.nextMessage()
                world.clock.advance(91 * 86400.0)
                assertEquals(Subscriber.CLOSE_SESSION_ENDED, closeCode())
            }
        }

    @Test
    fun `the control API can close sockets with 1013 or 1001`() =
        mockTest { _, api ->
            api.live {
                incoming.nextMessage()
                api.client.control(HttpMethod.Post, "/ws/close", """{"code":1013}""")
                assertEquals(CloseReason.Codes.TRY_AGAIN_LATER.code, closeCode())
            }
            api.live {
                incoming.nextMessage()
                api.client.control(HttpMethod.Post, "/ws/close", """{"code":1001}""")
                assertEquals(CloseReason.Codes.GOING_AWAY.code, closeCode())
            }
        }

    @Test
    fun `server shutdown closes sockets with 1001`() =
        mockTest { world, api ->
            api.live {
                incoming.nextMessage()
                world.hub.end(Subscriber.CLOSE_GOING_AWAY, "server shutting down")
                assertEquals(1001.toShort(), closeCode())
            }
        }

    @Test
    fun `a client that stops reading is cut off with 1013 instead of growing memory`() {
        val sub = Subscriber(AppSession("s", "h", "n", 0.0, 0.0), limit = 3)
        repeat(3) { assertTrue(sub.push("m$it")) }
        assertFalse(sub.push("overflow"))
        assertEquals(Subscriber.CLOSE_TOO_SLOW, sub.closeCode)
        // The queue was dropped and only the close remains.
        val item = sub.channel.tryReceive().getOrNull()
        assertEquals(
            io.github.gamso.petkitlocal.mock.state.Outgoing
                .Close(1013, "too slow: messages were not being read"),
            item,
        )
        assertFalse(sub.push("after"))
    }
}

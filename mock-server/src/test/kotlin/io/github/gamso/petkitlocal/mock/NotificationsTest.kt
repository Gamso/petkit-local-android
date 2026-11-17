package io.github.gamso.petkitlocal.mock

import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** `GET`/`PUT /notifications`, with petkit-local's rules (`handlers/notifications.py`, `push.py`). */
class NotificationsTest {
    private val everyKind = listOf("toilet", "feed_served", "feed_failed", "error", "food_low", "consumable", "offline")

    private fun Resp.kinds(): List<String>? = kindsOf(this["kinds"])

    private fun kindsOf(kinds: JsonElement?): List<String>? =
        kinds.takeUnless { it is JsonNull }?.jsonArray?.map { it.jsonPrimitive.content }

    @Test
    fun `a new session gets every kind, and the server lists each with its switch`() =
        mockTest { _, api ->
            api.login()
            val settings = api.get("/notifications")

            assertEquals(null, settings.kinds())
            assertEquals(everyKind, settings["server_kinds"].jsonArray.map { it.jsonObject["kind"]!!.jsonPrimitive.content })
            assertEquals(true, settings["server_kinds"].jsonArray.all { it.jsonObject["enabled"]!!.jsonPrimitive.boolean })
            assertEquals(false, settings["connected"].jsonPrimitive.boolean)
            assertEquals(0, settings["last_id"].jsonPrimitive.int)
        }

    @Test
    fun `the choice is kept in the server's order, without unknown kinds, and shown on the session`() =
        mockTest { _, api ->
            api.login()
            val set = api.put("/notifications", body = """{"kinds":["offline","toilet","later_kind","toilet"]}""")

            assertEquals(listOf("toilet", "offline"), set.kinds())
            assertEquals(listOf("toilet", "offline"), api.get("/notifications").kinds())
            assertEquals(listOf("toilet", "offline"), kindsOf(api.get("/info")["session"].jsonObject["kinds"]))

            assertEquals(emptyList<String>(), api.put("/notifications", body = """{"kinds":[]}""").kinds())
            assertEquals(null, api.put("/notifications", body = """{"kinds":null}""").kinds())
        }

    @Test
    fun `kinds are per session`() =
        mockTest { _, api ->
            val first = api.login(name = "first")
            api.put("/notifications", body = """{"kinds":["error"]}""")
            api.login(name = "second")

            assertEquals(null, api.get("/notifications").kinds())
            assertEquals(listOf("error"), api.get("/notifications", token = first).kinds())
        }

    @Test
    fun `malformed kinds are refused with bad_parameter`() =
        mockTest { _, api ->
            api.login()
            val tooMany = (1..33).joinToString(",") { "\"k$it\"" }
            for (body in listOf("{}", """{"kinds":"toilet"}""", """{"kinds":[1]}""", """{"kinds":[$tooMany]}""")) {
                val r = api.put("/notifications", body = body)
                assertEquals(400, r.status, body)
                assertEquals("bad_parameter", r.error, body)
            }
        }

    @Test
    fun `the control API switches kinds off as the panel would, until reset`() =
        mockTest { _, api ->
            api.client.control(HttpMethod.Put, "/notifications", """{"disabled":["offline"]}""")
            api.login()
            val enabled =
                api.get("/notifications")["server_kinds"].jsonArray.associate {
                    it.jsonObject["kind"]!!.jsonPrimitive.content to it.jsonObject["enabled"]!!.jsonPrimitive.boolean
                }
            assertEquals(false, enabled["offline"])
            assertEquals(true, enabled["toilet"])

            api.client.control(HttpMethod.Post, "/reset")
            api.login()
            assertEquals(true, api.get("/notifications")["server_kinds"].jsonArray.all { it.jsonObject["enabled"]!!.jsonPrimitive.boolean })
        }
}

package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.media.MediaFactory
import io.github.gamso.petkitlocal.mock.seed.Seed
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlApiTest {
    private fun entity(
        detail: kotlinx.serialization.json.JsonObject,
        key: String,
    ) = detail["entities"]!!.jsonArray.map { it.jsonObject }.first { it["key"]!!.jsonPrimitive.content == key }

    @Test
    fun `the index lists the routes and needs no auth`() =
        mockTest { _, api ->
            val r = api.client.control(HttpMethod.Get, "")
            assertEquals(200, r.status)
            assertTrue(r["routes"].jsonArray.size > 10)
        }

    @Test
    fun `triggers - visit, meal, drink, eating, error, offline`() =
        mockTest { world, api ->
            api.login()
            val before = world.timeline.cards.size
            val visit = api.client.control(HttpMethod.Post, "/devices/${Seed.T3}/visit", """{"pet_id":1,"auto_clean":false}""")
            assertEquals(200, visit.status)
            assertEquals(422, api.client.control(HttpMethod.Post, "/devices/${Seed.D4}/visit").status)
            assertEquals(404, api.client.control(HttpMethod.Post, "/devices/${Seed.T3}/visit", """{"pet_id":9}""").status)
            assertEquals(200, api.client.control(HttpMethod.Post, "/devices/${Seed.D4}/meal", """{"amount":2}""").status)
            assertEquals(200, api.client.control(HttpMethod.Post, "/devices/${Seed.W7H}/drink", """{"pet_id":2}""").status)
            assertEquals(200, api.client.control(HttpMethod.Post, "/devices/${Seed.D4SH}/eat", """{"pet_id":2}""").status)
            assertEquals(422, api.client.control(HttpMethod.Post, "/devices/${Seed.D4}/eat").status)
            assertEquals(404, api.client.control(HttpMethod.Post, "/devices/${Seed.D4H}/eat", """{"pet_id":9}""").status)
            assertEquals(200, api.client.control(HttpMethod.Post, "/devices/${Seed.T3}/error", """{"message":"motor stuck"}""").status)
            kotlinx.coroutines.delay(300)
            assertTrue(world.timeline.cards.size >= before + 5)
            val t3 = api.get("/devices/{id}", "/devices/${Seed.T3}")["device"].jsonObject
            assertEquals("motor stuck", entity(t3, "error")["value"]!!.jsonPrimitive.content)
            assertEquals("Mochi", entity(t3, "last_pet")["value"]!!.jsonPrimitive.content)
            api.client.control(HttpMethod.Post, "/devices/${Seed.T3}/error/clear")
            val cleared = api.get("/devices/{id}", "/devices/${Seed.T3}")["device"].jsonObject
            assertEquals(kotlinx.serialization.json.JsonNull, entity(cleared, "error")["value"])
            val faults = api.get("/timeline", "/timeline?device=${Seed.T3}&kind=fault&limit=2")["items"].jsonArray
            assertEquals("Error cleared", faults[0].jsonObject["label"]!!.jsonPrimitive.content)
            assertEquals("Error - motor stuck", faults[1].jsonObject["label"]!!.jsonPrimitive.content)

            api.client.control(HttpMethod.Post, "/devices/${Seed.D4H}/offline")
            assertEquals(
                false,
                api
                    .get("/devices")["devices"]
                    .jsonArray
                    .first { it.jsonObject["id"]!!.jsonPrimitive.int == Seed.D4H }
                    .jsonObject["online"]!!
                    .jsonPrimitive.content
                    .toBoolean(),
            )
            assertEquals(404, api.client.control(HttpMethod.Post, "/devices/77/offline").status)
        }

    @Test
    fun `raw state can be set for any entity`() =
        mockTest { _, api ->
            api.login()
            api.client.control(HttpMethod.Put, "/devices/${Seed.K3}/state", """{"consumables.liquid":5}""")
            val k3 = api.get("/devices/{id}", "/devices/${Seed.K3}")["device"].jsonObject
            assertEquals(5, entity(k3, "k3_liquid")["value"]!!.jsonPrimitive.int)
        }

    @Test
    fun `a device's transport can be switched, and its accessories follow`() =
        mockTest { _, api ->
            api.login()

            suspend fun transportOf(id: Int) =
                api
                    .get("/devices")["devices"]
                    .jsonArray
                    .first { it.jsonObject["id"]!!.jsonPrimitive.int == id }
                    .jsonObject["transport"]!!
                    .jsonPrimitive.content
            assertEquals("http", transportOf(Seed.CTW3))
            assertEquals(200, api.client.control(HttpMethod.Put, "/devices/${Seed.D4}/transport", """{"transport":"mqtt"}""").status)
            assertEquals("mqtt", transportOf(Seed.D4))
            assertEquals("mqtt", transportOf(Seed.CTW3))
            assertEquals(400, api.client.control(HttpMethod.Put, "/devices/${Seed.D4}/transport", """{"transport":"ble"}""").status)
            assertEquals(400, api.client.control(HttpMethod.Put, "/devices/${Seed.CTW3}/transport", """{"transport":"mqtt"}""").status)
        }

    @Test
    fun `the clock advances, sessions are listed and revoked`() =
        mockTest { world, api ->
            val token = api.login()
            val now = world.clock.now()
            val r = api.client.control(HttpMethod.Post, "/clock/advance", """{"seconds":3600}""")
            assertTrue(r["now"].jsonPrimitive.double - now in 3599.0..3700.0)
            val sessions = api.client.control(HttpMethod.Get, "/sessions")["sessions"].jsonArray
            assertEquals(1, sessions.size)
            assertEquals(404, api.client.control(HttpMethod.Delete, "/sessions/nope").status)
            api.client.control(HttpMethod.Delete, "/sessions/${sessions[0].jsonObject["id"]!!.jsonPrimitive.content}")
            assertEquals(401, api.get("/devices", token = token).status)
        }

    @Test
    fun `forced failures answer the next N requests of a route, then stop`() =
        mockTest { _, api ->
            api.login()
            api.client.control(
                HttpMethod.Post,
                "/fail",
                """{"method":"POST","path":"/devices/{id}/actions/{action}","status":422,"error":"refused","times":2}""",
            )
            repeat(2) { assertEquals("refused", api.post("/devices/{id}/actions/{action}", "/devices/${Seed.T4}/actions/deodorize").error) }
            assertEquals(200, api.post("/devices/{id}/actions/{action}", "/devices/${Seed.T4}/actions/deodorize").status)
            api.client.control(HttpMethod.Post, "/fail", """{"path":"/devices","status":401,"error":"invalid_token"}""")
            val r = api.get("/devices")
            assertEquals("invalid_token", r.error)
            assertEquals("Bearer realm=\"petkit-local\"", r.headers[HttpHeaders.WWWAuthenticate])
            api.client.control(HttpMethod.Post, "/fail", """{"method":"POST","path":"/auth/login","status":429,"retry_after":30}""")
            assertEquals("30", api.post("/auth/login", body = """{"password":"demo"}""").headers[HttpHeaders.RetryAfter])
            api.client.control(HttpMethod.Post, "/fail", """{"path":"/pets","status":503}""")
            api.client.control(HttpMethod.Delete, "/fail")
            assertEquals(200, api.get("/pets").status)
        }

    @Test
    fun `reset brings back the seed and drops sessions`() =
        mockTest { world, api ->
            val token = api.login()
            api.client.control(HttpMethod.Post, "/devices/${Seed.K3}/remove")
            api.client.control(HttpMethod.Put, "/password", """{"password":"other password"}""")
            assertEquals(12, world.devices.size)
            api.client.control(HttpMethod.Post, "/reset")
            assertEquals(13, world.devices.size)
            assertEquals(401, api.get("/devices", token = token).status)
            assertNotEquals(token, api.login("demo"))
        }

    @Test
    fun `proxy mode is switched from the control API and reported by the index`() =
        mockTest { _, api ->
            api.login()
            val on = api.client.control(HttpMethod.Put, "/proxy", """{"proxy_mode":true}""")
            assertEquals(200, on.status)
            val said = listOf("proxy_mode", "pets_source", "effective").map { on[it].jsonPrimitive.content }
            assertEquals(listOf("true", "petkit", "petkit"), said)
            api.patch("/settings/pets-source", body = """{"pets_source":"local"}""")
            val index = api.client.control(HttpMethod.Get, "")
            assertEquals(true, index["proxy_mode"].jsonPrimitive.boolean)
            assertEquals("local", index["pets_source"].jsonPrimitive.content)
            assertEquals(400, api.client.control(HttpMethod.Put, "/proxy", """{"proxy_mode":"yes"}""").status)
        }

    @Test
    fun `supports_ai can be switched on a device, with a device message, never on an accessory`() =
        mockTest { world, api ->
            api.login()
            assertEquals(200, api.client.control(HttpMethod.Put, "/devices/${Seed.D4H}/supports-ai", """{"supports_ai":true}""").status)
            val d4h = api.get("/devices/{id}", "/devices/${Seed.D4H}")["device"].jsonObject
            assertEquals(true, d4h["supports_ai"]!!.jsonPrimitive.boolean)
            assertEquals(400, api.client.control(HttpMethod.Put, "/devices/${Seed.W5}/supports-ai", """{"supports_ai":true}""").status)
            assertEquals(400, api.client.control(HttpMethod.Put, "/devices/${Seed.D4H}/supports-ai", """{}""").status)
            assertEquals(404, api.client.control(HttpMethod.Put, "/devices/77/supports-ai", """{"supports_ai":true}""").status)
            world.reset()
            assertEquals(
                false,
                world.devices
                    .getValue(Seed.D4H)
                    .summary()
                    .supportsAi,
            )
        }

    @Test
    fun `reset brings back the pets, their faces and the pets source`() =
        mockTest { world, api ->
            api.login()
            api.post("/pets", body = """{"name":"Pixel"}""")
            api.delete("/pets/{id}", "/pets/1")
            api.upload("/pets/{id}/faces", "/pets/2/faces", MediaFactory.jpeg(240, 240, 1))
            api.client.control(HttpMethod.Put, "/proxy", """{"proxy_mode":true}""")
            api.patch("/settings/pets-source", body = """{"pets_source":"local"}""")
            api.client.control(HttpMethod.Post, "/reset")
            api.login()
            val pets = api.get("/pets")["pets"].jsonArray.map { it.jsonObject }
            assertEquals(listOf("Mochi", "Tigrou"), pets.map { it.str("name") })
            assertEquals(listOf(1, 2), pets[0].arr("faces").map { it.jsonObject["id"]!!.jsonPrimitive.int })
            assertEquals(0, pets[1].arr("faces").size)
            assertEquals(3, world.nextFaceId)
            assertEquals(
                3,
                api
                    .post("/pets", body = """{"name":"Pixel"}""")["pet"]
                    .jsonObject["id"]!!
                    .jsonPrimitive.int,
            )
            val source = api.get("/settings/pets-source").json
            assertEquals(listOf("petkit", "false"), listOf(source.str("pets_source"), source["proxy_mode"]!!.jsonPrimitive.content))
        }

    @Test
    fun `the scenario engine rolls events on demand`() =
        mockTest { world, api ->
            api.login()
            val before = world.timeline.cards.size + world.devices.values.sumOf { it.doc.hashCode() }
            repeat(15) { assertEquals(200, api.client.control(HttpMethod.Post, "/scenario/tick").status) }
            kotlinx.coroutines.delay(300)
            assertNotEquals(before, world.timeline.cards.size + world.devices.values.sumOf { it.doc.hashCode() })
            assertEquals(200, api.client.control(HttpMethod.Put, "/scenario", """{"interval_sec":0}""").status)
        }

    @Test
    fun `the control API is absent when disabled`() =
        mockTest(testConfig(control = false)) { _, api ->
            assertEquals(404, api.client.control(HttpMethod.Post, "/reset").status)
            assertEquals(404, api.client.control(HttpMethod.Get, "").status)
        }
}

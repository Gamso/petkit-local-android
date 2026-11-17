package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.seed.Seed
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

/**
 * Every operation of `api/openapi.yaml`, its success and its documented refusals, each response
 * validated against the spec by [Api] as it arrives. The last test checks nothing in the spec
 * went uncalled, so a new route in petkit-local fails this suite until the mock serves it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ContractTest {
    @Test
    @Order(1)
    fun `meta - info and the openapi file`() =
        mockTest { _, api ->
            assertEquals(200, api.get("/info").status)
            val yaml = api.get("/openapi.yaml")
            assertEquals(200, yaml.status)
            assertTrue(yaml.text.startsWith("openapi: 3.1.0"))
            api.login()
            val info = api.get("/info")
            assertEquals(true, info["authenticated"].jsonPrimitive.content.toBoolean())
        }

    @Test
    @Order(2)
    fun `auth - login, logout, password`() =
        mockTest { _, api ->
            assertEquals(400, api.post("/auth/login", body = "[1]").status)
            assertEquals(415, api.call(HttpMethod.Post, "/auth/login", body = "password=demo", contentType = ContentType.Text.Plain).status)
            assertEquals(401, api.post("/auth/login", body = """{"password":"nope"}""").status)
            assertEquals(429, api.post("/auth/login", body = """{"password":"demo"}""").status)
        }

    @Test
    @Order(3)
    fun `auth - session routes`() =
        mockTest { _, api ->
            api.login()
            assertEquals(400, api.post("/auth/password", body = "{").status)
            assertEquals(415, api.call(HttpMethod.Post, "/auth/password", body = "x", contentType = ContentType.Text.Plain).status)
            assertEquals(400, api.post("/auth/password", body = """{"current_password":"demo","new_password":"short"}""").status)
            assertEquals(200, api.post("/auth/password", body = """{"current_password":"demo","new_password":"long enough"}""").status)
            assertEquals(401, api.post("/auth/password", body = """{"current_password":"demo","new_password":"long enough"}""").status)
            assertEquals(
                429,
                api.post("/auth/password", body = """{"current_password":"long enough","new_password":"other one!"}""").status,
            )
            assertEquals(200, api.post("/auth/logout").status)
            assertEquals(401, api.post("/auth/logout").status)
        }

    @Test
    @Order(4)
    fun `devices - list, detail, actions, entities`() =
        mockTest { _, api ->
            api.login()
            assertEquals(13, api.get("/devices")["devices"].jsonArray.size)
            Seed.DEVICES.forEach { assertEquals(200, api.get("/devices/{id}", "/devices/${it.id}").status) }
            assertEquals(404, api.get("/devices/{id}", "/devices/99").status)
            assertEquals(404, api.get("/devices/{id}", "/devices/abc").status)

            val action = "/devices/{id}/actions/{action}"
            assertEquals(200, api.post(action, "/devices/${Seed.T4}/actions/cleaning_start").status)
            assertEquals(422, api.post(action, "/devices/${Seed.T4}/actions/dump_litter").status)
            assertEquals(200, api.post(action, "/devices/${Seed.T4}/actions/dump_litter", """{"confirm":true}""").status)
            assertEquals(400, api.post(action, "/devices/${Seed.T4}/actions/deodorize", "nope").status)
            assertEquals(
                415,
                api
                    .call(
                        HttpMethod.Post,
                        action,
                        "/devices/${Seed.T4}/actions/deodorize",
                        "x",
                        contentType = ContentType.Text.Plain,
                    ).status,
            )
            assertEquals(404, api.post(action, "/devices/${Seed.T4}/actions/feed").status)
            assertEquals(404, api.post(action, "/devices/99/actions/feed").status)
            assertEquals(409, api.post(action, "/devices/${Seed.CTW3}/actions/ctw3_reset_filter", """{"confirm":true}""").status)

            val entity = "/devices/{id}/entities/{key}"
            assertEquals(200, api.put(entity, "/devices/${Seed.T4}/entities/cleaning_delay", """{"value":600}""").status)
            assertEquals(422, api.put(entity, "/devices/${Seed.T4}/entities/cleaning_delay", """{"value":4000}""").status)
            assertEquals(422, api.put(entity, "/devices/${Seed.T4}/entities/litter_percent", """{"value":1}""").status)
            assertEquals(400, api.put(entity, "/devices/${Seed.T4}/entities/cleaning_delay", """{"v":1}""").status)
            assertEquals(
                415,
                api
                    .call(
                        HttpMethod.Put,
                        entity,
                        "/devices/${Seed.T4}/entities/child_lock",
                        "x",
                        contentType = ContentType.Text.Plain,
                    ).status,
            )
            assertEquals(404, api.put(entity, "/devices/${Seed.T4}/entities/nope", """{"value":1}""").status)
            assertEquals(409, api.put(entity, "/devices/${Seed.CTW3}/entities/ctw3_light", """{"value":true}""").status)
            assertEquals(200, api.put(entity, "/devices/${Seed.W5}/entities/w5_light", """{"value":true}""").status)
            assertEquals(401, api.put(entity, "/devices/${Seed.T4}/entities/child_lock", """{"value":true}""", token = "bad").status)
        }

    @Test
    @Order(5)
    fun `feeding - plan and one-off feeds`() =
        mockTest { world, api ->
            api.login()
            val schedule = "/devices/{id}/schedule"
            val plan = api.get(schedule, "/devices/${Seed.D4}/schedule")
            assertEquals(200, plan.status)
            assertEquals(404, api.get(schedule, "/devices/${Seed.T4}/schedule").status)
            assertEquals(200, api.put(schedule, "/devices/${Seed.D4}/schedule", """{"active":false}""").status)
            assertEquals(400, api.put(schedule, "/devices/${Seed.D4}/schedule", """{}""").status)
            assertEquals(
                415,
                api.call(HttpMethod.Put, schedule, "/devices/${Seed.D4}/schedule", "x", contentType = ContentType.Text.Plain).status,
            )
            assertEquals(422, api.put(schedule, "/devices/${Seed.D4}/schedule", """{"groups":[{"days":[8],"meals":[]}]}""").status)
            assertEquals(404, api.put(schedule, "/devices/${Seed.W7H}/schedule", """{"active":true}""").status)

            val feeds = "/devices/{id}/deferred-feeds"
            assertEquals(200, api.get(feeds, "/devices/${Seed.D4H}/deferred-feeds").status)
            assertEquals(404, api.get(feeds, "/devices/${Seed.T5}/deferred-feeds").status)
            val tomorrow = world.clock.today().plusDays(1)
            val created = api.post(feeds, "/devices/${Seed.D4}/deferred-feeds", """{"date":"$tomorrow","time":"09:15","amount":2}""")
            assertEquals(201, created.status)
            assertEquals(
                422,
                api.post(feeds, "/devices/${Seed.D4}/deferred-feeds", """{"date":"2001-01-01","time":"09:15","amount":2}""").status,
            )
            assertEquals(400, api.post(feeds, "/devices/${Seed.D4}/deferred-feeds", "[]").status)
            assertEquals(
                415,
                api.call(HttpMethod.Post, feeds, "/devices/${Seed.D4}/deferred-feeds", "x", contentType = ContentType.Text.Plain).status,
            )
            assertEquals(
                404,
                api.post(feeds, "/devices/${Seed.T5}/deferred-feeds", """{"date":"$tomorrow","time":"09:15","amount":2}""").status,
            )

            val one = "/devices/{id}/deferred-feeds/{feed_id}"
            val id = created["feed"].jsonObject["id"]!!.jsonPrimitive.content
            assertEquals(200, api.delete(one, "/devices/${Seed.D4}/deferred-feeds/$id").status)
            assertEquals(404, api.delete(one, "/devices/${Seed.D4}/deferred-feeds/$id").status)
            assertEquals(401, api.delete(one, "/devices/${Seed.D4}/deferred-feeds/$id", token = null).status)
        }

    @Test
    @Order(6)
    fun `video - the stream, HLS, and the documented refusals`() =
        mockTest { _, api ->
            api.login()
            val live = "/devices/{id}/live"
            assertEquals(426, api.get(live, "/devices/${Seed.T5}/live").status)
            assertEquals(404, api.get(live, "/devices/${Seed.T4}/live").status)
            assertEquals(404, api.get(live, "/devices/99/live").status)
            assertEquals(401, api.get(live, "/devices/${Seed.T5}/live", token = null).status)
            api.client.control(HttpMethod.Post, "/fail", """{"method":"GET","path":"$live","status":503,"error":"live_unavailable"}""")
            assertEquals(503, api.get(live, "/devices/${Seed.T5}/live").status)
            api.client.control(
                HttpMethod.Post,
                "/fail",
                """{"method":"GET","path":"$live","status":429,"error":"live_limit_reached","retry_after":5}""",
            )
            assertEquals(429, api.get(live, "/devices/${Seed.T5}/live").status)
            api.client.webSocket(
                "/app/v1/devices/${Seed.T5}/live",
                request = { header(HttpHeaders.Authorization, "Bearer ${api.token}") },
            ) {
                send(
                    io.ktor.websocket.Frame
                        .Text("""{"type":"mse","value":"avc1.42E01E"}"""),
                )
                val answer = (kotlinx.coroutines.withTimeout(5000) { incoming.receive() } as io.ktor.websocket.Frame.Text).readText()
                assertTrue(answer.startsWith("""{"type":"mse""""), answer)
            }
            Coverage.record("GET", live, 101)

            val hls = "/devices/{id}/live/hls/{file}"
            val master = api.get(hls, "/devices/${Seed.T5}/live/hls/playlist.m3u8")
            assertEquals(200, master.status)
            val variant = master.text.lines().single { it.startsWith("playlist.m3u8?id=") }
            assertEquals(200, api.get(hls, "/devices/${Seed.T5}/live/hls/$variant").status)
            assertEquals(200, api.get(hls, "/devices/${Seed.T5}/live/hls/init.mp4?id=${variant.substringAfter("id=")}").status)
            assertEquals(404, api.get(hls, "/devices/${Seed.T4}/live/hls/playlist.m3u8").status)
            assertEquals(404, api.get(hls, "/devices/${Seed.T5}/live/hls/evil.sh").status)
            assertEquals(401, api.get(hls, "/devices/${Seed.T5}/live/hls/init.mp4", token = null).status)
            api.client.control(HttpMethod.Post, "/fail", """{"method":"GET","path":"$hls","status":503,"error":"live_unavailable"}""")
            assertEquals(503, api.get(hls, "/devices/${Seed.T5}/live/hls/playlist.m3u8").status)
            api.client.control(HttpMethod.Post, "/fail", """{"method":"GET","path":"$hls","status":502,"error":"bad_upstream"}""")
            assertEquals(502, api.get(hls, "/devices/${Seed.T5}/live/hls/playlist.m3u8").status)
        }

    @Test
    @Order(7)
    fun `timeline, media, pets`() =
        mockTest { _, api ->
            api.login()
            val page = api.get("/timeline", "/timeline?limit=50")
            assertEquals(200, page.status)
            assertEquals(400, api.get("/timeline", "/timeline?cursor=zzz").status)
            val items = page["items"].jsonArray
            val firstId =
                items
                    .first()
                    .jsonObject["id"]!!
                    .jsonPrimitive.int
            assertEquals(200, api.get("/timeline/{event_id}", "/timeline/$firstId").status)
            assertEquals(404, api.get("/timeline/{event_id}", "/timeline/1").status)

            val withMedia = items.map { it.jsonObject }.first { it["media"] !is kotlinx.serialization.json.JsonNull }
            val media = withMedia["media"]!!.jsonObject
            val snapshot = media["snapshot"]!!.jsonPrimitive.content.removePrefix("/app/v1")
            val thumb = media["thumbnail"]!!.jsonPrimitive.content.removePrefix("/app/v1")
            val clip = media["highlight"]!!.jsonPrimitive.content.removePrefix("/app/v1")
            assertEquals(200, api.get("/media/{path}", snapshot).status)
            assertEquals(200, api.get("/media/thumb/{path}", thumb).status)
            val ranged = api.call(HttpMethod.Get, "/media/{path}", clip, headers = mapOf(HttpHeaders.Range to "bytes=0-99"))
            assertEquals(206, ranged.status)
            assertEquals(100, ranged.bytes.size)
            assertEquals(404, api.get("/media/{path}", "/media/9999/2026-01-01/1_snapshot.jpg").status)
            assertEquals(404, api.get("/media/thumb/{path}", "/media/thumb/nope.jpg").status)
            assertEquals(401, api.get("/media/{path}", snapshot, token = null).status)
            assertEquals(401, api.get("/media/thumb/{path}", thumb, token = null).status)

            assertEquals(2, api.get("/pets")["pets"].jsonArray.size)
            assertEquals(200, api.get("/pets/{id}", "/pets/1").status)
            assertEquals(404, api.get("/pets/{id}", "/pets/9").status)
            assertEquals(200, api.get("/pets/{id}/photo", "/pets/1/photo").status)
            assertEquals(404, api.get("/pets/{id}/photo", "/pets/2/photo").status)
            assertEquals(401, api.get("/pets", token = null).status)
            assertEquals(401, api.get("/pets/{id}/photo", "/pets/1/photo", token = null).status)
            assertEquals(401, api.get("/timeline", token = null).status)
            assertEquals(401, api.get("/timeline/{event_id}", "/timeline/$firstId", token = null).status)
        }

    @Test
    @Order(8)
    fun `forced 503s are declared where the spec has them`() =
        mockTest { _, api ->
            api.login()
            for (path in listOf("/timeline", "/timeline/{event_id}", "/pets", "/pets/{id}", "/pets/{id}/photo")) {
                api.client.control(HttpMethod.Post, "/fail", """{"method":"GET","path":"$path","status":503,"error":"unavailable"}""")
                val concrete = path.replace("{event_id}", "1000").replace("{id}", "1")
                assertEquals(503, api.get(path, concrete).status)
            }
        }

    @Test
    @Order(9)
    fun `ws - upgrade, plain GET and no session`() =
        mockTest { _, api ->
            assertEquals(401, api.get("/ws").status)
            val token = api.login()
            assertEquals(400, api.get("/ws").status)
            api.client.webSocket("/app/v1/ws", request = { header(HttpHeaders.Authorization, "Bearer $token") }) {
                assertEquals("hello", incoming.nextMessage().type)
            }
            Coverage.record("GET", "/ws", 101)
        }

    @Test
    @Order(10)
    fun `notifications - this session's kinds`() =
        mockTest { _, api ->
            assertEquals(401, api.get("/notifications").status)
            api.login()
            assertEquals(200, api.get("/notifications").status)
            assertEquals(200, api.put("/notifications", body = """{"kinds":["toilet"]}""").status)
            assertEquals(200, api.put("/notifications", body = """{"kinds":null}""").status)
            assertEquals(400, api.put("/notifications", body = """{"kinds":"toilet"}""").status)
            assertEquals(400, api.put("/notifications", body = "{").status)
            assertEquals(415, api.call(HttpMethod.Put, "/notifications", body = "x", contentType = ContentType.Text.Plain).status)
            assertEquals(401, api.put("/notifications", body = """{"kinds":null}""", token = null).status)
        }

    @Test
    @Order(11)
    fun `notifications ws - upgrade, plain GET and no session`() =
        mockTest { _, api ->
            assertEquals(401, api.get("/notifications/ws").status)
            val token = api.login()
            assertEquals(400, api.get("/notifications/ws").status)
            api.client.webSocket("/app/v1/notifications/ws", request = { header(HttpHeaders.Authorization, "Bearer $token") }) {
                val hello = (incoming.receive() as Frame.Text).readText()
                assertEquals(emptyList<String>(), Contract.checkNotificationMessage(hello))
            }
            Coverage.record("GET", "/notifications/ws", 101)
        }

    /**
     * UnifiedPush: in the spec, not served, as the app does not use it (ADR 0007). Every session
     * the mock hands out says `push: null`, which is true. The notification socket is covered by
     * NotifySocketTest.
     */
    private val pending =
        setOf(
            "PUT" to "/push",
            "DELETE" to "/push",
        )

    @AfterAll
    fun `every operation of the spec was called`() {
        val missing = Contract.operations - Coverage.seen.keys - pending
        assertTrue(Contract.operations.containsAll(pending), "a pending operation left the spec: ${pending - Contract.operations}")
        assertTrue(missing.isEmpty(), "operations never called: $missing")
        val unsuccessful =
            Coverage.seen.filter { (_, statuses) -> statuses.none { it in 200..299 || it == 101 } }
        assertTrue(unsuccessful.isEmpty(), "operations never answered with success: ${unsuccessful.keys}")
    }
}

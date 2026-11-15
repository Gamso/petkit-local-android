package io.github.gamso.petkitlocal.mock

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthTest {
    @Test
    fun `no password set closes every route, public and unknown ones included`() =
        mockTest(testConfig(password = "")) { _, api ->
            for (r in listOf(
                api.get("/info"),
                api.get("/openapi.yaml"),
                api.get("/devices"),
                api.post("/auth/login", body = """{"password":"x"}"""),
            )) {
                assertEquals(401, r.status)
                assertEquals("password_not_set", r.error)
                assertEquals("Bearer realm=\"petkit-local\"", r.headers[HttpHeaders.WWWAuthenticate])
            }
            val unknown = api.client.get("/app/v1/nothing/here")
            assertEquals(401, unknown.status.value)
            assertTrue(unknown.bodyAsText().contains("password_not_set"))
        }

    @Test
    fun `info and the openapi file are public, everything else needs a session`() =
        mockTest { _, api ->
            val info = api.get("/info")
            assertEquals(200, info.status)
            assertEquals(false, info["authenticated"].jsonPrimitive.content.toBoolean())
            assertEquals(JsonNull, info["server_version"])
            assertEquals(JsonNull, info["session"])
            assertEquals(200, api.get("/openapi.yaml").status)
            val devices = api.get("/devices")
            assertEquals(401, devices.status)
            assertEquals("invalid_token", devices.error)
            // An unauthenticated caller cannot map which paths exist.
            assertEquals(
                401,
                api.client
                    .get("/app/v1/no/such/route")
                    .status.value,
            )
        }

    @Test
    fun `login gives a per-phone session, info then shows it`() =
        mockTest { _, api ->
            val r = api.post("/auth/login", body = """{"password":"demo","device_name":"Pixel\u0007 8   Pro"}""")
            assertEquals("Bearer", r["token_type"].jsonPrimitive.content)
            assertEquals(90 * 86400, r["idle_timeout_sec"].jsonPrimitive.int)
            val session = r["session"].jsonObject
            assertEquals("Pixel 8 Pro", session["name"]!!.jsonPrimitive.content)
            assertEquals(session["last_used"]!!.jsonPrimitive.double + 90 * 86400, session["expires"]!!.jsonPrimitive.double, 0.001)
            api.token = r["token"].jsonPrimitive.content
            val info = api.get("/info")
            assertEquals(true, info["authenticated"].jsonPrimitive.content.toBoolean())
            assertEquals("mock-0.1.0", info["server_version"].jsonPrimitive.content)
            assertEquals(session["id"], info["session"].jsonObject["id"])

            val other = api.post("/auth/login", body = """{"password":"demo"}""", token = null)
            assertNotEquals(api.token, other["token"].jsonPrimitive.content)
            assertEquals("Unnamed device", other["session"].jsonObject["name"]!!.jsonPrimitive.content)
        }

    @Test
    fun `wrong passwords lock the address out, doubling each time, and the clock ends it`() =
        mockTest { world, api ->
            val wrong = api.post("/auth/login", body = """{"password":"nope"}""")
            assertEquals(401, wrong.status)
            assertEquals("invalid_password", wrong.error)
            assertEquals("1", wrong.headers[HttpHeaders.RetryAfter])
            // Locked: answered 429 without the password being checked, the right one included.
            val locked = api.post("/auth/login", body = """{"password":"demo"}""")
            assertEquals(429, locked.status)
            assertEquals("too_many_attempts", locked.error)
            assertEquals("1", locked.headers[HttpHeaders.RetryAfter])
            world.clock.advance(1.1)
            assertEquals("2", api.post("/auth/login", body = """{"password":"nope"}""").headers[HttpHeaders.RetryAfter])
            world.clock.advance(2.1)
            assertEquals("4", api.post("/auth/login", body = """{"password":"nope"}""").headers[HttpHeaders.RetryAfter])
            world.clock.advance(4.1)
            assertEquals(200, api.post("/auth/login", body = """{"password":"demo"}""").status)
            // A success clears the record: the next failure starts again at 1 s.
            assertEquals("1", api.post("/auth/login", body = """{"password":"nope"}""").headers[HttpHeaders.RetryAfter])
        }

    @Test
    fun `the lockout is capped`() =
        mockTest(testConfig().copy(lockoutBaseSec = 100.0, lockoutMaxSec = 150.0)) { world, api ->
            assertEquals("100", api.post("/auth/login", body = """{"password":"x"}""").headers[HttpHeaders.RetryAfter])
            world.clock.advance(101.0)
            assertEquals("150", api.post("/auth/login", body = """{"password":"x"}""").headers[HttpHeaders.RetryAfter])
        }

    @Test
    fun `logout revokes only the calling session`() =
        mockTest { _, api ->
            val first = api.login()
            val second = api.login(name = "second")
            assertEquals(200, api.post("/auth/logout", token = first).status)
            assertEquals("invalid_token", api.get("/devices", token = first).error)
            assertEquals(200, api.get("/devices", token = second).status)
        }

    @Test
    fun `a password change needs the current one, under the login throttle`() =
        mockTest { _, api ->
            api.login()
            val refused = api.post("/auth/password", body = """{"current_password":"wrong","new_password":"new password"}""")
            assertEquals("invalid_password", refused.error)
            assertTrue(refused.headers[HttpHeaders.RetryAfter] != null)
        }

    @Test
    fun `a password change keeps the caller signed in`() =
        mockTest { _, api ->
            val other = api.login(name = "other")
            val mine = api.login(name = "mine")
            val tooShort = api.post("/auth/password", body = """{"current_password":"demo","new_password":"short"}""")
            assertEquals(400, tooShort.status)
            assertEquals("password_rejected", tooShort.error)
            val r = api.post("/auth/password", body = """{"current_password":"demo","new_password":"a new password"}""")
            assertEquals(1, r["revoked_sessions"].jsonPrimitive.int)
            assertEquals(200, api.get("/devices", token = mine).status)
            assertEquals(401, api.get("/devices", token = other).status)
            assertEquals(401, api.post("/auth/login", body = """{"password":"demo"}""", token = null).status)
        }

    @Test
    fun `a session dies after 90 days unused, and each use extends it`() =
        mockTest { world, api ->
            val token = api.login()
            world.clock.advance(89 * 86400.0)
            assertEquals(200, api.get("/devices").status)
            world.clock.advance(89 * 86400.0)
            assertEquals(200, api.get("/devices").status)
            world.clock.advance(90 * 86400.0 + 1)
            assertEquals("invalid_token", api.get("/devices", token = token).error)
        }

    @Test
    fun `revocation from the panel and a panel password change end sessions`() =
        mockTest { _, api ->
            val token = api.login()
            val id =
                api
                    .get("/info")["session"]
                    .jsonObject["id"]!!
                    .jsonPrimitive.content
            assertEquals(200, api.client.control(HttpMethod.Delete, "/sessions/$id").status)
            assertEquals(401, api.get("/devices", token = token).status)
            val again = api.login()
            api.client.control(HttpMethod.Put, "/password", """{"password":"changed!"}""")
            assertEquals(401, api.get("/devices", token = again).status)
            assertEquals(200, api.post("/auth/login", body = """{"password":"changed!"}""", token = null).status)
        }

    @Test
    fun `authenticated unknown paths are 404, wrong methods 405 with Allow`() =
        mockTest { _, api ->
            val token = api.login()
            val unknown = api.client.get("/app/v1/no/such/route") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(404, unknown.status.value)
            assertTrue(unknown.bodyAsText().contains("\"not_found\""))
            val wrong = api.client.get("/app/v1/auth/logout") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(405, wrong.status.value)
            assertEquals("POST", wrong.headers[HttpHeaders.Allow])
            // GET on the login route is not the credential route: it needs a session first.
            assertEquals(
                401,
                api.client
                    .get("/app/v1/auth/login")
                    .status.value,
            )
        }

    @Test
    fun `a bearer header in another scheme is no token`() =
        mockTest { _, api ->
            val token = api.login()
            val r = api.client.get("/app/v1/devices") { header(HttpHeaders.Authorization, "Basic $token") }
            assertEquals(401, r.status.value)
            val lower = api.client.get("/app/v1/devices") { header(HttpHeaders.Authorization, "bearer $token") }
            assertEquals(200, lower.status.value)
        }
}

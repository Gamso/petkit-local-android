package org.petkitlocal.core.network

import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.network.dto.ActionRequestDto
import org.petkitlocal.core.network.dto.LoginRequestDto
import org.petkitlocal.core.network.error.apiCall

/**
 * Integration: the real OkHttp client and Retrofit service against a scripted server, through
 * the interceptors, the converter and the error mapping.
 */
class PetkitApiIntegrationTest {
    private val server = MockWebServer()
    private var token: String? = "secret-token"
    private lateinit var base: HttpUrl
    private lateinit var client: OkHttpClient
    private lateinit var api: PetkitApi

    @BeforeEach
    fun setUp() {
        server.start()
        // Behind a reverse proxy, below a path prefix: the prefix must survive the rewrite.
        base = server.url("/petkit/app/v1/")
        client = PetkitHttpClient.okHttpClient({ base }, { token })
        api = PetkitHttpClient.api(client)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun json(code: Int, body: String, vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json")
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    @Test
    fun `a call goes to the configured server, below its prefix, with the bearer token`() = runTest {
        json(200, """{"devices":[],"unexpected_new_field":true}""")

        val result = apiCall { api.listDevices() }

        assertEquals(emptyList<Any>(), (result as Outcome.Success).value.devices)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/petkit/app/v1/devices", request.url.encodedPath)
        assertEquals("Bearer secret-token", request.headers["Authorization"])
    }

    @Test
    fun `query parameters are kept and a null one is left out`() = runTest {
        json(200, """{"items":[],"next_cursor":null}""")

        apiCall { api.getTimeline(day = "2026-11-15", kind = "toileting") }

        val url = server.takeRequest(1, TimeUnit.SECONDS)!!.url
        assertEquals("/petkit/app/v1/timeline", url.encodedPath)
        assertEquals("2026-11-15", url.queryParameter("day"))
        assertEquals("toileting", url.queryParameter("kind"))
        assertNull(url.queryParameter("cursor"))
    }

    @Test
    fun `an optional request field left null is not sent`() = runTest {
        json(200, """{"ok":true,"action":"clean","delivered":"mqtt"}""")

        apiCall { api.runDeviceAction(4, "clean", ActionRequestDto()) }

        assertEquals("{}", server.takeRequest(1, TimeUnit.SECONDS)!!.body!!.utf8())
    }

    @Test
    fun `signed out, no Authorization header is sent`() = runTest {
        token = null
        json(200, """{"api_version":1,"server_time":1.0,"authenticated":false,"server_version":null,"session":null}""")

        apiCall { api.getInfo() }

        assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.headers["Authorization"])
    }

    @Test
    fun `the token never goes to another origin`() {
        val other = MockWebServer().apply { start() }
        other.use {
            it.enqueue(MockResponse.Builder().code(200).body("x").build())
            client.newCall(Request.Builder().url(it.url("/elsewhere")).build()).execute().close()
            assertNull(it.takeRequest(1, TimeUnit.SECONDS)!!.headers["Authorization"])
        }
    }

    @Test
    fun `401 invalid_token on a signed-in call ends the session`() = runTest {
        json(401, """{"error":"invalid_token","message":"Sign in again"}""", "WWW-Authenticate" to "Bearer")

        val result = apiCall { api.listPets() }

        assertEquals(Outcome.Failure(DomainError.SessionEnded(SessionEndReason.InvalidToken)), result)
    }

    @Test
    fun `429 on login carries Retry-After`() = runTest {
        token = null
        json(429, """{"error":"too_many_attempts","message":"Wait"}""", "Retry-After" to "8")

        val result = apiCall { api.login(LoginRequestDto("pw")) }

        assertEquals(Outcome.Failure(DomainError.RateLimited(8.seconds)), result)
    }

    @Test
    fun `an unreachable server is a network error`() = runTest {
        server.close()

        val result = apiCall { api.listDevices() }

        assertInstanceOf(DomainError.Network::class.java, (result as Outcome.Failure).error)
    }

    @Test
    fun `an answer that is not petkit-local's JSON is an unexpected response`() = runTest {
        json(200, "<html>Router login</html>")

        val result = apiCall { api.getInfo() }

        assertInstanceOf(DomainError.UnexpectedResponse::class.java, (result as Outcome.Failure).error)
    }

    @Test
    fun `before a server is chosen, a call fails as a network error and reaches no one`() = runTest {
        val unconfigured = PetkitHttpClient.api(PetkitHttpClient.okHttpClient({ null }, { token }))

        val result = apiCall { unconfigured.listDevices() }

        assertInstanceOf(DomainError.Network::class.java, (result as Outcome.Failure).error)
        assertEquals(0, server.requestCount)
    }
}

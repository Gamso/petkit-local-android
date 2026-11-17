package org.petkitlocal.core.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.settings.NotificationSettings
import org.petkitlocal.core.domain.settings.ServerNotificationKind
import org.petkitlocal.core.network.PetkitHttpClient

/** Integration: `/notifications` against a scripted server, through the real client. */
class NotificationSettingsRepositoryImplTest {
    private val server = MockWebServer()
    private lateinit var repository: NotificationSettingsRepositoryImpl

    @BeforeEach
    fun setUp() {
        server.start()
        val base = server.url("/app/v1/")
        val api = PetkitHttpClient.api(PetkitHttpClient.okHttpClient({ base }, { "token" }))
        repository = NotificationSettingsRepositoryImpl(api)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun answer(code: Int, body: String) = server.enqueue(
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build(),
    )

    @Test
    fun `the session's kinds are read as the server holds them`() = runTest {
        answer(200, SETTINGS)

        val settings = (repository.load() as Outcome.Success).value

        assertEquals(
            NotificationSettings(
                chosen = listOf("toilet"),
                serverKinds = listOf(ServerNotificationKind("toilet", true), ServerNotificationKind("offline", false)),
                connected = true,
                lastId = 42,
            ),
            settings,
        )
        assertEquals("/app/v1/notifications", server.takeRequest(1, TimeUnit.SECONDS)!!.url.encodedPath)
    }

    @Test
    fun `a choice is put, and every kind goes as an explicit null`() = runTest {
        answer(200, SETTINGS)
        answer(200, SETTINGS)

        repository.setKinds(listOf("toilet"))
        repository.setKinds(null)

        val first = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("PUT", first.method)
        assertEquals("""{"kinds":["toilet"]}""", first.body!!.utf8())
        assertEquals("""{"kinds":null}""", server.takeRequest(1, TimeUnit.SECONDS)!!.body!!.utf8())
    }

    @Test
    fun `a refusal is the server's error`() = runTest {
        answer(400, """{"error":"bad_parameter","message":"kinds must be a list"}""")

        val result = repository.setKinds(emptyList())

        val error = assertInstanceOf(DomainError.Validation::class.java, (result as Outcome.Failure).error)
        assertEquals("bad_parameter", error.code)
    }

    private companion object {
        const val SETTINGS = """{"kinds":["toilet"],"server_kinds":[{"kind":"toilet","enabled":true},""" +
            """{"kind":"offline","enabled":false}],"connected":true,"last_id":42}"""
    }
}

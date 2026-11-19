package org.petkitlocal.core.network.tls

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.auth.CertificateProblem
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient
import org.petkitlocal.core.network.error.apiCall

/**
 * Integration: the app's client with its [ServerTrust], against an HTTPS server presenting a
 * self-signed certificate (MockWebServer), through the handshake and the error mapping.
 */
class ServerTrustIntegrationTest {
    private val server = MockWebServer()
    private val pins = FakePins()
    private val trust = ServerTrust(pins)
    private lateinit var base: HttpUrl
    private lateinit var client: OkHttpClient
    private lateinit var api: PetkitApi

    private fun serve(certificate: String) {
        server.useHttps(TlsFixtures.serverSocketFactory(certificate))
        server.start()
        base = server.url("/app/v1/")
        client = PetkitHttpClient.okHttpClient({ base }, { null }, trust = trust)
        api = PetkitHttpClient.api(client)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun answerInfo() {
        server.enqueue(
            MockResponse.Builder().code(200).body(INFO).addHeader("Content-Type", "application/json").build(),
        )
    }

    private suspend fun info() = apiCall { api.getInfo() }

    private fun pin(certificate: String) {
        pins.pin = CertificatePin(base.host, base.port, TlsFixtures.certificate(certificate))
        trust.reset()
    }

    private fun Outcome<*>.problem(): CertificateProblem =
        ((this as Outcome.Failure).error as DomainError.Certificate).problem

    @Test
    fun `a self-signed certificate fails as untrusted, with what to check before trusting it`() = runTest {
        serve(TlsFixtures.FIRST)

        val problem = info().problem()

        assertInstanceOf(CertificateProblem.Untrusted::class.java, problem)
        assertEquals(base.host, problem.host)
        assertEquals(Certificates.sha256(TlsFixtures.certificate(TlsFixtures.FIRST)), problem.presented.sha256)
        assertEquals("CN=petkit.home.lan", problem.presented.subject)
        assertEquals(0, server.requestCount) // nothing was sent: no request, no token
    }

    @Test
    fun `once pinned, the same certificate is accepted`() = runTest {
        serve(TlsFixtures.FIRST)
        pin(TlsFixtures.FIRST)
        answerInfo()

        assertInstanceOf(Outcome.Success::class.java, info())
    }

    @Test
    fun `a renewed certificate is refused as a change, with the pinned one's fingerprint`() = runTest {
        serve(TlsFixtures.RENEWED)
        pin(TlsFixtures.FIRST)

        val problem = info().problem() as CertificateProblem.Changed

        assertEquals(Certificates.sha256(TlsFixtures.certificate(TlsFixtures.FIRST)), problem.pinnedSha256)
        assertEquals(Certificates.sha256(TlsFixtures.certificate(TlsFixtures.RENEWED)), problem.presented.sha256)
        assertEquals(listOf(Refusal.Changed), pins.refused.map { it.refusal })
    }

    @Test
    fun `a certificate for another host is refused and never offered`() = runTest {
        serve(TlsFixtures.WRONG_HOST)

        val problem = info().problem()

        assertInstanceOf(CertificateProblem.WrongHost::class.java, problem)
        assertEquals(false, problem.canTrust)
    }

    @Test
    fun `after the pin is dropped and the trust reset, no pooled connection carries on`() = runTest {
        serve(TlsFixtures.FIRST)
        pin(TlsFixtures.FIRST)
        answerInfo()
        assertInstanceOf(Outcome.Success::class.java, info())

        pins.pin = null
        trust.reset()

        assertInstanceOf(CertificateProblem.Untrusted::class.java, info().problem())
    }

    private companion object {
        const val INFO = """{"api_version":1,"server_time":1700000000.5,"authenticated":false}"""
    }
}

package org.petkitlocal.core.network.tls

import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.petkitlocal.core.domain.auth.CertificateProblem

/**
 * The trust decision on its own: the phone's trust first, then the owner's one pin, for one host
 * and port. The host and port come from an SSLEngine set up for them, as a handshake's would.
 */
class PinningTrustManagerTest {
    private val first = TlsFixtures.certificate(TlsFixtures.FIRST)
    private val renewed = TlsFixtures.certificate(TlsFixtures.RENEWED)
    private val wrongHost = TlsFixtures.certificate(TlsFixtures.WRONG_HOST)
    private val expired = TlsFixtures.certificate(TlsFixtures.EXPIRED)
    private val pins = FakePins()

    /** The JDK's own trust (its CA list), which signed none of the fixtures. */
    private var platform: X509TrustManager = plain(ServerTrust.platformTrustManager())

    private fun check(leaf: X509Certificate, host: String = HOST, port: Int = PORT) {
        val engine = SSLContext.getDefault().createSSLEngine(host, port)
        PinningTrustManager(platform, pins).checkServerTrusted(arrayOf(leaf), AUTH_TYPE, engine)
    }

    private fun refusal(leaf: X509Certificate, host: String = HOST, port: Int = PORT): CertificateRefusedException =
        assertThrows<CertificateRefusedException> { check(leaf, host, port) }

    @Test
    fun `a self-signed certificate with no pin is untrusted, and the store is told`() {
        val refused = refusal(first)

        assertEquals(Refusal.Untrusted, refused.refusal)
        assertEquals(HOST, refused.host)
        assertSame(first, refused.presented)
        assertNull(refused.pinned)
        assertEquals(listOf(refused), pins.refused)
    }

    @Test
    fun `the pinned certificate is accepted for its host and port only`() {
        pins.pin = CertificatePin(HOST, PORT, first)

        assertDoesNotThrow { check(first) }
        assertEquals(Refusal.Untrusted, refusal(first, port = 443).refusal)
        assertEquals(Refusal.Untrusted, refusal(first, host = "127.0.0.1").refusal)
    }

    @Test
    fun `another certificate than the pinned one is a change, with both`() {
        pins.pin = CertificatePin(HOST, PORT, first)

        val refused = refusal(renewed)

        assertEquals(Refusal.Changed, refused.refusal)
        assertSame(renewed, refused.presented)
        assertSame(first, refused.pinned)
        val problem = refused.toProblem() as CertificateProblem.Changed
        assertEquals(Certificates.sha256(first), problem.pinnedSha256)
        assertEquals(Certificates.sha256(renewed), problem.presented.sha256)
    }

    @Test
    fun `a certificate that does not name the host is refused, pinned or not`() {
        assertEquals(Refusal.WrongHost, refusal(wrongHost).refusal)

        pins.pin = CertificatePin(HOST, PORT, wrongHost)
        assertEquals(Refusal.WrongHost, refusal(wrongHost).refusal)
    }

    @Test
    fun `an expired certificate is refused, pinned or not`() {
        assertEquals(Refusal.NotValidNow, refusal(expired).refusal)

        pins.pin = CertificatePin(HOST, PORT, expired)
        assertEquals(Refusal.NotValidNow, refusal(expired).refusal)
    }

    @Test
    fun `what the phone trusts is accepted without a pin, if it names the host`() {
        platform = plain(TlsFixtures.trusting(first, wrongHost)) // a home CA installed on the phone

        assertDoesNotThrow { check(first) }
        assertEquals(Refusal.WrongHost, refusal(wrongHost).refusal)
        assertTrue(pins.refused.none { it.refusal == Refusal.Untrusted })
    }

    @Test
    fun `a failure other than an untrusted chain is thrown as it is, never offered`() {
        val revoked = CertificateException("revoked")
        platform = object : X509TrustManager by platform {
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = throw revoked
        }

        val thrown = assertThrows<CertificateException> { check(first) }

        assertSame(revoked, thrown)
        assertEquals(emptyList<CertificateRefusedException>(), pins.refused)
    }

    @Test
    fun `without a host, the phone's trust alone decides`() {
        pins.pin = CertificatePin(HOST, PORT, first)
        val manager = PinningTrustManager(platform, pins)

        assertThrows<CertificateException> { manager.checkServerTrusted(arrayOf(first), AUTH_TYPE) }
    }

    @Test
    fun `the refusal is found among the causes of the failure that wraps it`() {
        val refused = refusal(first)
        val wrapped = javax.net.ssl.SSLHandshakeException("handshake failed").apply {
            initCause(CertificateException("wrapped", refused))
        }

        assertSame(refused, wrapped.certificateRefusal())
        assertNull(RuntimeException("other").certificateRefusal())
    }

    /**
     * A plain X509TrustManager around [manager]: the JDK's own wants a running handshake for its
     * host-aware checks, which these tests do not have.
     */
    private fun plain(manager: X509TrustManager): X509TrustManager = object : X509TrustManager by manager {}

    private companion object {
        const val HOST = "localhost"
        const val PORT = 8443
        const val AUTH_TYPE = "ECDHE_ECDSA"
    }
}

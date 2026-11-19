package org.petkitlocal.core.network.tls

import android.annotation.SuppressLint
import java.net.Socket
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * The phone's own trust (system CAs, and the user's: `network_security_config.xml`), plus at most
 * one certificate the owner pinned for their server (SPEC.md §8). Never in place of the phone's:
 * whatever [platform] accepts is accepted, and the pin is only looked at when it refused.
 *
 * - The certificate must name the host, whoever signed it: a pin never stands in for that, and
 *   a certificate for another name is refused as [Refusal.WrongHost] (OkHttp's own hostname
 *   check still runs after the handshake).
 * - A refused certificate is accepted only if it is, byte for byte, the one pinned for this host
 *   and port, and valid today. Any other is refused, saying why: [Refusal.Changed] when another
 *   is pinned, [Refusal.Untrusted] when none is, [Refusal.NotValidNow] when out of date.
 * - Only a refusal of the chain itself (no path to a trust anchor) can be [Refusal.Untrusted];
 *   any other failure of [platform] (a revoked or malformed certificate) is thrown as it is.
 *
 * The host is the TLS session's peer host, which OkHttp sets to the URL's host. Without one (a
 * caller that gives no socket), the platform alone decides.
 */
// Not the insecure kind lint warns about: the platform's own trust manager decides first, and this
// one only narrows (the host must be named) or adds the one leaf the owner pinned for one host.
@SuppressLint("CustomX509TrustManager")
class PinningTrustManager(
    private val platform: X509TrustManager,
    private val pins: CertificatePinStore,
    private val now: () -> Date = ::Date,
) : X509ExtendedTrustManager() {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        val session = (socket as? SSLSocket)?.handshakeSession
        val port = session?.peerPort?.takeIf { it > 0 } ?: socket?.port ?: -1
        check(chain, session?.peerHost, port) {
            if (platform is X509ExtendedTrustManager) {
                platform.checkServerTrusted(chain, authType, socket)
            } else {
                platform.checkServerTrusted(chain, authType)
            }
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        check(chain, engine?.peerHost, engine?.peerPort ?: -1) {
            if (platform is X509ExtendedTrustManager) {
                platform.checkServerTrusted(chain, authType, engine)
            } else {
                platform.checkServerTrusted(chain, authType)
            }
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        platform.checkServerTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
        platform.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
        platform.checkClientTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        platform.checkClientTrusted(chain, authType)

    /** The phone's own anchors. The pin is not one: it vouches for one leaf, not for what it signs. */
    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

    private fun check(chain: Array<X509Certificate>, host: String?, port: Int, platformCheck: () -> Unit) {
        val leaf = chain.firstOrNull()
        if (host.isNullOrEmpty() || leaf == null) return platformCheck()
        val failure = try {
            platformCheck()
            null
        } catch (e: CertificateException) {
            e
        }
        verdict(leaf, host, port, failure)?.let { throw it }
    }

    /** Null when [leaf] is accepted for [host]:[port]; otherwise what to throw into the handshake. */
    private fun verdict(
        leaf: X509Certificate,
        host: String,
        port: Int,
        failure: CertificateException?,
    ): CertificateException? {
        val pin = failure?.let { pins.pinned()?.takeIf { it.isFor(host, port) } }
        val validNow = Certificates.isValidAt(leaf, now())
        return when {
            !Certificates.namesHost(leaf, host) -> refuse(Refusal.WrongHost, host, leaf, cause = failure)
            failure == null -> null
            pin != null && validNow && pin.certificate.encoded.contentEquals(leaf.encoded) -> null
            !validNow -> refuse(Refusal.NotValidNow, host, leaf, pin?.certificate, failure)
            !failure.isUntrustedPath() -> failure
            pin != null -> refuse(Refusal.Changed, host, leaf, pin.certificate, failure)
            else -> refuse(Refusal.Untrusted, host, leaf, cause = failure)
        }
    }

    private fun refuse(
        refusal: Refusal,
        host: String,
        leaf: X509Certificate,
        pinned: X509Certificate? = null,
        cause: Throwable?,
    ): CertificateRefusedException =
        CertificateRefusedException(refusal, host, leaf, pinned, cause).also(pins::onRefused)

    /**
     * No path to a trust anchor: Android says so with a CertPathValidatorException ("Trust anchor
     * for certification path not found"), the JVM with a CertPathBuilderException.
     */
    private fun Throwable.isUntrustedPath(): Boolean = generateSequence(this) { it.cause }
        .take(MAX_CAUSES)
        .any { it is CertPathValidatorException || it is CertPathBuilderException }

    private companion object {
        const val MAX_CAUSES = 16
    }
}

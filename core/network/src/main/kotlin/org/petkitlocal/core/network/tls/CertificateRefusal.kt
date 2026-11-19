package org.petkitlocal.core.network.tls

import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/**
 * The one certificate the owner trusted by hand, for one server address: [host] and [port] as
 * the app connects to them (`https://petkit.home.lan` is port 443).
 */
class CertificatePin(val host: String, val port: Int, val certificate: X509Certificate) {
    fun isFor(host: String, port: Int): Boolean = this.host.equals(host, ignoreCase = true) && this.port == port
}

/**
 * Where [PinningTrustManager] reads the pin, and what it tells of each certificate it refuses.
 * The data layer implements it on top of the session store. Both are called on the TLS handshake's
 * thread, never the main one, and must answer at once.
 */
interface CertificatePinStore {
    /** The pinned certificate, or null when the owner trusted none. */
    fun pinned(): CertificatePin?

    /** A certificate was refused; [refusal] is thrown into the handshake right after. */
    fun onRefused(refusal: CertificateRefusedException) = Unit
}

/** Why a certificate was refused, from the one the owner may trust to the ones never offered. */
enum class Refusal {
    /** No authority the phone trusts signed it, and none is pinned for this server. */
    Untrusted,

    /** Another certificate is pinned for this server. */
    Changed,

    /** It does not name the host. */
    WrongHost,

    /** Expired, or not valid yet. */
    NotValidNow,
}

/**
 * Thrown by [PinningTrustManager] into the TLS handshake, which fails with it as its cause; the
 * error mapping finds it there ([certificateRefusal]) and tells the user why, with the details
 * to check before trusting [presented].
 *
 * @property pinned the certificate pinned for this server, for [Refusal.Changed].
 */
class CertificateRefusedException(
    val refusal: Refusal,
    val host: String,
    val presented: X509Certificate,
    val pinned: X509Certificate? = null,
    cause: Throwable? = null,
) : CertificateException("$refusal certificate for $host: ${presented.subjectX500Principal.name}", cause)

/**
 * The [CertificateRefusedException] among this failure's causes, if a certificate was refused.
 * Suppressed failures are searched too: OkHttp tries a host's addresses side by side and may
 * throw another one's failure (IPv6 unreachable) with the TLS one suppressed in it.
 */
fun Throwable.certificateRefusal(): CertificateRefusedException? {
    val seen = mutableSetOf<Throwable>()
    val queue = ArrayDeque(listOf(this))
    while (queue.isNotEmpty() && seen.size < MAX_FAILURES) {
        val failure = queue.removeFirst()
        if (!seen.add(failure)) continue
        if (failure is CertificateRefusedException) return failure
        failure.cause?.let(queue::addLast)
        queue.addAll(failure.suppressed)
    }
    return null
}

private const val MAX_FAILURES = 32

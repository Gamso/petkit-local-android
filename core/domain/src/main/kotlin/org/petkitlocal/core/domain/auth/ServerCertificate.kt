package org.petkitlocal.core.domain.auth

import kotlin.time.Instant

/**
 * A server's TLS certificate as the owner is shown it before trusting it (SPEC.md §8): the leaf
 * the server presented, not its chain.
 *
 * @property subject the subject's distinguished name, as the certificate states it.
 * @property sha256 the SHA-256 fingerprint of the whole certificate, as colon-separated pairs of
 *   upper-case hex digits (`AB:01:...`), the form browsers and `openssl x509 -fingerprint` show.
 * @property encoded the certificate itself (DER, Base64): what trusting it pins.
 */
data class ServerCertificate(
    val subject: String,
    val validFrom: Instant,
    val validUntil: Instant,
    val sha256: String,
    val encoded: String,
)

/**
 * Why the app refused a server's certificate. The connection was never made: no request, and no
 * token, reached whoever answered.
 */
sealed interface CertificateProblem {
    /** The host the app was connecting to. */
    val host: String

    /** The certificate that host presented. */
    val presented: ServerCertificate

    /**
     * No authority the phone trusts signed it (a self-signed certificate, a home CA not installed
     * on the phone), and the owner has pinned none for this server. It names the host and is
     * valid today, so the owner may trust it.
     */
    data class Untrusted(override val host: String, override val presented: ServerCertificate) : CertificateProblem

    /**
     * The owner pinned another certificate for this server: a renewal, or someone in the middle.
     * Never accepted silently; the owner may trust the new one after comparing [pinnedSha256].
     */
    data class Changed(
        override val host: String,
        override val presented: ServerCertificate,
        val pinnedSha256: String,
    ) : CertificateProblem

    /** It does not name [host]. Refused, and never offered: trusting it would not help. */
    data class WrongHost(override val host: String, override val presented: ServerCertificate) : CertificateProblem

    /** Expired, or not valid yet (or the phone's clock is wrong). Refused, and never offered. */
    data class NotValidNow(override val host: String, override val presented: ServerCertificate) : CertificateProblem

    /** Whether the owner may trust [presented]: only an untrusted or a changed certificate. */
    val canTrust: Boolean get() = this is Untrusted || this is Changed
}

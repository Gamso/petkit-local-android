package org.petkitlocal.core.network.tls

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import kotlin.time.Instant
import okhttp3.internal.tls.OkHostnameVerifier
import org.petkitlocal.core.domain.auth.CertificateProblem
import org.petkitlocal.core.domain.auth.ServerCertificate

/** Reading, naming and checking certificates, as the trust manager and the screens need them. */
object Certificates {
    /** The certificate in [der], or null when it is not one. */
    fun parse(der: ByteArray): X509Certificate? = try {
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as? X509Certificate
    } catch (_: CertificateException) {
        null
    }

    /** The certificate in a [ServerCertificate.encoded] (Base64 DER), or null when it is not one. */
    fun decode(encoded: String): X509Certificate? = try {
        parse(Base64.getDecoder().decode(encoded))
    } catch (_: IllegalArgumentException) {
        null
    }

    /** `AB:01:...`: SHA-256 of the whole certificate, as browsers and `openssl -fingerprint` show it. */
    fun sha256(certificate: X509Certificate): String = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString(":") { "%02X".format(it.toInt() and BYTE_MASK) }

    /** Whether [certificate] names [host], by OkHttp's rules: its DNS names or IP addresses. */
    fun namesHost(certificate: X509Certificate, host: String): Boolean = OkHostnameVerifier.verify(host, certificate)

    fun isValidAt(certificate: X509Certificate, date: Date): Boolean = try {
        certificate.checkValidity(date)
        true
    } catch (_: CertificateException) { // CertificateExpiredException, CertificateNotYetValidException
        false
    }

    private const val BYTE_MASK = 0xff
}

/** The certificate as the owner is shown it before trusting it. */
fun X509Certificate.toServerCertificate(): ServerCertificate = ServerCertificate(
    subject = subjectX500Principal.name,
    validFrom = Instant.fromEpochMilliseconds(notBefore.time),
    validUntil = Instant.fromEpochMilliseconds(notAfter.time),
    sha256 = Certificates.sha256(this),
    encoded = Base64.getEncoder().encodeToString(encoded),
)

fun CertificateRefusedException.toProblem(): CertificateProblem {
    val shown = presented.toServerCertificate()
    val pinnedSha256 = pinned?.let(Certificates::sha256)
    return when (refusal) {
        Refusal.Untrusted -> CertificateProblem.Untrusted(host, shown)
        Refusal.Changed -> CertificateProblem.Changed(host, shown, pinnedSha256.orEmpty())
        Refusal.WrongHost -> CertificateProblem.WrongHost(host, shown)
        Refusal.NotValidNow -> CertificateProblem.NotValidNow(host, shown)
    }
}

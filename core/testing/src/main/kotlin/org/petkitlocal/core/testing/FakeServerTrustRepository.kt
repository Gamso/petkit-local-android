package org.petkitlocal.core.testing

import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.petkitlocal.core.domain.auth.CertificateProblem
import org.petkitlocal.core.domain.auth.ServerAddress
import org.petkitlocal.core.domain.auth.ServerCertificate
import org.petkitlocal.core.domain.auth.ServerTrustRepository

/**
 * A [ServerTrustRepository] that records what was trusted and answers [trustResult]; a test sets
 * [changedCertificate] to ask about a changed certificate.
 */
class FakeServerTrustRepository : ServerTrustRepository {
    override val changedCertificate = MutableStateFlow<CertificateProblem.Changed?>(null)

    var trustResult = true

    /** Every certificate trusted, with the server it was trusted for (`null` for a change). */
    val trusted = mutableListOf<Pair<ServerAddress?, ServerCertificate>>()
    val dismissed = mutableListOf<CertificateProblem.Changed>()

    override suspend fun trust(server: ServerAddress, certificate: ServerCertificate): Boolean {
        trusted += server to certificate
        return trustResult
    }

    override suspend fun trustChange(change: CertificateProblem.Changed): Boolean {
        trusted += null to change.presented
        if (trustResult) changedCertificate.value = null
        return trustResult
    }

    override fun dismissChange(change: CertificateProblem.Changed) {
        dismissed += change
        changedCertificate.value = null
    }

    companion object {
        /** A self-signed certificate as a server on the LAN would present it. */
        val CERTIFICATE = ServerCertificate(
            subject = "CN=petkit.home.lan",
            validFrom = Instant.parse("2026-01-15T12:00:00Z"),
            validUntil = Instant.parse("2027-01-15T12:00:00Z"),
            sha256 = "3F:9A:12:C4:5E:77:08:B1:D2:6C:4F:90:AE:31:5B:E8:" +
                "07:C9:64:2D:F1:88:3A:5C:B0:19:E4:7D:62:A3:0F:5E",
            encoded = "MIIBfake",
        )

        /** Its renewal. */
        val RENEWED = CERTIFICATE.copy(
            validFrom = Instant.parse("2027-01-10T12:00:00Z"),
            validUntil = Instant.parse("2028-01-10T12:00:00Z"),
            sha256 = "A7:04:6E:D9:31:BC:52:F0:8E:13:C6:79:2A:E5:D4:01:" +
                "9F:68:B3:27:4C:E0:15:8A:73:DB:46:C2:0E:91:F7:3B",
            encoded = "MIIBrenewed",
        )

        const val HOST = "petkit.home.lan"

        val UNTRUSTED = CertificateProblem.Untrusted(HOST, CERTIFICATE)
        val CHANGED = CertificateProblem.Changed(HOST, RENEWED, pinnedSha256 = CERTIFICATE.sha256)
        val WRONG_HOST = CertificateProblem.WrongHost("192.168.1.20", CERTIFICATE)
    }
}

package org.petkitlocal.core.domain.auth

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * The one certificate the owner trusted by hand for their server (SPEC.md §8), on top of the
 * authorities the phone trusts, never in place of them. It is kept with the server's address,
 * forgotten on sign-out and when the app signs in to another server; the address's host must be
 * named by it, as for any certificate.
 */
interface ServerTrustRepository {
    /**
     * A certificate the signed-in server presented in place of the pinned one, which was refused.
     * Null when there is none, or once the owner trusted it or put it off.
     */
    val changedCertificate: StateFlow<CertificateProblem.Changed?>

    /**
     * Pins [certificate] for [server], replacing any certificate pinned before. False, and
     * nothing pinned, when it does not name the server's host or cannot be read.
     */
    suspend fun trust(server: ServerAddress, certificate: ServerCertificate): Boolean

    /** Pins the new certificate of [change], for the server whose pin it replaces. */
    suspend fun trustChange(change: CertificateProblem.Changed): Boolean

    /** "Not now": [change] is not asked about again. The connection stays refused. */
    fun dismissChange(change: CertificateProblem.Changed)
}

/**
 * Trusts the certificate a sign-in was refused for, for the typed [address] (SPEC.md §5.1). The
 * screen then signs in again.
 */
class TrustCertificateUseCase @Inject constructor(private val repository: ServerTrustRepository) {
    suspend operator fun invoke(address: String, certificate: ServerCertificate): Boolean {
        val server = (ServerAddresses.normalise(address) as? AddressCheck.Valid)?.address ?: return false
        return repository.trust(server, certificate)
    }
}

/** The pinned certificate the signed-in server replaced, while the owner has not answered. */
class ObserveCertificateChangeUseCase @Inject constructor(private val repository: ServerTrustRepository) {
    operator fun invoke(): StateFlow<CertificateProblem.Changed?> = repository.changedCertificate
}

/** Answers a [CertificateProblem.Changed]: trust the new certificate, or put it off. */
class AnswerCertificateChangeUseCase @Inject constructor(private val repository: ServerTrustRepository) {
    suspend fun trust(change: CertificateProblem.Changed): Boolean = repository.trustChange(change)

    fun dismiss(change: CertificateProblem.Changed) = repository.dismissChange(change)
}

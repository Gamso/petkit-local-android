package org.petkitlocal.core.data.auth

import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.petkitlocal.core.datastore.SessionStore
import org.petkitlocal.core.datastore.StoredPin
import org.petkitlocal.core.domain.auth.CertificateProblem
import org.petkitlocal.core.domain.auth.ServerAddress
import org.petkitlocal.core.domain.auth.ServerCertificate
import org.petkitlocal.core.domain.auth.ServerScopedCache
import org.petkitlocal.core.domain.auth.ServerTrustRepository
import org.petkitlocal.core.network.tls.CertificatePin
import org.petkitlocal.core.network.tls.CertificatePinStore
import org.petkitlocal.core.network.tls.CertificateRefusedException
import org.petkitlocal.core.network.tls.Certificates
import org.petkitlocal.core.network.tls.Refusal
import org.petkitlocal.core.network.tls.ServerTrust
import org.petkitlocal.core.network.tls.toProblem

/**
 * The certificate the owner pinned (SPEC.md §8), from the encrypted [store] to the TLS handshake.
 *
 * - The handshake asks for it ([CertificatePinStore]) on its own thread and must be answered at
 *   once, so it is mirrored in memory; until it is first read, the handshake waits for it.
 * - It is kept with its server's address and goes with the session: the end of the session,
 *   signed out here or ended by the server, forgets it ([forget], called by the session), and so
 *   does a sign-in to another server ([ServerScopedCache]). It is trusted before the session
 *   starts, so a sign-in that fails (a wrong password) keeps it for the next attempt.
 * - Whenever it changes, [ServerTrust.reset] drops every connection and TLS session made under
 *   the old one, so the next request is checked against the new one.
 * - A pinned certificate refused for another one ([Refusal.Changed]) is kept in
 *   [changedCertificate] for the app to ask the owner about, wherever the request came from.
 */
@Singleton
class CertificateTrust @Inject constructor(
    private val store: SessionStore,
    // A Provider: the trust manager inside it asks this class for the pin.
    private val serverTrust: Provider<ServerTrust>,
    @param:AuthCoroutineScope private val scope: CoroutineScope,
) : ServerTrustRepository,
    CertificatePinStore,
    ServerScopedCache {
    private class Pinned(val serverBaseUrl: String, val pin: CertificatePin)

    @Volatile private var pinned: Pinned? = null

    /** The fingerprint of a changed certificate the owner put off; asked about no more. */
    @Volatile private var dismissed: String? = null

    private val changed = MutableStateFlow<CertificateProblem.Changed?>(null)
    override val changedCertificate: StateFlow<CertificateProblem.Changed?> = changed.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()
    private val mutex = Mutex()

    init {
        scope.launch { load() }
    }

    @Suppress("TooGenericExceptionCaught") // An unreadable pin is no pin; the handshake must not wait forever.
    private suspend fun load() {
        try {
            pinned = store.pin()?.let(::pinnedOf)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            pinned = null
        } finally {
            loaded.complete(Unit)
        }
    }

    // --- what the TLS handshake asks, on its own threads ------------------------------------------

    override fun pinned(): CertificatePin? {
        if (!loaded.isCompleted) runBlocking { loaded.await() }
        return pinned?.pin
    }

    override fun onRefused(refusal: CertificateRefusedException) {
        if (refusal.refusal != Refusal.Changed) return
        val change = refusal.toProblem() as? CertificateProblem.Changed ?: return
        if (change.presented.sha256 != dismissed) changed.value = change
    }

    // --- ServerTrustRepository --------------------------------------------------------------------

    override suspend fun trust(server: ServerAddress, certificate: ServerCertificate): Boolean =
        pin(server.baseUrl, certificate)

    override suspend fun trustChange(change: CertificateProblem.Changed): Boolean {
        loaded.await()
        val server = pinned?.serverBaseUrl ?: return false
        return pin(server, change.presented)
    }

    override fun dismissChange(change: CertificateProblem.Changed) {
        dismissed = change.presented.sha256
        changed.compareAndSet(change, null)
    }

    // --- the session's ----------------------------------------------------------------------------

    override suspend fun keepOnlyFor(serverBaseUrl: String) {
        loaded.await()
        if (pinned?.let { it.serverBaseUrl != serverBaseUrl } == true) forget()
    }

    /** The session ended (signed out here, or by the server): the pin goes with it. */
    suspend fun forget() = mutex.withLock {
        loaded.await()
        store.clearPin()
        replace(null)
    }

    // --- internals --------------------------------------------------------------------------------

    private suspend fun pin(serverBaseUrl: String, certificate: ServerCertificate): Boolean = mutex.withLock {
        loaded.await()
        val x509 = Certificates.decode(certificate.encoded) ?: return@withLock false
        val url = serverBaseUrl.toHttpUrlOrNull() ?: return@withLock false
        // A certificate that does not name the server would be refused anyway: never pin one.
        if (!Certificates.namesHost(x509, url.host)) return@withLock false
        store.savePin(serverBaseUrl, x509.encoded)
        replace(Pinned(serverBaseUrl, CertificatePin(url.host, url.port, x509)))
        true
    }

    private suspend fun replace(value: Pinned?) {
        pinned = value
        dismissed = null
        changed.value = null
        // Closing pooled connections writes to their sockets: never on the main thread.
        withContext(Dispatchers.IO) { serverTrust.get().reset() }
    }

    private fun pinnedOf(stored: StoredPin): Pinned? {
        val url = stored.serverBaseUrl.toHttpUrlOrNull() ?: return null
        val certificate = Certificates.parse(stored.certificate) ?: return null
        return Pinned(stored.serverBaseUrl, CertificatePin(url.host, url.port, certificate))
    }
}

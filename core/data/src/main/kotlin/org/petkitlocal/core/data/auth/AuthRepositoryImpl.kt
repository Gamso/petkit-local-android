package org.petkitlocal.core.data.auth

import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.petkitlocal.core.datastore.SessionStore
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ServerAddress
import org.petkitlocal.core.domain.auth.ServerInfo
import org.petkitlocal.core.domain.auth.ServerScopedCache
import org.petkitlocal.core.domain.auth.Session
import org.petkitlocal.core.domain.auth.SessionScopedCache
import org.petkitlocal.core.domain.map
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.ServerAddressProvider
import org.petkitlocal.core.network.auth.SessionEndListener
import org.petkitlocal.core.network.auth.SessionTokenProvider
import org.petkitlocal.core.network.dto.LoginRequestDto
import org.petkitlocal.core.network.error.apiCall

/**
 * The session, from the encrypted [store] to the HTTP client (ADR 0004).
 *
 * It is also what the shared OkHttp client asks for the server's address and the token
 * ([ServerAddressProvider], [SessionTokenProvider]) and what it tells when the server ends the
 * session ([SessionEndListener]). Those calls come from OkHttp threads and must answer at once,
 * so the stored session is mirrored in memory; until it is first read, they wait for it.
 */
// Four small interfaces of one state: the repository's, and the three the HTTP client asks.
@Suppress("TooManyFunctions")
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val store: SessionStore,
    // A Provider: the API's client needs this class, so it cannot be built before it.
    private val api: Provider<PetkitApi>,
    private val caches: Set<@JvmSuppressWildcards SessionScopedCache>,
    private val serverCaches: Set<@JvmSuppressWildcards ServerScopedCache>,
    @param:AuthCoroutineScope private val scope: CoroutineScope,
) : AuthRepository,
    ServerAddressProvider,
    SessionTokenProvider,
    SessionEndListener {
    /** The signed-in session, as the HTTP client needs it. */
    private class Current(val apiBase: HttpUrl, val token: String)

    private val state = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = state.asStateFlow()

    @Volatile private var current: Current? = null

    /** The server being signed in to, which no token may reach while it is set. */
    @Volatile private var pending: HttpUrl? = null

    private val loaded = CompletableDeferred<Unit>()

    /** Serialises everything that changes the session, so a late 401 cannot undo a sign-in. */
    private val mutex = Mutex()

    init {
        scope.launch { load() }
    }

    @Suppress("TooGenericExceptionCaught") // Whatever broke, the app must leave "Unknown".
    private suspend fun load() {
        try {
            val stored = store.current()
            val server = stored.serverBaseUrl
            val apiBase = server?.let(::apiBaseOf)
            val token = stored.token
            if (server != null && apiBase != null && token != null) {
                current = Current(apiBase, token)
                state.value = AuthState.SignedIn(server)
            } else {
                state.value = AuthState.SignedOut(reasonOf(stored.endedReason))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            state.value = AuthState.SignedOut(reason = null) // an unreadable session is no session
        } finally {
            loaded.complete(Unit)
        }
    }

    // --- what the HTTP client asks, on its own threads -------------------------------------------

    override fun apiBaseUrl(): HttpUrl? = pending ?: awaitLoaded().current?.apiBase

    override fun currentToken(): String? = if (pending != null) null else awaitLoaded().current?.token

    override fun onSessionEnded(reason: SessionEndReason, token: String) {
        scope.launch {
            mutex.withLock { if (current?.token == token) clearLocally(reason) }
        }
    }

    /** OkHttp threads only, never the main thread: blocks until the store was read once. */
    private fun awaitLoaded(): AuthRepositoryImpl {
        if (!loaded.isCompleted) runBlocking { loaded.await() }
        return this
    }

    // --- AuthRepository -------------------------------------------------------------------------

    override suspend fun lastServerBaseUrl(): String? = store.current().serverBaseUrl

    override suspend fun probe(server: ServerAddress): Outcome<ServerInfo> = mutex.withLock {
        onPendingServer(server) { apiCall { api.get().getInfo() }.map { it.toDomain() } }
    }

    override suspend fun signIn(server: ServerAddress, password: String, deviceName: String?): Outcome<Session> =
        mutex.withLock {
            val login = onPendingServer(server) { apiCall { api.get().login(LoginRequestDto(password, deviceName)) } }
            if (login !is Outcome.Success) return@withLock login.map { it.session.toDomain() }
            try {
                store.saveSignIn(server.baseUrl, login.value.token, login.value.session.id)
            } catch (e: GeneralSecurityException) {
                val error = DomainError.Server(status = null, code = TOKEN_STORAGE, message = e.message)
                return@withLock Outcome.Failure(error)
            }
            // Before the app shows anything of this server: what was kept for another one goes.
            serverCaches.forEach { it.keepOnlyFor(server.baseUrl) }
            current = Current(server.apiBaseUrl.toHttpUrl(), login.value.token)
            state.value = AuthState.SignedIn(server.baseUrl)
            Outcome.Success(login.value.session.toDomain())
        }

    override suspend fun signOut() = mutex.withLock {
        // Whatever the server answers, or if it cannot be reached, this phone forgets the session.
        if (current != null) apiCall { api.get().logout() }
        clearLocally(reason = null)
    }

    override suspend fun validateSession() {
        val checked = current ?: return
        val info = apiCall { api.get().getInfo() }
        if (info is Outcome.Success && !info.value.authenticated) {
            mutex.withLock { if (current === checked) clearLocally(SessionEndReason.InvalidToken) }
        }
    }

    override suspend fun endSession(reason: SessionEndReason) = mutex.withLock {
        if (current != null) clearLocally(reason)
    }

    // --- internals ------------------------------------------------------------------------------

    private suspend fun <T> onPendingServer(server: ServerAddress, block: suspend () -> T): T {
        awaitLoadedSuspending()
        pending = server.apiBaseUrl.toHttpUrl()
        return try {
            block()
        } finally {
            pending = null
        }
    }

    private suspend fun awaitLoadedSuspending() = loaded.await()

    /** Forgets the session here, then every cache, and only then says so: no stale data shows. */
    @Suppress("TooGenericExceptionCaught") // A cache that fails to clear must not keep the session.
    private suspend fun clearLocally(reason: SessionEndReason?) {
        current = null
        store.clearSession(endedReason = reason?.name)
        caches.forEach { cache ->
            try {
                cache.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The session is gone either way; the next sign-in's refresh replaces the rows.
            }
        }
        state.value = AuthState.SignedOut(reason)
    }

    private fun apiBaseOf(serverBaseUrl: String): HttpUrl? =
        "$serverBaseUrl${ServerAddress.API_PATH}/".toHttpUrlOrNull()

    private fun reasonOf(name: String?): SessionEndReason? = SessionEndReason.entries.firstOrNull { it.name == name }

    private companion object {
        const val TOKEN_STORAGE = "token_storage"
    }
}

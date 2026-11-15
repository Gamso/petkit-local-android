package org.petkitlocal.core.testing

import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AuthRepository
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ServerAddress
import org.petkitlocal.core.domain.auth.ServerInfo
import org.petkitlocal.core.domain.auth.Session

/**
 * An [AuthRepository] that answers what a test scripts and records what it was asked. A probe,
 * a login and a sign-out suspend in [gate] when it is set, for a test that looks at the screen
 * while the request is in flight.
 */
class FakeAuthRepository(initial: AuthState = AuthState.SignedOut(null)) : AuthRepository {
    override val authState = MutableStateFlow(initial)

    var lastServer: String? = null
    var probeResult: Outcome<ServerInfo> = Outcome.Success(INFO)
    var loginResult: Outcome<Session> = Outcome.Success(SESSION)
    var gate: (suspend () -> Unit)? = null

    val probes = mutableListOf<ServerAddress>()
    val logins = mutableListOf<Login>()
    var signOuts = 0
        private set
    var validations = 0
        private set

    data class Login(val server: ServerAddress, val password: String, val deviceName: String?)

    override suspend fun lastServerBaseUrl(): String? = lastServer

    override suspend fun probe(server: ServerAddress): Outcome<ServerInfo> {
        probes += server
        gate?.invoke()
        return probeResult
    }

    override suspend fun signIn(server: ServerAddress, password: String, deviceName: String?): Outcome<Session> {
        logins += Login(server, password, deviceName)
        gate?.invoke()
        return loginResult.also { if (it is Outcome.Success) authState.value = AuthState.SignedIn(server.baseUrl) }
    }

    override suspend fun signOut() {
        signOuts++
        gate?.invoke()
        authState.value = AuthState.SignedOut(null)
    }

    override suspend fun validateSession() {
        validations++
    }

    override suspend fun endSession(reason: SessionEndReason) {
        authState.value = AuthState.SignedOut(reason)
    }

    fun failProbe(error: DomainError) {
        probeResult = Outcome.Failure(error)
    }

    fun failLogin(error: DomainError) {
        loginResult = Outcome.Failure(error)
    }

    companion object {
        private val EPOCH = Instant.fromEpochSeconds(0)
        val SESSION = Session("abc123", "Pixel 8", EPOCH, EPOCH, EPOCH)
        val INFO =
            ServerInfo(apiVersion = 1, serverTime = EPOCH, authenticated = false, serverVersion = null, session = null)
    }
}

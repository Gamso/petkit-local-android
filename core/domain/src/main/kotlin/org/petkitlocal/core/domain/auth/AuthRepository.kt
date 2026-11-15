package org.petkitlocal.core.domain.auth

import kotlinx.coroutines.flow.StateFlow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason

/**
 * The session with the user's server (ADR 0004). The token itself never leaves the data layer:
 * nothing here returns it.
 */
interface AuthRepository {
    /** Signed in, signed out (and why), or not known yet at start-up. */
    val authState: StateFlow<AuthState>

    /** The server of the last session, to prefill the sign-in form; null on a first start. */
    suspend fun lastServerBaseUrl(): String?

    /** `GET /info` on [server], before any session exists there. */
    suspend fun probe(server: ServerAddress): Outcome<ServerInfo>

    /**
     * `POST /auth/login` on [server]. On success the token is stored encrypted and [authState]
     * becomes [AuthState.SignedIn]; the password is not kept anywhere.
     */
    suspend fun signIn(server: ServerAddress, password: String, deviceName: String?): Outcome<Session>

    /**
     * `POST /auth/logout`, then clears the session and every [SessionScopedCache], whether or not
     * the server could be reached: a phone that cannot reach its server can still sign out.
     */
    suspend fun signOut()

    /**
     * Asks the server whether the stored session still stands (`GET /info` with the token) and
     * ends it if not. Failing to reach the server proves nothing and changes nothing.
     */
    suspend fun validateSession()

    /**
     * Ends the session because the server did: a 401 `invalid_token`, or a WebSocket closed with
     * 4401. The data layer calls it itself for HTTP; a socket client calls it for 4401.
     */
    suspend fun endSession(reason: SessionEndReason)
}

/**
 * Anything kept on the phone for the signed-in session (the Room cache of devices, pets and the
 * timeline). Signing out, or a session ended by the server, clears every implementation bound
 * into the set Hilt provides (`@Binds @IntoSet` in the data layer), so the next account on this
 * phone starts empty.
 */
fun interface SessionScopedCache {
    suspend fun clear()
}

/**
 * Anything kept on the phone across sessions, but for one server only (the user's quick actions
 * per device id: ids are the server's). Signing out, or a session the server ended, keeps it; a
 * sign-in that succeeds calls every implementation bound into the set Hilt provides (`@Binds
 * @IntoSet` in the data layer) with the server's normalised [ServerAddress.baseUrl], and each
 * forgets what it kept when that is another server than the one it was kept for.
 */
fun interface ServerScopedCache {
    suspend fun keepOnlyFor(serverBaseUrl: String)
}

/** This phone's default name for the server's list of signed-in phones: its model. */
fun interface PhoneNameProvider {
    fun defaultPhoneName(): String
}

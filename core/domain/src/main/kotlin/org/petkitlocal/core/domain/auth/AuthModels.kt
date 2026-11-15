package org.petkitlocal.core.domain.auth

import kotlin.time.Instant
import org.petkitlocal.core.domain.SessionEndReason

/** One signed-in phone, as the server knows it. */
data class Session(val id: String, val name: String, val created: Instant, val lastUsed: Instant, val expires: Instant)

/** What `GET /info` says about a server, and about this phone's session when it has one. */
data class ServerInfo(
    val apiVersion: Int,
    val serverTime: Instant,
    val authenticated: Boolean,
    val serverVersion: String?,
    val session: Session?,
) {
    companion object {
        /** The `/app/v1` API version this app speaks. */
        const val SUPPORTED_API_VERSION = 1
    }
}

/** Whether the app has a session, and why it lost the last one. */
sealed interface AuthState {
    /** Still reading the stored session at start-up. */
    data object Unknown : AuthState

    /**
     * No session. [reason] says why the last one ended when the server ended it (revoked, expired,
     * password changed or removed); it is null on a first start or after signing out by hand.
     */
    data class SignedOut(val reason: SessionEndReason?) : AuthState

    data class SignedIn(val serverBaseUrl: String) : AuthState
}

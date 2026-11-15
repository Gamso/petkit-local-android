package org.petkitlocal.core.network.auth

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.network.ServerAddressProvider
import org.petkitlocal.core.network.error.ErrorCodes
import org.petkitlocal.core.network.error.HttpErrorMapper

/**
 * The current session token, or null when signed out. The data layer implements it on top of
 * the Keystore-encrypted session store (ADR 0004); this module never stores a token.
 */
fun interface SessionTokenProvider {
    fun currentToken(): String?
}

/**
 * Told when the server says a session is over (ARCHITECTURE.md §3, "Auth"). [token] is the one
 * the refused request carried, so a late answer to a request of an older session cannot end the
 * current one. Called on an OkHttp thread; the data layer clears the session and the app routes
 * to sign-in. A socket client calls it too, for close code 4401.
 */
fun interface SessionEndListener {
    fun onSessionEnded(reason: SessionEndReason, token: String)
}

/**
 * Adds `Authorization: Bearer <token>` to every request bound for the user's server: REST,
 * media (Coil) and WebSocket upgrades alike. The token travels only in this header, never in a
 * URL (SPEC.md §8), and only to the server: a request to any other origin goes out without it.
 * A request that already carries an `Authorization` header is left as it is.
 *
 * A 401 to a request it signed, whose `Error` says the session is gone (`invalid_token`, or
 * `password_not_set` once the panel removed the password), is reported to [sessionEnded]. Any
 * other 401 is not: `invalid_password` while changing the password leaves the session standing,
 * and a proxy's own 401 page says nothing about petkit-local's session.
 */
class AuthInterceptor(
    private val serverAddress: ServerAddressProvider,
    private val tokenProvider: SessionTokenProvider,
    private val sessionEnded: SessionEndListener = SessionEndListener { _, _ -> },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.currentToken()
        if (token.isNullOrEmpty() || !isForServer(request)) return chain.proceed(request)
        val response = chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
        if (response.code == UNAUTHORIZED) reportEndedSession(response, token)
        return response
    }

    private fun reportEndedSession(response: Response, token: String) {
        // peekBody leaves the body for the caller, which maps the same error for itself.
        val reason = when (HttpErrorMapper.errorCode(response.peekBody(MAX_ERROR_BODY_BYTES).string())) {
            ErrorCodes.INVALID_TOKEN -> SessionEndReason.InvalidToken
            ErrorCodes.PASSWORD_NOT_SET -> SessionEndReason.PasswordRemoved
            else -> null
        }
        if (reason != null) sessionEnded.onSessionEnded(reason, token)
    }

    /** Bound for the user's server, and not already carrying credentials of its own. */
    private fun isForServer(request: Request): Boolean {
        val server = serverAddress.apiBaseUrl() ?: return false
        return request.header(AUTHORIZATION) == null && request.url.isSameOrigin(server)
    }

    private fun HttpUrl.isSameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    companion object {
        const val AUTHORIZATION = "Authorization"
        private const val UNAUTHORIZED = 401
        private const val MAX_ERROR_BODY_BYTES = 16L * 1024
    }
}

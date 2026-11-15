package org.petkitlocal.core.network.auth

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.petkitlocal.core.network.ServerAddressProvider

/**
 * The current session token, or null when signed out. The data layer implements it on top of
 * the Keystore-encrypted session store (M1, ADR 0004); this module never stores a token.
 */
fun interface SessionTokenProvider {
    fun currentToken(): String?
}

/**
 * Adds `Authorization: Bearer <token>` to every request bound for the user's server: REST,
 * media (Coil) and WebSocket upgrades alike. The token travels only in this header, never in a
 * URL (SPEC.md §8), and only to the server: a request to any other origin goes out without it.
 * A request that already carries an `Authorization` header is left as it is.
 */
class AuthInterceptor(
    private val serverAddress: ServerAddressProvider,
    private val tokenProvider: SessionTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.currentToken()
        if (token.isNullOrEmpty() || !isForServer(request)) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
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
    }
}

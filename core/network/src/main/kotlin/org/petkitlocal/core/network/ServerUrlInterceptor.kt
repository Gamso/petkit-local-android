package org.petkitlocal.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Thrown, as the I/O failure it is, when a request is made before a server is chosen. */
class NoServerConfiguredException : IOException("No petkit-local server is configured")

/**
 * Rewrites a request built against [PetkitHttpClient.PLACEHOLDER_BASE_URL] to the current
 * server, keeping its path below the API base and its query. The server may sit below a path
 * prefix of a reverse proxy (`https://example.com/petkit/app/v1/`), so the base's path is kept.
 * Any other request (an absolute media URL from Coil, say) passes through untouched.
 */
class ServerUrlInterceptor(private val serverAddress: ServerAddressProvider) : Interceptor {
    private val placeholder = PetkitHttpClient.PLACEHOLDER_BASE_URL

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != placeholder.host) return chain.proceed(request)

        val base = serverAddress.apiBaseUrl() ?: throw NoServerConfiguredException()
        val relative = url.encodedPathSegments.drop(placeholder.pathSegments.size - 1)
        val rewritten = base.newBuilder()
            .encodedPath(base.encodedPath.trimEnd('/') + "/" + relative.joinToString("/"))
            .encodedQuery(url.encodedQuery)
            .build()
        return chain.proceed(request.newBuilder().url(rewritten).build())
    }
}

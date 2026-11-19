package org.petkitlocal.core.network

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.petkitlocal.core.network.auth.AuthInterceptor
import org.petkitlocal.core.network.auth.SessionEndListener
import org.petkitlocal.core.network.auth.SessionTokenProvider
import org.petkitlocal.core.network.tls.ServerTrust
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Where the server's API is, e.g. `https://pets.example.com/app/v1/`, or null before sign-in.
 * The data layer implements it from the stored server address (M1).
 */
fun interface ServerAddressProvider {
    fun apiBaseUrl(): HttpUrl?
}

/**
 * Builds the one OkHttp client of the app and the Retrofit service on top of it. The same client
 * serves Retrofit, Coil (media and thumbnails) and the WebSockets, so all of them carry the
 * bearer header and none of them can reach a host other than the user's server with it. With a
 * [ServerTrust], they also share its TLS trust: the phone's CAs plus the certificate the owner
 * pinned, if any (SPEC.md §8).
 */
object PetkitHttpClient {
    /**
     * Retrofit needs a base URL when it is built, but the server is chosen at sign-in and can
     * change. Requests are built against this placeholder and [ServerUrlInterceptor] rewrites them
     * to the current server. `.invalid` is reserved (RFC 2606), so a request that escaped the
     * rewrite could never reach anyone.
     */
    val PLACEHOLDER_BASE_URL: HttpUrl = "http://petkit-local.invalid/app/v1/".toHttpUrl()

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L

    fun okHttpClient(
        serverAddress: ServerAddressProvider,
        tokenProvider: SessionTokenProvider,
        sessionEnded: SessionEndListener = SessionEndListener { _, _ -> },
        builder: OkHttpClient.Builder = OkHttpClient.Builder(),
        trust: ServerTrust? = null,
    ): OkHttpClient = builder
        .apply {
            if (trust != null) {
                sslSocketFactory(trust.socketFactory, trust.trustManager)
                connectionPool(trust.connectionPool)
            }
        }
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Order matters: the auth interceptor must see the real server URL to decide.
        .addInterceptor(ServerUrlInterceptor(serverAddress))
        .addInterceptor(AuthInterceptor(serverAddress, tokenProvider, sessionEnded))
        .build()

    /** [validateEagerly] checks every method's annotations now rather than on first call. */
    fun api(client: OkHttpClient, validateEagerly: Boolean = false): PetkitApi = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(PetkitJson.asConverterFactory("application/json".toMediaType()))
        .validateEagerly(validateEagerly)
        .build()
        .create(PetkitApi::class.java)
}

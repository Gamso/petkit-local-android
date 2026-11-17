package org.petkitlocal.core.e2e

import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient
import org.petkitlocal.core.network.dto.LoginRequestDto

/**
 * The mock server (mock-server/README.md) the end-to-end tests run against: its address, its
 * control API under `/mock`, and clients of its `/app/v1` API built the way the app builds its own
 * ([PetkitHttpClient]).
 *
 * The address is the instrumentation argument [URL_ARGUMENT], by default [DEFAULT_URL], the
 * emulator's address for the host where `docker compose -f e2e/docker-compose.yml up` publishes
 * the mock. Point the tests elsewhere with
 * `-Pandroid.testInstrumentationRunnerArguments.petkitMockUrl=http://<host>:<port>`.
 */
object MockServer {
    const val URL_ARGUMENT = "petkitMockUrl"
    const val DEFAULT_URL = "http://10.0.2.2:8097"

    /** The mock's default password (`PETKIT_MOCK_PASSWORD`). */
    const val PASSWORD = "demo"

    /** The `device_name` the tests sign in with; the mock lists it among the sessions. */
    const val DEVICE_NAME = "petkit-local e2e"

    /** The server's root, e.g. `http://10.0.2.2:8097/`. */
    val baseUrl: HttpUrl by lazy {
        val argument = InstrumentationRegistry.getArguments().getString(URL_ARGUMENT)
        (argument?.takeIf { it.isNotBlank() } ?: DEFAULT_URL).toHttpUrl()
    }

    /** The API base the app is given at sign-in: `<server>/app/v1/`. */
    val apiBaseUrl: HttpUrl by lazy { baseUrl.newBuilder().addPathSegments("app/v1/").build() }

    private val controlClient = OkHttpClient.Builder()
        .connectTimeout(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(CONTROL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Back to the seeded household: devices, pets, history and password. Ends every session. */
    fun reset() {
        control("POST", "reset")
    }

    /**
     * Calls the control API: [path] is relative to `/mock` (`devices/1002/visit`, or empty for
     * `GET /mock` itself), [jsonBody] the request's JSON, if any. Returns the response body; throws
     * [IOException] for a non-2xx answer or an unreachable server. See mock-server/README.md,
     * "Control API", for the routes.
     */
    fun control(method: String, path: String, jsonBody: String? = null): String {
        val url = baseUrl.newBuilder().addPathSegment("mock")
            .apply { if (path.isNotEmpty()) addPathSegments(path) }
            .build()
        val body = (jsonBody ?: if (method == "GET" || method == "DELETE") null else "{}")
            ?.toRequestBody(JSON)
        val request = Request.Builder().url(url).method(method, body).build()
        controlClient.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw IOException("$method $url answered ${response.code}: $text")
            return text
        }
    }

    /**
     * The app's API client, pointed at the mock. [token] is read on each request, as the app's
     * session store is, so one client can sign in and then carry the new token.
     */
    fun api(token: () -> String? = { null }): PetkitApi =
        PetkitHttpClient.api(PetkitHttpClient.okHttpClient({ apiBaseUrl }, token))

    /** Signs in with [PASSWORD] and returns a client that carries the new session's token. */
    suspend fun signedInApi(): PetkitApi {
        val token = api().login(LoginRequestDto(password = PASSWORD, deviceName = DEVICE_NAME)).token
        return api { token }
    }

    private val JSON = "application/json".toMediaType()
    private const val CONTROL_TIMEOUT_SECONDS = 15L
}

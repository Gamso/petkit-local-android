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
import org.json.JSONArray
import org.json.JSONObject
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient
import org.petkitlocal.core.network.dto.LoginRequestDto

/**
 * The server the end-to-end tests run against, chosen by instrumentation arguments
 * (`-Pandroid.testInstrumentationRunnerArguments.<name>=<value>`):
 *
 * | Argument | Default | |
 * |---|---|---|
 * | [KIND_ARGUMENT] | `mock` | `mock` (mock-server/) or `real` (petkit-local itself) |
 * | [URL_ARGUMENT] | [DEFAULT_URL] | the server's root as the emulator reaches it; [LEGACY_URL_ARGUMENT] also works |
 * | [PASSWORD_ARGUMENT] | [MOCK_PASSWORD] | the app password |
 * | [PANEL_URL_ARGUMENT] | none | the real server's panel; required with `real` |
 * | [TLS_URL_ARGUMENT] | [DEFAULT_TLS_URL] | the mock's HTTPS port, with its self-signed certificate |
 *
 * By default that is the mock that `docker compose -f e2e/docker-compose.yml up` publishes on the
 * host, which the emulator reaches as 10.0.2.2. `scripts/real-server.sh up` starts the real one
 * and prints the arguments for it.
 *
 * Both kinds can do what a user of the panel does to the app's sessions ([reset], [sessions],
 * [revokeSessions]): the mock through its control API, the real server through its panel
 * (Setup -> Phone app access). Anything else the mock can be made to do (a device going offline,
 * a pet's visit, the clock) is [MockServer]'s, and a test using it is [MockOnly].
 */
object TestServer {
    const val KIND_ARGUMENT = "petkitServerKind"
    const val URL_ARGUMENT = "petkitServerUrl"

    /** The argument's name before the tests could run against the real server. */
    const val LEGACY_URL_ARGUMENT = "petkitMockUrl"
    const val PASSWORD_ARGUMENT = "petkitServerPassword"
    const val PANEL_URL_ARGUMENT = "petkitServerPanelUrl"

    const val DEFAULT_URL = "http://10.0.2.2:8097"

    /** The same mock over HTTPS (`--tls-port`), presenting a self-signed certificate ([MockServer.useCertificate]). */
    const val TLS_URL_ARGUMENT = "petkitServerTlsUrl"
    const val DEFAULT_TLS_URL = "https://10.0.2.2:8443"

    /** The mock's default password (`PETKIT_MOCK_PASSWORD`). */
    const val MOCK_PASSWORD = "demo"

    /** The `device_name` the tests sign in with; the server lists it among the sessions. */
    const val DEVICE_NAME = "petkit-local e2e"

    enum class Kind { MOCK, REAL }

    val kind: Kind by lazy {
        when (val value = argument(KIND_ARGUMENT)?.lowercase()) {
            null, "mock" -> Kind.MOCK
            "real" -> Kind.REAL
            else -> error("$KIND_ARGUMENT is `$value`; expected `mock` or `real`.")
        }
    }

    /** The server's root, e.g. `http://10.0.2.2:8097/`. */
    val baseUrl: HttpUrl by lazy {
        (argument(URL_ARGUMENT) ?: argument(LEGACY_URL_ARGUMENT) ?: DEFAULT_URL).toHttpUrl()
    }

    /** What a user types in the sign-in screen's address field: [baseUrl] without its last slash. */
    val address: String get() = baseUrl.toString().removeSuffix("/")

    /** The address of the mock's HTTPS port, as typed in the sign-in screen. */
    val tlsAddress: String by lazy { (argument(TLS_URL_ARGUMENT) ?: DEFAULT_TLS_URL).removeSuffix("/") }

    /** The API base the app is given at sign-in: `<server>/app/v1/`. */
    val apiBaseUrl: HttpUrl by lazy { baseUrl.newBuilder().addPathSegments("app/v1/").build() }

    val password: String by lazy { argument(PASSWORD_ARGUMENT) ?: MOCK_PASSWORD }

    /** The real server's panel. It has no authentication, so it is published on loopback only. */
    private val panelUrl: HttpUrl by lazy {
        checkNotNull(argument(PANEL_URL_ARGUMENT)) {
            "$PANEL_URL_ARGUMENT is needed with $KIND_ARGUMENT=real (scripts/real-server.sh prints it)."
        }.toHttpUrl()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Back to a known start with no session. The mock goes back to its seeded household and
     * password; the real server has its password set again, which also ends every session (the
     * panel's "set password") and undoes a test's change of password.
     */
    fun reset() {
        when (kind) {
            Kind.MOCK -> MockServer.reset()
            Kind.REAL -> panel("POST", "api/app-access/password", JSONObject().put("password", password).toString())
        }
    }

    /** The live sessions, as the panel lists them: `id`, `name`, the notification `kinds` chosen... */
    fun sessions(): List<JSONObject> {
        val answer = when (kind) {
            Kind.MOCK -> MockServer.control("GET", "sessions")
            Kind.REAL -> panel("GET", "api/app-access")
        }
        return JSONObject(answer).getJSONArray("sessions").objects()
    }

    /** Ends every session, as the panel's "revoke" does: open sockets close with 4401. */
    fun revokeSessions() {
        when (kind) {
            Kind.MOCK -> MockServer.control("DELETE", "sessions")
            Kind.REAL -> panel("DELETE", "api/app-access/sessions")
        }
    }

    /**
     * The app's API client, pointed at the server. [token] is read on each request, as the app's
     * session store is, so one client can sign in and then carry the new token.
     */
    fun api(token: () -> String? = { null }): PetkitApi =
        PetkitHttpClient.api(PetkitHttpClient.okHttpClient({ apiBaseUrl }, token))

    /** Signs in with [password] and returns a client that carries the new session's token. */
    suspend fun signedInApi(): PetkitApi {
        val token = api().login(LoginRequestDto(password = password, deviceName = DEVICE_NAME)).token
        return api { token }
    }

    /** A request to the root of the mock or the panel; throws [IOException] for a non-2xx answer. */
    internal fun call(root: HttpUrl, method: String, path: String, jsonBody: String?): String {
        val url = root.newBuilder().apply { if (path.isNotEmpty()) addPathSegments(path) }.build()
        val body = (jsonBody ?: if (method == "GET" || method == "DELETE") null else "{}")?.toRequestBody(JSON)
        val request = Request.Builder().url(url).method(method, body).build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw IOException("$method $url answered ${response.code}: $text")
            return text
        }
    }

    private fun panel(method: String, path: String, jsonBody: String? = null): String =
        call(panelUrl, method, path, jsonBody)

    private fun argument(name: String): String? =
        InstrumentationRegistry.getArguments().getString(name)?.takeIf { it.isNotBlank() }

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private val JSON = "application/json".toMediaType()
    private const val TIMEOUT_SECONDS = 15L
}

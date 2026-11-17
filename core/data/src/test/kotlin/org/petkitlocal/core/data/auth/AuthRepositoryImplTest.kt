package org.petkitlocal.core.data.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.petkitlocal.core.datastore.SealedSecret
import org.petkitlocal.core.datastore.SessionStore
import org.petkitlocal.core.datastore.TokenCipher
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.UnauthorizedReason
import org.petkitlocal.core.domain.auth.AddressCheck
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ServerAddress
import org.petkitlocal.core.domain.auth.ServerAddresses
import org.petkitlocal.core.domain.auth.ServerScopedCache
import org.petkitlocal.core.domain.auth.SessionScopedCache
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient
import org.petkitlocal.core.network.closeCodeToDomainError
import org.petkitlocal.core.network.error.apiCall

/**
 * Integration: the session repository with the real DataStore file, the real OkHttp client and
 * Retrofit service, against a scripted server (MockWebServer), HTTP and WebSocket.
 */
class AuthRepositoryImplTest {
    @TempDir
    lateinit var dir: File

    private val server = MockWebServer()
    private val scopes = mutableListOf<CoroutineScope>()
    private val cleared = mutableListOf<String>()
    private val cache = SessionScopedCache { cleared += "devices" }
    private val keptFor = mutableListOf<String>()
    private val serverCache = ServerScopedCache { keptFor += it }

    /** Reversible, and not the identity: enough to prove the token goes through the cipher. */
    private class ReversingCipher : TokenCipher {
        override fun encrypt(plain: ByteArray) = SealedSecret(ByteArray(12), plain.reversedArray())

        override fun decrypt(sealed: SealedSecret): ByteArray = sealed.ciphertext.reversedArray()
    }

    /** One app process: closing it releases the DataStore file, as a process death would. */
    private inner class Harness {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        private var apiRef: PetkitApi? = null
        val repository: AuthRepositoryImpl = AuthRepositoryImpl(
            store = SessionStore(
                PreferenceDataStoreFactory.create(scope = scope) { File(dir, "session.preferences_pb") },
                ReversingCipher(),
            ),
            api = { checkNotNull(apiRef) },
            caches = setOf(cache),
            serverCaches = setOf(serverCache),
            scope = scope,
        )
        val client = PetkitHttpClient.okHttpClient(repository, repository, repository)
        val api: PetkitApi = PetkitHttpClient.api(client).also { apiRef = it }

        suspend fun close() = scope.coroutineContext.job.cancelAndJoin()
    }

    private val address: ServerAddress
        get() = (ServerAddresses.normalise(server.url("/proxy/").toString()) as AddressCheck.Valid).address

    @BeforeEach
    fun setUp() {
        server.start()
    }

    @AfterEach
    fun tearDown() {
        scopes.forEach { it.cancel() }
        server.close()
    }

    private fun json(code: Int, body: String, vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json")
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    private fun take(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS)!!

    private suspend fun AuthRepositoryImpl.awaitState(predicate: (AuthState) -> Boolean): AuthState =
        withTimeout(5.seconds) { authState.first(predicate) }

    private suspend fun Harness.signIn() {
        json(200, LOGIN)
        assertInstanceOf(Outcome.Success::class.java, repository.signIn(address, "demo", "Pixel 8"))
        take()
    }

    @Test
    fun `a first start is signed out, with no server and no reason`() = runBlocking {
        val repository = Harness().repository

        assertEquals(AuthState.SignedOut(null), repository.awaitState { it != AuthState.Unknown })
        assertNull(repository.apiBaseUrl())
        assertNull(repository.currentToken())
    }

    @Test
    fun `the probe reaches the server being signed in to, without a token`() = runBlocking {
        val harness = Harness()
        json(200, INFO)

        val info = harness.repository.probe(address)

        assertEquals(1, (info as Outcome.Success).value.apiVersion)
        val request = take()
        assertEquals("/proxy/app/v1/info", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
        assertNull(harness.repository.apiBaseUrl())
    }

    @Test
    fun `signing in stores the session, and every later call carries its token`() = runBlocking {
        val harness = Harness()
        json(200, LOGIN)

        val session = harness.repository.signIn(address, "demo", "Pixel 8")

        assertEquals("abc123", (session as Outcome.Success).value.id)
        val login = take()
        assertEquals("/proxy/app/v1/auth/login", login.url.encodedPath)
        assertEquals("""{"password":"demo","device_name":"Pixel 8"}""", login.body!!.utf8())
        assertEquals(AuthState.SignedIn(address.baseUrl), harness.repository.authState.value)

        json(200, DEVICES)
        apiCall { harness.api.listDevices() }
        assertEquals("Bearer tok-1", take().headers["Authorization"])
    }

    @Test
    fun `a restarted app is still signed in, from the encrypted store`() = runBlocking {
        Harness().apply { signIn() }.close()

        val restarted = Harness().repository

        assertEquals(AuthState.SignedIn(address.baseUrl), restarted.awaitState { it != AuthState.Unknown })
        assertEquals("tok-1", restarted.currentToken())
        assertEquals(address.apiBaseUrl, restarted.apiBaseUrl().toString())
    }

    @Test
    fun `a refused login keeps the app signed out`() = runBlocking {
        val harness = Harness()
        json(401, """{"error":"invalid_password","message":"wrong password"}""", "Retry-After" to "2")

        val result = harness.repository.signIn(address, "nope", null)

        assertInstanceOf(DomainError.Unauthorized::class.java, (result as Outcome.Failure).error)
        assertEquals(AuthState.SignedOut(null), harness.repository.authState.value)
        assertNull(harness.repository.currentToken())
        assertEquals(emptyList<String>(), keptFor)
    }

    @Test
    fun `a sign-in names its server to what is kept per server, and signing out keeps it`() = runBlocking {
        val harness = Harness()

        harness.signIn()
        assertEquals(listOf(address.baseUrl), keptFor)

        json(200, """{"ok":true}""")
        harness.repository.signOut()
        take()
        // The session caches are cleared; what is kept per server is not touched again.
        assertEquals(listOf("devices"), cleared)
        assertEquals(listOf(address.baseUrl), keptFor)
    }

    @Test
    fun `signing out tells the server, then forgets the session and clears the caches`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        json(200, """{"ok":true}""")

        harness.repository.signOut()

        val logout = take()
        assertEquals("/proxy/app/v1/auth/logout", logout.url.encodedPath)
        assertEquals("Bearer tok-1", logout.headers["Authorization"])
        assertEquals(AuthState.SignedOut(null), harness.repository.authState.value)
        assertEquals(listOf("devices"), cleared)
        assertNull(harness.repository.currentToken())
        assertEquals(address.baseUrl, harness.repository.lastServerBaseUrl())
    }

    @Test
    fun `signing out works when the server cannot be reached`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        server.close()

        harness.repository.signOut()

        assertEquals(AuthState.SignedOut(null), harness.repository.authState.value)
        assertEquals(listOf("devices"), cleared)
    }

    @Test
    fun `a 401 invalid_token ends the session, and the reason survives a restart`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        json(401, """{"error":"invalid_token","message":"sign in again"}""", "WWW-Authenticate" to "Bearer")

        apiCall { harness.api.listDevices() }

        assertEquals(
            AuthState.SignedOut(SessionEndReason.InvalidToken),
            harness.repository.awaitState { it is AuthState.SignedOut },
        )
        assertEquals(listOf("devices"), cleared)
        assertNull(harness.repository.currentToken())
        harness.close()
        assertEquals(
            AuthState.SignedOut(SessionEndReason.InvalidToken),
            Harness().repository.awaitState { it != AuthState.Unknown },
        )
    }

    @Test
    fun `the password removed in the panel ends the session with that reason`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        json(401, """{"error":"password_not_set","message":"no password"}""")

        apiCall { harness.api.listDevices() }

        assertEquals(
            AuthState.SignedOut(SessionEndReason.PasswordRemoved),
            harness.repository.awaitState { it is AuthState.SignedOut },
        )
    }

    @Test
    fun `a late 401 for an older token does not end the current session`() = runBlocking {
        val harness = Harness()
        harness.signIn()

        harness.repository.onSessionEnded(SessionEndReason.InvalidToken, token = "tok-0")
        delay(300) // the report is handled on the repository's own scope; give it the time to run

        assertEquals(AuthState.SignedIn(address.baseUrl), harness.repository.authState.value)
        assertEquals("tok-1", harness.repository.currentToken())
        assertEquals(emptyList<String>(), cleared)
    }

    @Test
    fun `a validated session that the server no longer knows ends`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        json(200, INFO)

        harness.repository.validateSession()

        assertEquals("Bearer tok-1", take().headers["Authorization"])
        assertEquals(AuthState.SignedOut(SessionEndReason.InvalidToken), harness.repository.authState.value)
    }

    @Test
    fun `server info carries the token and names this phone's session`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        json(200, SIGNED_IN_INFO)

        val info = (harness.repository.serverInfo() as Outcome.Success).value

        assertEquals("Bearer tok-1", take().headers["Authorization"])
        assertEquals("1.4.0", info.serverVersion)
        assertEquals("Pixel 8", info.session?.name)
        assertEquals(AuthState.SignedIn(address.baseUrl), harness.repository.authState.value)
    }

    @Test
    fun `server info signed out asks nothing and fails as an ended session`() = runBlocking {
        val harness = Harness()
        harness.repository.awaitState { it is AuthState.SignedOut }

        val info = harness.repository.serverInfo()

        assertEquals(Outcome.Failure(DomainError.SessionEnded(SessionEndReason.InvalidToken)), info)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a password change sends both passwords and keeps this session`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        json(200, """{"ok":true,"revoked_sessions":2}""")

        val result = harness.repository.changePassword("demo", "a new password")

        assertEquals(Outcome.Success(2), result)
        val request = take()
        assertEquals("/proxy/app/v1/auth/password", request.url.encodedPath)
        assertEquals("""{"current_password":"demo","new_password":"a new password"}""", request.body!!.utf8())
        assertEquals(AuthState.SignedIn(address.baseUrl), harness.repository.authState.value)
    }

    @Test
    fun `a wrong current password is said, with the lockout, and the session stands`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        json(401, """{"error":"invalid_password","message":"wrong password"}""", "Retry-After" to "2")

        val result = harness.repository.changePassword("nope", "a new password")

        assertEquals(Outcome.Failure(DomainError.Unauthorized(UnauthorizedReason.InvalidPassword, 2.seconds)), result)
        assertEquals(AuthState.SignedIn(address.baseUrl), harness.repository.authState.value)
    }

    @Test
    fun `validation that cannot reach the server changes nothing`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        server.close()

        harness.repository.validateSession()

        assertEquals(AuthState.SignedIn(address.baseUrl), harness.repository.authState.value)
    }

    @Test
    fun `a WebSocket closed with 4401 ends the session through the same path`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.close(4401, "session ended")
                    }
                },
            ).build(),
        )
        val request = Request.Builder().url(PetkitHttpClient.PLACEHOLDER_BASE_URL.resolve("ws")!!).build()

        // What the /ws client (M2) does with a close code: map it, and hand a session end over.
        harness.client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    val error = closeCodeToDomainError(code)
                    if (error is DomainError.SessionEnded) runBlocking { harness.repository.endSession(error.reason) }
                    webSocket.close(code, null)
                }
            },
        )

        assertEquals(
            AuthState.SignedOut(SessionEndReason.ClosedByServer),
            harness.repository.awaitState { it is AuthState.SignedOut },
        )
        assertEquals("Bearer tok-1", take().headers["Authorization"])
    }

    @Test
    fun `while signed in, a probe of another server never carries the token`() = runBlocking {
        val harness = Harness()
        harness.signIn()
        MockWebServer().use { other ->
            other.start()
            other.enqueue(MockResponse.Builder().code(200).body(INFO).build())
            val elsewhere = (ServerAddresses.normalise(other.url("/").toString()) as AddressCheck.Valid).address

            harness.repository.probe(elsewhere)

            assertNull(other.takeRequest(5, TimeUnit.SECONDS)!!.headers["Authorization"])
        }
        assertEquals("tok-1", harness.repository.currentToken())
    }

    private companion object {
        const val SESSION =
            """{"id":"abc123","name":"Pixel 8","created":1.0,"last_used":1.0,"expires":7776001.0,"kinds":null,"push":null}"""
        const val LOGIN = """{"token":"tok-1","token_type":"Bearer","idle_timeout_sec":7776000,"session":$SESSION}"""
        const val INFO =
            """{"api_version":1,"server_time":5.0,"authenticated":false,"server_version":null,"session":null}"""
        const val SIGNED_IN_INFO =
            """{"api_version":1,"server_time":5.0,"authenticated":true,"server_version":"1.4.0","session":$SESSION}"""
        const val DEVICES = """{"devices":[]}"""
    }
}

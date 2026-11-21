package org.petkitlocal.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import org.junit.rules.ExternalResource
import org.petkitlocal.core.database.PetkitDatabase
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient

/**
 * A scripted petkit-local (MockWebServer, answering by path, so concurrent requests work) and a
 * real in-memory Room database: what the repositories run against in these integration tests.
 */
class ScriptedServer : ExternalResource() {
    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, MockResponse>()
    val requests = CopyOnWriteArrayList<String>()

    /** Every request as `METHOD path body` (the body as text, or `<n bytes type>` when it is not JSON). */
    val calls = CopyOnWriteArrayList<String>()

    lateinit var base: HttpUrl
        private set
    lateinit var api: PetkitApi
        private set
    lateinit var db: PetkitDatabase
        private set

    override fun before() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath.removePrefix(base.encodedPath) +
                    (request.url.encodedQuery?.let { "?$it" } ?: "")
                requests += path
                val body = request.body
                val type = request.headers["Content-Type"].orEmpty()
                calls += when {
                    body == null || body.size == 0 -> "${request.method} $path"
                    type.startsWith("application/json") -> "${request.method} $path ${body.utf8()}"
                    else -> "${request.method} $path <${body.size} bytes $type>"
                }
                return routes[path]
                    ?: MockResponse.Builder().code(404).body("""{"error":"not_found","message":"$path"}""").build()
            }
        }
        server.start()
        base = server.url("/petkit/app/v1/")
        api = PetkitHttpClient.api(PetkitHttpClient.okHttpClient({ base }, { "token" }))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PetkitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    override fun after() {
        db.close()
        server.close()
    }

    /** Answers [path] (below the API base, with its query) with [body] and [code]. */
    fun on(path: String, body: String, code: Int = 200) {
        routes[path] = MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
    }

    /** Every request from now on fails as if the server were unreachable. */
    fun goOffline() {
        server.close()
    }
}

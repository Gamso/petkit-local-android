package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.http.mockModule
import io.github.gamso.petkitlocal.mock.scenario.Scenario
import io.github.gamso.petkitlocal.mock.state.World
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import java.util.concurrent.ConcurrentHashMap

/** Every timing at 0 and the scenario off: each test drives the world itself. */
fun testConfig(
    password: String = "demo",
    control: Boolean = true,
) = MockConfig(
    scenarioIntervalSec = 0.0,
    busySec = 0.0,
    visitSec = 0.0,
    videoDelaySec = 0.0,
    password = password,
    controlEnabled = control,
)

/** Every operation a test has called, with the statuses seen. ContractTest checks it covers the spec. */
object Coverage {
    val seen: MutableMap<Pair<String, String>, MutableSet<Int>> = ConcurrentHashMap()

    fun record(
        method: String,
        template: String,
        status: Int,
    ) {
        seen.getOrPut(method to template) { ConcurrentHashMap.newKeySet() } += status
    }
}

class Resp(
    val status: Int,
    val headers: Headers,
    val bytes: ByteArray,
) {
    val text: String get() = bytes.decodeToString()
    val json: JsonObject get() = Json.parseToJsonElement(text).jsonObject
    val error: String get() = json["error"]!!.jsonPrimitive.content

    operator fun get(key: String): JsonElement = json[key] ?: fail("no $key in $text")
}

/** A client for `/app/v1` that checks every response against the contract as it arrives. */
class Api(
    val client: HttpClient,
) {
    var token: String? = null

    suspend fun call(
        method: HttpMethod,
        template: String,
        path: String = template,
        body: String? = null,
        token: String? = this.token,
        contentType: ContentType? = ContentType.Application.Json,
        headers: Map<String, String> = emptyMap(),
    ): Resp {
        val response: HttpResponse =
            client.request("/app/v1$path") {
                this.method = method
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                headers.forEach { (k, v) -> header(k, v) }
                if (body != null) {
                    contentType?.let { contentType(it) }
                    setBody(body)
                }
            }
        val resp = Resp(response.status.value, response.headers, response.readRawBytes())
        Coverage.record(method.value, template, resp.status)
        val problems =
            Contract.check(
                method.value,
                template,
                resp.status,
                response.headers[HttpHeaders.ContentType],
                response.headers::get,
                resp.text,
            )
        if (problems.isNotEmpty()) fail<Unit>("contract violations:\n" + problems.joinToString("\n") + "\nbody: " + resp.text.take(500))
        return resp
    }

    suspend fun get(
        template: String,
        path: String = template,
        token: String? = this.token,
    ) = call(HttpMethod.Get, template, path, token = token)

    suspend fun post(
        template: String,
        path: String = template,
        body: String? = null,
        token: String? = this.token,
    ) = call(HttpMethod.Post, template, path, body, token)

    suspend fun put(
        template: String,
        path: String = template,
        body: String,
        token: String? = this.token,
    ) = call(HttpMethod.Put, template, path, body, token)

    suspend fun delete(
        template: String,
        path: String = template,
        token: String? = this.token,
    ) = call(HttpMethod.Delete, template, path, token = token)

    /** Signs in and keeps the token for the following calls. */
    suspend fun login(
        password: String = "demo",
        name: String = "test phone",
    ): String {
        val r = post("/auth/login", body = """{"password":"$password","device_name":"$name"}""", token = null)
        assertEquals(200, r.status, r.text)
        return r["token"].jsonPrimitive.content.also { token = it }
    }
}

/** Calls the control API (no auth, not part of the contract). */
suspend fun HttpClient.control(
    method: HttpMethod,
    path: String,
    body: String? = null,
): Resp {
    val r =
        request("/mock$path") {
            this.method = method
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    return Resp(r.status.value, r.headers, r.readRawBytes())
}

/** A fresh mock server per test, with the world handed to the test for inspection. */
fun mockTest(
    config: MockConfig = testConfig(),
    block: suspend ApplicationTestBuilder.(world: World, api: Api) -> Unit,
) = testApplication {
    val world = World(config)
    val scenario = Scenario(world, config.scenarioIntervalSec)
    application { mockModule(world, scenario) }
    val client = createClient { install(WebSockets) }
    try {
        block(world, Api(client))
    } finally {
        scenario.close()
        world.shutdown()
    }
}

/** The next text frame as JSON, checked against `UpdateMessage`. */
suspend fun ReceiveChannel<Frame>.nextMessage(timeoutMs: Long = 5000): JsonObject =
    withTimeout(timeoutMs) {
        while (true) {
            val frame = receive()
            if (frame is Frame.Text) {
                val text = frame.readText()
                val problems = Contract.checkMessage(text)
                if (problems.isNotEmpty()) fail<Unit>("ws message violates UpdateMessage:\n${problems.joinToString("\n")}\n$text")
                return@withTimeout Json.parseToJsonElement(text).jsonObject
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

/** Reads messages until one matches [predicate]; returns everything read, the match last. */
suspend fun ReceiveChannel<Frame>.until(
    timeoutMs: Long = 5000,
    predicate: (JsonObject) -> Boolean,
): List<JsonObject> {
    val seen = mutableListOf<JsonObject>()
    withTimeout(timeoutMs) {
        while (true) {
            val m = nextMessage(timeoutMs)
            seen += m
            if (predicate(m)) break
        }
    }
    return seen
}

val JsonObject.type: String get() = this["type"]!!.jsonPrimitive.content

fun JsonElement.str(key: String): String? = (jsonObject[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonElement.arr(key: String): JsonArray = jsonObject[key]!!.jsonArray

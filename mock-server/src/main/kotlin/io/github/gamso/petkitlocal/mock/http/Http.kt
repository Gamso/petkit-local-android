package io.github.gamso.petkitlocal.mock.http

import io.github.gamso.petkitlocal.mock.api.ApiErrorBody
import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.api.MockJson
import io.github.gamso.petkitlocal.mock.scenario.Scenario
import io.github.gamso.petkitlocal.mock.state.AppSession
import io.github.gamso.petkitlocal.mock.state.World
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.util.AttributeKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

const val PREFIX = "/app/v1"

/** The session a request authenticated with, set by the access policy. */
val SessionKey = AttributeKey<AppSession>("app-session")

private val log = LoggerFactory.getLogger("io.github.gamso.petkitlocal.mock.http")

/**
 * Every operation of `api/openapi.yaml`, as `(method, path template)`. The access policy, the
 * forced failures of the control API and the 405 answer all read it.
 */
val OPERATIONS: List<Pair<HttpMethod, String>> =
    listOf(
        HttpMethod.Get to "/info",
        HttpMethod.Get to "/openapi.yaml",
        HttpMethod.Post to "/auth/login",
        HttpMethod.Post to "/auth/logout",
        HttpMethod.Post to "/auth/password",
        HttpMethod.Get to "/devices",
        HttpMethod.Get to "/devices/{id}",
        HttpMethod.Post to "/devices/{id}/actions/{action}",
        HttpMethod.Put to "/devices/{id}/entities/{key}",
        HttpMethod.Get to "/devices/{id}/schedule",
        HttpMethod.Put to "/devices/{id}/schedule",
        HttpMethod.Get to "/devices/{id}/deferred-feeds",
        HttpMethod.Post to "/devices/{id}/deferred-feeds",
        HttpMethod.Delete to "/devices/{id}/deferred-feeds/{feed_id}",
        HttpMethod.Get to "/devices/{id}/live",
        HttpMethod.Get to "/devices/{id}/live/hls/{file}",
        HttpMethod.Get to "/devices/{id}/talk",
        HttpMethod.Get to "/timeline",
        HttpMethod.Get to "/timeline/{event_id}",
        HttpMethod.Get to "/media/thumb/{path}",
        HttpMethod.Get to "/media/{path}",
        HttpMethod.Get to "/pets",
        HttpMethod.Post to "/pets",
        HttpMethod.Get to "/pets/{id}",
        HttpMethod.Patch to "/pets/{id}",
        HttpMethod.Delete to "/pets/{id}",
        HttpMethod.Get to "/pets/{id}/photo",
        HttpMethod.Get to "/settings/pets-source",
        HttpMethod.Patch to "/settings/pets-source",
        HttpMethod.Get to "/pets/{id}/faces",
        HttpMethod.Post to "/pets/{id}/faces",
        HttpMethod.Delete to "/pets/{id}/faces/{face_id}",
        HttpMethod.Get to "/pets/{id}/faces/{face_id}/photo",
        HttpMethod.Get to "/ws",
        HttpMethod.Get to "/notifications",
        HttpMethod.Put to "/notifications",
        HttpMethod.Get to "/notifications/ws",
    )

/** Served without a session (still not before a password exists). */
private val PUBLIC = setOf(HttpMethod.Get to "/info", HttpMethod.Get to "/openapi.yaml")

/** Authenticates by password itself. */
private val CREDENTIALS = setOf(HttpMethod.Post to "/auth/login")

private fun templateRegex(template: String): Regex {
    val pattern =
        template.split("/").joinToString("/") { seg ->
            when {
                seg == "{path}" -> ".+"
                seg.startsWith("{") -> "[^/]+"
                else -> Regex.escape(seg)
            }
        }
    return Regex("^$pattern$")
}

private val OPERATION_REGEX = OPERATIONS.map { (m, t) -> Triple(m, t, templateRegex(t)) }

/** The spec template [path] (relative to `/app/v1`) matches, for [method]; null if none. */
fun templateOf(
    method: HttpMethod,
    path: String,
): String? =
    // `/media/thumb/{path}` is listed before `/media/{path}`, so it wins for a thumbnail path.
    OPERATION_REGEX.firstOrNull { (m, _, r) -> m == method && r.matches(path) }?.second

fun methodsAllowed(path: String): List<HttpMethod> = OPERATION_REGEX.filter { (_, _, r) -> r.matches(path) }.map { it.first }.distinct()

// --- responses ------------------------------------------------------------------------------------

suspend fun <T> ApplicationCall.respondJson(
    serializer: KSerializer<T>,
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respond(status, TextContent(MockJson.encodeToString(serializer, value), ContentType.Application.Json, status))
}

suspend fun ApplicationCall.respondError(e: ApiException) {
    e.headers.forEach { (k, v) -> response.header(k, v) }
    respondJson(ApiErrorBody.serializer(), ApiErrorBody(e.code, e.message), HttpStatusCode.fromValue(e.status))
}

fun unauthorized(
    code: String,
    message: String,
) = ApiException(401, code, message, mapOf(HttpHeaders.WWWAuthenticate to "Bearer realm=\"petkit-local\""))

fun passwordNotSet() =
    unauthorized(
        "password_not_set",
        "The app API is closed until a password is set in the petkit-local panel (Setup -> Phone app access).",
    )

fun invalidToken() =
    unauthorized(
        "invalid_token",
        "Send Authorization: Bearer <token> from POST /app/v1/auth/login. " +
            "Tokens end on logout, revocation, a password change, or after 90 days unused.",
    )

fun ApplicationCall.bearerToken(): String? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    val scheme = header.substringBefore(' ')
    if (!scheme.equals("bearer", ignoreCase = true)) return null
    return header.substringAfter(' ', "").trim().ifEmpty { null }
}

/** The request body as a JSON object: 415 when not declared as JSON, 400 when not an object. */
suspend fun ApplicationCall.jsonBody(): JsonObject {
    if (!request.contentType().match(ContentType.Application.Json)) {
        throw ApiException(415, "unsupported_media_type", "send JSON with Content-Type: application/json")
    }
    val element =
        runCatching { Json.parseToJsonElement(receiveText()) }.getOrNull()
            ?: throw ApiException(400, "bad_json", "the body is not valid JSON")
    return element as? JsonObject ?: throw ApiException(400, "bad_json", "the body must be a JSON object")
}

/** [jsonBody] for a route whose body may be left out entirely: `{}` then. */
suspend fun ApplicationCall.optionalJsonBody(): JsonObject {
    val length = request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    val chunked = request.headers[HttpHeaders.TransferEncoding] != null
    if ((length == null || length == 0L) && !chunked) return JsonObject(emptyMap())
    return jsonBody()
}

// --- the application ------------------------------------------------------------------------------

/**
 * The mock's Ktor module: `/app/v1` with petkit-local's access policy, `/mock` (the control
 * API) unless disabled, and `/ws`.
 *
 * The policy is one interceptor, in the order of petkit-local's middleware:
 * a forced failure (control API) first, then no password → 401 `password_not_set` for EVERY
 * `/app/v1` request (unknown paths too), then the public and credential routes, then a valid
 * bearer session for everything else — unknown paths included, so an unauthenticated caller
 * cannot map the API.
 */
fun Application.mockModule(
    world: World,
    scenario: Scenario,
) {
    install(CallLogging) {
        level = Level.INFO
        filter { it.request.path().startsWith("/") }
    }
    install(WebSockets) {
        pingPeriod = world.config.heartbeatSec.seconds
        timeout = (world.config.heartbeatSec * 2).seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respondError(e) }
        exception<Throwable> { call, e ->
            log.error("{} {} failed", call.request.httpMethod.value, call.request.path(), e)
            call.respondError(ApiException(500, "internal_error", "internal error"))
        }
    }

    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.path()
        if (path != PREFIX && !path.startsWith("$PREFIX/")) return@intercept
        val rel = path.removePrefix(PREFIX)
        val method = call.request.httpMethod
        try {
            world.takeFailure(method, rel)?.let { throw it }
            if (!synchronized(world.lock) { world.auth.passwordSet }) throw passwordNotSet()
            val op = method to (templateOf(method, rel) ?: rel)
            if (op in PUBLIC || op in CREDENTIALS) return@intercept
            val session = synchronized(world.lock) { world.auth.authenticate(call.bearerToken()) } ?: throw invalidToken()
            call.attributes.put(SessionKey, session)
        } catch (e: ApiException) {
            call.respondError(e)
            finish()
        }
    }

    // An open socket must close within a second or so of its session ending (expiry included).
    launch {
        while (isActive) {
            delay(1000)
            world.checkSessions()
        }
    }
    monitor.subscribe(ApplicationStopPreparing) {
        log.info("stopping: closing live-update and live-video sockets with 1001")
        world.shutdown()
        scenario.close()
    }

    routing {
        apiRoutes(world)
        if (world.config.controlEnabled) controlRoutes(world, scenario)
        fallbackRoutes()
    }
}

/** Ends the sockets of every session that is no longer live (expired, revoked, password gone). */
fun World.checkSessions() =
    synchronized(lock) {
        auth.pruneExpired()
        hub.subscribers().filter { !auth.isLive(it.session) }.forEach {
            it.end(io.github.gamso.petkitlocal.mock.state.Subscriber.CLOSE_SESSION_ENDED, "session ended")
        }
        live.endSessions { !auth.isLive(it) }
    }

/** The next forced failure for this request, if the control API set one. */
fun World.takeFailure(
    method: HttpMethod,
    rel: String,
): ApiException? =
    synchronized(lock) {
        val template = templateOf(method, rel)
        val match =
            failures.firstOrNull { f ->
                f.method.equals(method.value, ignoreCase = true) && (f.path == rel || f.path == template)
            } ?: return null
        match.remaining -= 1
        if (match.remaining <= 0) failures.remove(match)
        ApiException(
            match.status,
            match.error,
            match.message,
            buildMap {
                match.retryAfter?.let { put(HttpHeaders.RetryAfter, it.toString()) }
                if (match.status == 401) put(HttpHeaders.WWWAuthenticate, "Bearer realm=\"petkit-local\"")
            },
        )
    }

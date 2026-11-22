package io.github.gamso.petkitlocal.mock.http

import io.github.gamso.petkitlocal.mock.api.ActionResult
import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.api.BleSync
import io.github.gamso.petkitlocal.mock.api.ChangePasswordResponse
import io.github.gamso.petkitlocal.mock.api.DeferredFeedDto
import io.github.gamso.petkitlocal.mock.api.DeferredFeedResult
import io.github.gamso.petkitlocal.mock.api.DeviceDetail
import io.github.gamso.petkitlocal.mock.api.DeviceSummary
import io.github.gamso.petkitlocal.mock.api.DrinkUploadResult
import io.github.gamso.petkitlocal.mock.api.EntityWriteResult
import io.github.gamso.petkitlocal.mock.api.EventDetail
import io.github.gamso.petkitlocal.mock.api.FeedingPlanDto
import io.github.gamso.petkitlocal.mock.api.FeedingPlanResult
import io.github.gamso.petkitlocal.mock.api.Info
import io.github.gamso.petkitlocal.mock.api.LoginResponse
import io.github.gamso.petkitlocal.mock.api.NotificationSettings
import io.github.gamso.petkitlocal.mock.api.Ok
import io.github.gamso.petkitlocal.mock.api.TimelinePage
import io.github.gamso.petkitlocal.mock.api.WriteResult
import io.github.gamso.petkitlocal.mock.media.MediaFactory
import io.github.gamso.petkitlocal.mock.state.AppSession
import io.github.gamso.petkitlocal.mock.state.FILTERS
import io.github.gamso.petkitlocal.mock.state.MockDevice
import io.github.gamso.petkitlocal.mock.state.Outgoing
import io.github.gamso.petkitlocal.mock.state.PasswordRejected
import io.github.gamso.petkitlocal.mock.state.Timeline
import io.github.gamso.petkitlocal.mock.state.World
import io.github.gamso.petkitlocal.mock.state.addDeferredFeed
import io.github.gamso.petkitlocal.mock.state.bleSync
import io.github.gamso.petkitlocal.mock.state.deleteDeferredFeed
import io.github.gamso.petkitlocal.mock.state.runAction
import io.github.gamso.petkitlocal.mock.state.setEntity
import io.github.gamso.petkitlocal.mock.state.uploadDrinks
import io.github.gamso.petkitlocal.mock.state.writePlan
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingPipelineCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.ceil
import kotlin.math.max

private val OPENAPI: ByteArray by lazy {
    World::class.java.getResourceAsStream("/openapi.yaml")?.use { it.readBytes() } ?: error("openapi.yaml is missing from the classpath")
}

private const val PONG = """{"type":"pong"}"""

internal fun ApplicationCall.session(): AppSession = attributes[SessionKey]

/** The path's parameters alone: [ApplicationCall.parameters] puts the query's first. */
internal fun ApplicationCall.pathParameters(): Parameters =
    when (this) {
        is RoutingCall -> pathParameters
        is RoutingPipelineCall -> pathParameters
        else -> parameters
    }

/**
 * A numeric path segment; anything else is a 404, since no such thing can exist. Path only:
 * `parameters` also holds the query, where HLS has an `id` of its own.
 */
internal fun ApplicationCall.pathInt(
    name: String,
    what: String,
): Int {
    val raw = pathParameters()[name].orEmpty()
    return raw.trim().toIntOrNull()?.takeIf { it.toString() == raw.trim() } ?: throw ApiException(404, "${what}_not_found", "no such $what")
}

/** An integer query parameter, clamped; absent gives [default]; not an integer is a 400. */
private fun ApplicationCall.queryInt(
    name: String,
    default: Int? = null,
    min: Int? = null,
    max: Int? = null,
): Int? {
    val raw = request.queryParameters[name]
    if (raw.isNullOrEmpty()) return default
    var value = raw.trim().toIntOrNull() ?: throw ApiException(400, "bad_parameter", "$name must be an integer")
    if (min != null) value = max(min, value)
    if (max != null) value = kotlin.math.min(max, value)
    return value
}

internal fun World.deviceOr404(call: ApplicationCall): MockDevice =
    synchronized(lock) { devices[call.pathInt("id", "device")] } ?: throw ApiException(404, "device_not_found", "no such device")

private fun World.feederOr404(call: ApplicationCall): MockDevice {
    val device = deviceOr404(call)
    if (device.plan == null) throw ApiException(404, "not_a_feeder", "this device has no feeding plan")
    return device
}

private fun tooMany(wait: Double): ApiException {
    val seconds = max(1, ceil(wait).toInt())
    return ApiException(
        429,
        "too_many_attempts",
        "too many wrong passwords; try again in $seconds s",
        mapOf(HttpHeaders.RetryAfter to "$seconds"),
    )
}

/** Checks a password under the per-address throttle, or throws the refusal (`auth.py::_check_password`). */
private fun World.checkPassword(
    call: ApplicationCall,
    password: Any?,
) = synchronized(lock) {
    val address = call.request.origin.remoteAddress
    val wait = auth.retryAfter(address)
    if (wait > 0) throw tooMany(wait)
    if (!auth.verifyPassword(password)) {
        val delay = auth.failure(address)
        throw ApiException(401, "invalid_password", "wrong password", mapOf(HttpHeaders.RetryAfter to "${max(1, ceil(delay).toInt())}"))
    }
    auth.success(address)
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun Route.apiRoutes(world: World) {
    route(PREFIX) {
        metaRoutes(world)
        authRoutes(world)
        deviceRoutes(world)
        accessoryRoutes(world)
        feedingRoutes(world)
        liveVideoRoutes(world)
        talkRoutes(world)
        timelineRoutes(world)
        mediaRoutes(world)
        petRoutes(world)
        updatesRoute(world)
        notificationRoutes(world)
        notificationSocketRoute(world)
    }
}

private fun Route.metaRoutes(world: World) {
    get("/info") {
        val session = synchronized(world.lock) { world.auth.authenticate(call.bearerToken()) }
        call.respondJson(
            Info.serializer(),
            Info(
                serverTime = world.clock.now(),
                authenticated = session != null,
                serverVersion = if (session != null) world.config.serverVersion else null,
                session = session?.dto(),
            ),
        )
    }
    get("/openapi.yaml") {
        call.respondBytes(OPENAPI, ContentType("application", "yaml"))
    }
}

private fun Route.authRoutes(world: World) {
    post("/auth/login") {
        val body = call.jsonBody()
        world.checkPassword(call, body.string("password"))
        val (token, session) = synchronized(world.lock) { world.auth.createSession(body.string("device_name")) }
        call.respondJson(
            LoginResponse.serializer(),
            LoginResponse(token, idleTimeoutSec = AppSession.SESSION_IDLE_LIFETIME, session = session.dto()),
        )
    }
    post("/auth/logout") {
        synchronized(world.lock) { world.auth.revoke(call.session().id) }
        call.respondJson(Ok.serializer(), Ok())
    }
    post("/auth/password") {
        val body = call.jsonBody()
        world.checkPassword(call, body.string("current_password"))
        val revoked =
            try {
                val newPassword: Any? = body["new_password"]?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: it }
                synchronized(world.lock) { world.auth.changePassword(newPassword, keep = call.session().id) }
            } catch (e: PasswordRejected) {
                throw ApiException(400, "password_rejected", e.message.orEmpty())
            }
        call.respondJson(ChangePasswordResponse.serializer(), ChangePasswordResponse(revokedSessions = revoked))
    }
}

/**
 * `GET`/`PUT /notifications`: the session's kinds (`handlers/notifications.py`), whether it holds
 * the notification socket (NotifyRoutes.kt) and the newest id logged.
 */
private fun Route.notificationRoutes(world: World) {
    get("/notifications") {
        call.respondJson(NotificationSettings.serializer(), world.notificationSettings(call.session()))
    }
    put("/notifications") {
        val body = call.jsonBody()
        if ("kinds" !in body) throw ApiException(400, "bad_parameter", "kinds is required: a list of kinds, or null")
        val kinds = checkKinds(body["kinds"]!!)
        val session = call.session()
        synchronized(world.lock) { session.kinds = kinds }
        call.respondJson(NotificationSettings.serializer(), world.notificationSettings(session))
    }
}

/**
 * `kinds` as a session chose them (`push.py::check_kinds`): null for every kind, or a list of
 * strings, at most 32, of which the unknown ones are dropped; kept in the server's order.
 */
private fun checkKinds(value: JsonElement): List<String>? {
    if (value is JsonNull) return null
    val list = value as? JsonArray
    val strings = list?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    if (list == null || strings == null || strings.size != list.size) {
        throw ApiException(400, "bad_parameter", "kinds must be a list of strings, or null for every kind")
    }
    if (strings.size > AppSession.MAX_KINDS) {
        throw ApiException(400, "bad_parameter", "kinds may list at most ${AppSession.MAX_KINDS} entries")
    }
    return AppSession.NOTIFICATION_KINDS.filter { it in strings }
}

private fun Route.deviceRoutes(world: World) {
    get("/devices") {
        val list = synchronized(world.lock) { world.devices.values.map { it.summary() } }
        call.respondJson(MapSerializer(String.serializer(), ListSerializer(DeviceSummary.serializer())), mapOf("devices" to list))
    }
    get("/devices/{id}") {
        val device = world.deviceOr404(call)
        val detail = synchronized(world.lock) { device.detail() }
        call.respondJson(MapSerializer(String.serializer(), DeviceDetail.serializer()), mapOf("device" to detail))
    }
    post("/devices/{id}/actions/{action}") {
        val device = world.deviceOr404(call)
        val key = call.parameters["action"].orEmpty()
        val action = device.model.action(key) ?: throw ApiException(404, "action_not_found", "this device offers no such action")
        val body = call.optionalJsonBody()
        if (action.destructive && (body["confirm"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull != true) {
            throw ApiException(422, "confirmation_required", "${action.name} cannot be undone; send {\"confirm\": true}")
        }
        val delivered =
            synchronized(world.lock) {
                world.requireRelay(device)
                world.runAction(device, key)
                world.delivered(device)
            }
        call.respondJson(ActionResult.serializer(), ActionResult(action = key, delivered = delivered))
    }
    put("/devices/{id}/entities/{key}") {
        val device = world.deviceOr404(call)
        val def =
            device.valueEntity(call.parameters["key"].orEmpty())
                ?: throw ApiException(404, "entity_not_found", "this device has no such entity")
        if (!def.settable) throw ApiException(422, "not_settable", "${def.name} is read-only")
        val body = call.jsonBody()
        val value = body["value"] ?: throw ApiException(400, "bad_request", "send {\"value\": ...}")
        val (delivered, entity) = synchronized(world.lock) { world.setEntity(device, def, value) }
        call.respondJson(EntityWriteResult.serializer(), EntityWriteResult(delivered = delivered, entity = entity))
    }
}

/** A CTW3's drink history, synced by the phone over its own Bluetooth (petkit-local#141). */
private fun Route.accessoryRoutes(world: World) {
    fun World.accessoryOr404(call: ApplicationCall): MockDevice {
        val id = call.pathInt("id", "accessory")
        return synchronized(lock) { devices[id]?.takeIf { it.isAccessory } }
            ?: throw ApiException(404, "accessory_not_found", "no Bluetooth accessory has this id")
    }
    get("/accessories/{id}/ble-sync") {
        val device = world.accessoryOr404(call)
        val sync = synchronized(world.lock) { world.bleSync(device) }
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondJson(BleSync.serializer(), sync)
    }
    post("/accessories/{id}/drinks") {
        val device = world.accessoryOr404(call)
        val body = call.jsonBody()
        val result = synchronized(world.lock) { world.uploadDrinks(device, body) }
        call.respondJson(DrinkUploadResult.serializer(), result)
    }
}

private fun Route.feedingRoutes(world: World) {
    get("/devices/{id}/schedule") {
        val device = world.feederOr404(call)
        val plan = synchronized(world.lock) { device.plan!!.dto(device.id) }
        call.respondJson(MapSerializer(String.serializer(), FeedingPlanDto.serializer()), mapOf("schedule" to plan))
    }
    put("/devices/{id}/schedule") {
        val device = world.feederOr404(call)
        val body = call.jsonBody()
        val (delivered, plan) =
            synchronized(world.lock) {
                world.writePlan(device, body) to device.plan!!.dto(device.id)
            }
        call.respondJson(FeedingPlanResult.serializer(), FeedingPlanResult(delivered = delivered, schedule = plan))
    }
    get("/devices/{id}/deferred-feeds") {
        val device = world.feederOr404(call)
        val feeds = synchronized(world.lock) { device.plan!!.let { p -> p.deferred.sortedBy { it.fireAt }.map(p::deferredDto) } }
        call.respondJson(MapSerializer(String.serializer(), ListSerializer(DeferredFeedDto.serializer())), mapOf("deferred_feeds" to feeds))
    }
    post("/devices/{id}/deferred-feeds") {
        val device = world.feederOr404(call)
        val body = call.jsonBody()
        val (delivered, feed) = synchronized(world.lock) { world.addDeferredFeed(device, body) }
        call.respondJson(
            DeferredFeedResult.serializer(),
            DeferredFeedResult(delivered = delivered, feed = device.plan!!.deferredDto(feed)),
            HttpStatusCode.Created,
        )
    }
    delete("/devices/{id}/deferred-feeds/{feed_id}") {
        val device = world.feederOr404(call)
        val delivered = synchronized(world.lock) { world.deleteDeferredFeed(device, call.parameters["feed_id"].orEmpty()) }
        call.respondJson(WriteResult.serializer(), WriteResult(delivered = delivered))
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(java.time.format.ResolverStyle.STRICT)

private fun Route.timelineRoutes(world: World) {
    get("/timeline") {
        val deviceId = call.queryInt("device")
        val petId = call.queryInt("pet")
        val limit = call.queryInt("limit", default = 30, min = 1, max = 100)!!
        val kind = call.request.queryParameters["kind"]?.ifEmpty { null } ?: "all"
        if (kind != "all" &&
            kind !in FILTERS
        ) {
            throw ApiException(400, "bad_parameter", "kind must be one of: all, ${FILTERS.joinToString(", ")}")
        }
        val day =
            call.request.queryParameters["day"]?.ifEmpty { null }?.let {
                try {
                    LocalDate.parse(it, DAY)
                } catch (_: DateTimeParseException) {
                    throw ApiException(400, "bad_parameter", "day must be YYYY-MM-DD")
                }
            }
        val cursor =
            call.request.queryParameters["cursor"]?.ifEmpty { null }?.let {
                Timeline.decodeCursor(it) ?: throw ApiException(400, "bad_cursor", "cursor is not one this server issued")
            }
        val page = synchronized(world.lock) { world.timelinePage(deviceId, day, kind, petId, limit, cursor) }
        call.respondJson(TimelinePage.serializer(), page)
    }
    get("/timeline/{event_id}") {
        val id = call.pathInt("event_id", "event")
        val detail =
            synchronized(world.lock) {
                world.timeline.rows[id]?.let { world.timeline.detail(it, world.pets) }
            } ?: throw ApiException(404, "event_not_found", "no such event")
        call.respondJson(MapSerializer(String.serializer(), EventDetail.serializer()), mapOf("event" to detail))
    }
}

private fun Route.mediaRoutes(world: World) {
    fun resolve(call: ApplicationCall): Pair<String, io.github.gamso.petkitlocal.mock.state.MediaEntry> {
        val rel =
            call.parameters
                .getAll("path")
                .orEmpty()
                .joinToString("/")
        val entry = synchronized(world.lock) { world.media[rel] } ?: throw ApiException(404, "media_not_found", "no such media file")
        return rel to entry
    }
    get("/media/thumb/{path...}") {
        val (_, entry) = resolve(call)
        call.respondBytes(MediaFactory.jpeg(160, 120, entry.seed), ContentType.Image.JPEG)
    }
    get("/media/{path...}") {
        val (_, entry) = resolve(call)
        if (entry.video) {
            call.respondRange(MediaFactory.clip, ContentType.Video.MP4)
        } else {
            call.respondRange(MediaFactory.jpeg(640, 480, entry.seed), ContentType.Image.JPEG)
        }
    }
}

/**
 * A byte array with single-range `Range` support (`bytes=a-b`, `bytes=a-`, `bytes=-n`), which is
 * what lets a player seek. A range that cannot be satisfied is 416, as aiohttp's FileResponse answers.
 */
suspend fun ApplicationCall.respondRange(
    bytes: ByteArray,
    type: ContentType,
) {
    response.header(HttpHeaders.AcceptRanges, "bytes")
    val header = request.headers[HttpHeaders.Range]
    val match = header?.let { Regex("^bytes=(\\d*)-(\\d*)$").matchEntire(it.trim()) }
    if (match == null) {
        respondBytes(bytes, type)
        return
    }
    val (a, b) = match.destructured
    val size = bytes.size.toLong()
    val (start, end) =
        when {
            a.isEmpty() && b.isEmpty() -> -1L to -1L
            a.isEmpty() -> max(0L, size - b.toLong()) to size - 1
            else -> a.toLong() to (if (b.isEmpty()) size - 1 else minOf(b.toLong(), size - 1))
        }
    if (start < 0 || start >= size || end < start) {
        response.header(HttpHeaders.ContentRange, "bytes */$size")
        respondBytes(ByteArray(0), type, HttpStatusCode.RequestedRangeNotSatisfiable)
        return
    }
    response.header(HttpHeaders.ContentRange, "bytes $start-$end/$size")
    respondBytes(bytes.copyOfRange(start.toInt(), end.toInt() + 1), type, HttpStatusCode.PartialContent)
}

/**
 * `GET /ws`: `hello` first, then the changes. The writer drains the subscriber's bounded queue;
 * the reader answers `{"type":"ping"}` and ignores everything else. A plain GET (no upgrade)
 * is the documented 400.
 */
private fun Route.updatesRoute(world: World) {
    webSocket("/ws") {
        val sub = world.attach(call.session())
        val writer =
            launch {
                for (item in sub.channel) {
                    when (item) {
                        is Outgoing.Text -> {
                            sub.taken()
                            send(Frame.Text(item.text))
                        }

                        is Outgoing.Close -> {
                            close(CloseReason(item.code, item.reason))
                            break
                        }
                    }
                }
            }
        try {
            for (frame in incoming) {
                if (frame is Frame.Text && isPing(frame.readText())) sub.push(PONG)
            }
        } finally {
            world.hub.remove(sub)
            writer.cancel()
        }
    }
    get("/ws") {
        throw ApiException(400, "bad_request", "not a WebSocket upgrade request")
    }
}

private fun isPing(text: String): Boolean =
    runCatching { Json.parseToJsonElement(text) == JsonObject(mapOf("type" to JsonPrimitive("ping"))) }.getOrDefault(false)

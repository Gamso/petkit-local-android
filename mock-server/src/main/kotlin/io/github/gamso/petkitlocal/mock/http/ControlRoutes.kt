package io.github.gamso.petkitlocal.mock.http

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.api.PushMessage
import io.github.gamso.petkitlocal.mock.scenario.Scenario
import io.github.gamso.petkitlocal.mock.state.FeedSource
import io.github.gamso.petkitlocal.mock.state.ForcedFailure
import io.github.gamso.petkitlocal.mock.state.LiveViewer
import io.github.gamso.petkitlocal.mock.state.MockDevice
import io.github.gamso.petkitlocal.mock.state.Subscriber
import io.github.gamso.petkitlocal.mock.state.TALK_VALUES
import io.github.gamso.petkitlocal.mock.state.TalkHub
import io.github.gamso.petkitlocal.mock.state.TalkRefusal
import io.github.gamso.petkitlocal.mock.state.Talker
import io.github.gamso.petkitlocal.mock.state.World
import io.github.gamso.petkitlocal.mock.state.asStrictInt
import io.github.gamso.petkitlocal.mock.state.clearError
import io.github.gamso.petkitlocal.mock.state.petsSourceDto
import io.github.gamso.petkitlocal.mock.state.playDrink
import io.github.gamso.petkitlocal.mock.state.playEating
import io.github.gamso.petkitlocal.mock.state.playFeed
import io.github.gamso.petkitlocal.mock.state.playVisit
import io.github.gamso.petkitlocal.mock.state.raiseError
import io.github.gamso.petkitlocal.mock.tls.MockTls
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/*
 * `/mock/...`: the control API the app's end-to-end tests drive. No authentication, and not
 * served at all with `--control=false`. Every route answers JSON; a refusal is the same
 * `{"error", "message"}` shape as the real API's.
 */

private suspend fun ApplicationCall.ok(body: JsonObject = JsonObject(emptyMap())) {
    val text = JsonObject(mapOf("ok" to JsonPrimitive(true)) + body).toString()
    respond(HttpStatusCode.OK, TextContent(text, ContentType.Application.Json))
}

private suspend fun ApplicationCall.optionalBody(): JsonObject = runCatching { optionalJsonBody() }.getOrElse { JsonObject(emptyMap()) }

private fun JsonObject.int(name: String): Int? = this[name].asStrictInt()

private fun JsonObject.double(name: String): Double? = (this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull

private fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

private fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun World.controlDevice(call: ApplicationCall): MockDevice {
    val id = call.parameters["id"]?.toIntOrNull()
    return synchronized(lock) { id?.let { devices[it] } } ?: throw ApiException(404, "device_not_found", "no such device")
}

/** An accessory is online while its parent is: take the parent offline instead. */
private fun MockDevice.notAccessory(): MockDevice =
    if (isAccessory) {
        throw ApiException(400, "bad_request", "an accessory is online while its parent ${parent?.id} is; take that one offline")
    } else {
        this
    }

/** `DeviceSummary.transport`: an MQTT session, or HTTP polling. */
private val TRANSPORTS = setOf("mqtt", "http")

/** A litter box's visit summary (`events/codes.py`), the code a `toilet` notification carries. */
private const val VISIT_CODE = "10"

private fun now(world: World): Long = world.clock.now().toLong()

/** The routes, for `GET /mock` (and the README). */
private val CONTROL_ROUTES =
    listOf(
        "GET    /mock                                 this list, the clock and the scenario state",
        "POST   /mock/reset                           back to the seed (devices, pets, history, password, proxy mode; sessions dropped)",
        "POST   /mock/devices/{id}/visit              {pet_id?, weight_g?, duration_sec?, auto_clean?, health_alert?}",
        "POST   /mock/devices/{id}/meal               {amount?, amount1?, amount2?}",
        "POST   /mock/devices/{id}/drink              {pet_id?, duration_sec?}",
        "POST   /mock/devices/{id}/eat                {pet_id?, duration_sec?}  a pet eating at a camera feeder",
        "POST   /mock/devices/{id}/error              {message?}  (default \"bin full\")",
        "POST   /mock/devices/{id}/error/clear",
        "POST   /mock/devices/{id}/offline | /online",
        "POST   /mock/devices/{id}/remove             sends device_removed (reset brings it back)",
        "PUT    /mock/devices/{id}/state              {\"<value_path>\": value, ...}  raw state, any entity",
        "PUT    /mock/devices/{id}/talk               {talk}  available | needs_patcher | device_unreachable | unsupported",
        "PUT    /mock/devices/{id}/supports-ai        {supports_ai}  true | false  (not an accessory)",
        "PUT    /mock/devices/{id}/transport          {transport}  mqtt | http  (a device, not an accessory; its accessories follow)",
        "PUT    /mock/devices/{id}/ble-secret         {secret: 16 hex | null}  a CTW3's pairing secret; null = none held",
        "POST   /mock/clock/advance                   {seconds}",
        "GET    /mock/sessions",
        "DELETE /mock/sessions/{id}                   revoke one (its sockets close 4401)",
        "DELETE /mock/sessions                        revoke all",
        "PUT    /mock/proxy                           {proxy_mode}  true | false  the panel's proxy mode (pets-source applies)",
        "PUT    /mock/password                        {password: string | null}  null = no password set",
        "PUT    /mock/notifications                   {disabled: [kind, ...]}  the panel's per-kind switches off",
        "POST   /mock/notifications                   {kind, device_id?, pet_id?, code?, ts?}  send one notification",
        "POST   /mock/notifications/close             {code?, reason?}  close every notification socket (1001, 1013, 4401...)",
        "DELETE /mock/notifications/log               lose the catch-up log: ids start again from 1",
        "POST   /mock/fail                            {method, path, status, error?, message?, retry_after?, times?}",
        "DELETE /mock/fail                            clear forced failures",
        "POST   /mock/ws/close                        {code?, reason?}  close every /ws socket (1001, 1013, 4401...)",
        "POST   /mock/live/close                      {code?, reason?}  close every live-video socket (1011; 1001, 1013, 4409...)",
        "POST   /mock/live/error                      {message?, next?}  `error` to every live-video socket and the next `next` opens",
        "POST   /mock/talk/error                      {code, message?, next?}  `error`, close 4000: every talk, the next `next` opens",
        "POST   /mock/talk/close                      {code?, reason?}  close every talk socket (1001; 1000, 1009, 4401...)",
        "POST   /mock/scenario/tick                   roll one scenario event now",
        "PUT    /mock/scenario                        {interval_sec}  0 stops it",
        "GET    /mock/tls                             the HTTPS port's certificate now, and every one's SHA-256",
        "PUT    /mock/tls                             {certificate}  primary | renewed | wrong-host",
    )

fun Route.controlRoutes(
    world: World,
    scenario: Scenario,
) {
    route("/mock") {
        get {
            call.ok(
                buildJsonObject {
                    put("now", world.clock.now())
                    put("clock_offset_sec", world.clock.offsetSeconds)
                    put("scenario_interval_sec", scenario.intervalSec)
                    put("scenario_running", scenario.running)
                    put("sockets", world.hub.size)
                    put("notification_sockets", synchronized(world.lock) { world.notify.size })
                    put("live_sockets", world.live.sockets)
                    put("hls_views", world.live.hlsViews)
                    put("talk_sockets", world.talk.sockets)
                    put("talk_bytes", world.talk.bytes)
                    synchronized(world.lock) {
                        put("proxy_mode", world.proxyMode)
                        put("pets_source", world.petsSource)
                    }
                    put("routes", buildJsonArray { CONTROL_ROUTES.forEach { add(JsonPrimitive(it)) } })
                },
            )
        }
        post("/reset") {
            world.reset()
            call.ok()
        }
        put("/proxy") {
            val proxy = call.jsonBody().bool("proxy_mode") ?: throw ApiException(400, "bad_request", "send {\"proxy_mode\": true | false}")
            val source =
                synchronized(world.lock) {
                    world.proxyMode = proxy
                    world.petsSourceDto()
                }
            call.ok(
                buildJsonObject {
                    put("proxy_mode", source.proxyMode)
                    put("pets_source", source.petsSource)
                    put("effective", source.effective)
                },
            )
        }
        deviceControl(world)
        accessoryControl(world)
        sessionControl(world)
        harnessControl(world, scenario)
        tlsControl(world)
    }
}

/** The HTTPS port's certificate (`--tls-port`): which one it presents, and switching it. */
private fun Route.tlsControl(world: World) {
    fun tls(): MockTls = world.tls ?: throw ApiException(404, "tls_off", "the mock has no HTTPS port; start it with --tls-port")

    fun describe(tls: MockTls) =
        buildJsonObject {
            put("certificate", tls.current.key)
            put("sha256", tls.sha256(tls.current))
            put(
                "certificates",
                buildJsonObject { MockTls.Certificate.entries.forEach { put(it.key, tls.sha256(it)) } },
            )
        }
    get("/tls") {
        call.ok(describe(tls()))
    }
    put("/tls") {
        val tls = tls()
        val key = call.jsonBody().str("certificate")
        val which =
            key?.let(MockTls.Certificate::of)
                ?: throw ApiException(400, "bad_request", "certificate is one of ${MockTls.Certificate.entries.map { it.key }}")
        tls.use(which)
        call.ok(describe(tls))
    }
}

/** An accessory's pairing secret, as petkit-local holds it or not (`BleSync`, petkit-local#141). */
private fun Route.accessoryControl(world: World) {
    route("/devices/{id}") {
        put("/ble-secret") {
            val device = world.controlDevice(call)
            if (!device.isAccessory) throw ApiException(400, "bad_request", "only a Bluetooth accessory has a pairing secret")
            val body = call.jsonBody()
            if ("secret" !in body) throw ApiException(400, "bad_request", "send {\"secret\": \"<16 hex>\" | null}")
            val secret = body.str("secret")
            if (secret == null && body["secret"] !is JsonNull) throw ApiException(400, "bad_request", "secret is a string or null")
            synchronized(world.lock) { device.bleSecret = secret }
            call.ok()
        }
    }
}

/** Triggers on one device: what the scenario engine plays, on demand. */
private fun Route.deviceControl(world: World) {
    route("/devices/{id}") {
        post("/visit") {
            val device = world.controlDevice(call)
            val body = call.optionalBody()
            if (!device.isLitter) throw ApiException(422, "not_a_litter_box", "visits happen on litter boxes")
            val petId = body.int("pet_id")
            if (petId != null && petId !in world.pets) throw ApiException(404, "pet_not_found", "no such pet")
            val started =
                synchronized(world.lock) {
                    world.playVisit(
                        device,
                        petId,
                        body.int("weight_g"),
                        body.int("duration_sec"),
                        body.bool("auto_clean"),
                        body.bool("health_alert") ?: false,
                    )
                }
            world.sendNotification(PushMessage(kind = "toilet", deviceId = device.id, petId = petId, code = VISIT_CODE, ts = now(world)))
            call.ok(buildJsonObject { put("card_id", started) })
        }
        post("/meal") {
            val device = world.controlDevice(call)
            if (!device.isFeeder) throw ApiException(422, "not_a_feeder", "meals happen on feeders")
            val body = call.optionalBody()
            synchronized(world.lock) {
                world.playFeed(device, FeedSource.SCHEDULE, body.int("amount") ?: 1, body.int("amount1") ?: 1, body.int("amount2") ?: 0)
            }
            world.sendNotification(PushMessage(kind = "feed_served", deviceId = device.id, petId = null, code = null, ts = now(world)))
            call.ok()
        }
        post("/drink") {
            val device = world.controlDevice(call)
            if (!device.isFountain) throw ApiException(422, "not_a_fountain", "drinks happen at fountains")
            val body = call.optionalBody()
            val card = synchronized(world.lock) { world.playDrink(device, body.int("pet_id"), body.int("duration_sec")) }
            call.ok(buildJsonObject { put("card_id", card.id) })
        }
        post("/eat") {
            val device = world.controlDevice(call)
            if (!device.isFeeder || !device.model.isCamera) {
                throw ApiException(422, "not_a_camera_feeder", "pets are seen eating at camera feeders")
            }
            val body = call.optionalBody()
            val petId = body.int("pet_id")
            if (petId != null && petId !in world.pets) throw ApiException(404, "pet_not_found", "no such pet")
            val duration = body.int("duration_sec")
            if (duration != null && duration < 0) throw ApiException(400, "bad_request", "duration_sec must be 0 or more")
            val card = synchronized(world.lock) { world.playEating(device, petId, duration) }
            call.ok(buildJsonObject { put("card_id", card.id) })
        }
        post("/error") {
            val device = world.controlDevice(call)
            val message = call.optionalBody().str("message") ?: "bin full"
            val card = synchronized(world.lock) { world.raiseError(device, message) }
            // The device's own text is never sent: the state key that carries it is.
            world.sendNotification(PushMessage(kind = "error", deviceId = device.id, petId = null, code = "errorMsg", ts = now(world)))
            call.ok(buildJsonObject { put("card_id", card.id) })
        }
        post("/error/clear") {
            val device = world.controlDevice(call)
            val card = synchronized(world.lock) { world.clearError(device) }
            call.ok(buildJsonObject { put("card_id", card.id) })
        }
        post("/offline") {
            val device = world.controlDevice(call).notAccessory()
            synchronized(world.lock) { world.setOnline(device, false) }
            world.sendNotification(PushMessage(kind = "offline", deviceId = device.id, petId = null, code = null, ts = now(world)))
            call.ok()
        }
        post("/online") {
            val device = world.controlDevice(call).notAccessory()
            synchronized(world.lock) { world.setOnline(device, true) }
            call.ok()
        }
        post("/remove") {
            val device = world.controlDevice(call)
            synchronized(world.lock) { world.removeDevice(device.id) }
            call.ok()
        }
        put("/talk") {
            val device = world.controlDevice(call)
            val talk =
                call.jsonBody().str("talk")?.takeIf { it in TALK_VALUES }
                    ?: throw ApiException(400, "bad_request", "talk is one of $TALK_VALUES")
            synchronized(world.lock) { world.change(device, reported = false) { device.talk = talk } }
            call.ok()
        }
        put("/supports-ai") {
            val device = world.controlDevice(call)
            if (device.isAccessory) throw ApiException(400, "bad_request", "an accessory never recognises pets")
            val supportsAi =
                call.jsonBody().bool("supports_ai") ?: throw ApiException(400, "bad_request", "send {\"supports_ai\": true | false}")
            synchronized(world.lock) { world.change(device, reported = false) { device.supportsAi = supportsAi } }
            call.ok()
        }
        put("/transport") {
            val device = world.controlDevice(call).notAccessory()
            val transport =
                call.jsonBody().str("transport")?.takeIf { it in TRANSPORTS }
                    ?: throw ApiException(400, "bad_request", "transport is one of $TRANSPORTS")
            // An accessory's summary follows its parent's: `change` sends theirs too.
            synchronized(world.lock) { world.change(device, reported = false) { device.transport = transport } }
            call.ok()
        }
        put("/state") {
            val device = world.controlDevice(call)
            val body = call.jsonBody()
            synchronized(world.lock) {
                world.change(device) { body.forEach { (path, value) -> device.doc[path] = value } }
            }
            call.ok()
        }
    }
}

/** The clock, the sessions and the password: what the panel does, and time passing. */
private fun Route.sessionControl(world: World) {
    post("/clock/advance") {
        val seconds = call.jsonBody().double("seconds") ?: throw ApiException(400, "bad_request", "send {\"seconds\": n}")
        world.clock.advance(seconds)
        world.checkSessions()
        call.ok(buildJsonObject { put("now", world.clock.now()) })
    }
    get("/sessions") {
        val sessions = synchronized(world.lock) { world.auth.sessions() }
        call.ok(
            buildJsonObject {
                put(
                    "sessions",
                    JsonArray(
                        sessions.map {
                            buildJsonObject {
                                put("id", it.id)
                                put("name", it.name)
                                put("created", it.created)
                                put("last_used", it.lastUsed)
                                put("expires", it.expires)
                                put("kinds", it.kinds?.let { k -> JsonArray(k.map(::JsonPrimitive)) } ?: JsonNull)
                            }
                        },
                    ),
                )
            },
        )
    }
    delete("/sessions/{id}") {
        val found = synchronized(world.lock) { world.auth.revoke(call.parameters["id"].orEmpty()) }
        if (!found) throw ApiException(404, "session_not_found", "no such session")
        call.ok()
    }
    delete("/sessions") {
        val revoked = synchronized(world.lock) { world.auth.revokeAll() }
        call.ok(buildJsonObject { put("revoked", revoked) })
    }
    put("/notifications") {
        val list = call.jsonBody()["disabled"] as? JsonArray ?: throw ApiException(400, "bad_request", "send {\"disabled\": [kind, ...]}")
        val disabled =
            list.map {
                (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                    ?: throw ApiException(400, "bad_request", "kinds are strings")
            }
        synchronized(world.lock) {
            world.disabledKinds.clear()
            world.disabledKinds += disabled
        }
        call.ok()
    }
    post("/notifications") {
        val body = call.jsonBody()
        val kind = body.str("kind") ?: throw ApiException(400, "bad_request", "send {\"kind\": \"toilet\", ...}")
        val message =
            PushMessage(
                kind = kind,
                deviceId = body.int("device_id"),
                petId = body.int("pet_id"),
                code = body.str("code"),
                ts = body.double("ts")?.toLong() ?: world.clock.now().toLong(),
            )
        val id = world.sendNotification(message)
        call.ok(buildJsonObject { put("id", id) })
    }
    post("/notifications/close") {
        val body = call.optionalBody()
        val code = (body.int("code") ?: Subscriber.CLOSE_GOING_AWAY.toInt()).toShort()
        val closed = synchronized(world.lock) { world.notify.end(code, body.str("reason") ?: "closed by the mock's control API") }
        call.ok(buildJsonObject { put("closed", closed) })
    }
    delete("/notifications/log") {
        synchronized(world.lock) { world.notify.loseLog() }
        call.ok()
    }
    put("/password") {
        val body = call.jsonBody()
        val value: JsonElement = body["password"] ?: throw ApiException(400, "bad_request", "send {\"password\": string | null}")
        val password = if (value is JsonNull) null else (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (value !is JsonNull && password == null) throw ApiException(400, "bad_request", "password must be a string or null")
        val revoked = synchronized(world.lock) { world.auth.setPasswordFromPanel(password) }
        call.ok(buildJsonObject { put("revoked", revoked) })
    }
}

/** Forced failures, sockets and the scenario engine. */
private fun Route.harnessControl(
    world: World,
    scenario: Scenario,
) {
    post("/fail") {
        val body = call.jsonBody()
        val method = body.str("method")?.uppercase() ?: "GET"
        val path =
            body.str("path")?.removePrefix(PREFIX) ?: throw ApiException(400, "bad_request", "path is required, e.g. /devices/{id}")
        val status = body.int("status") ?: throw ApiException(400, "bad_request", "status is required")
        synchronized(world.lock) {
            world.failures +=
                ForcedFailure(
                    method = method,
                    path = path,
                    status = status,
                    error = body.str("error") ?: defaultCode(status),
                    message = body.str("message") ?: "forced by the mock's control API",
                    retryAfter = body.int("retry_after"),
                    remaining = body.int("times") ?: 1,
                )
        }
        call.ok()
    }
    delete("/fail") {
        synchronized(world.lock) { world.failures.clear() }
        call.ok()
    }
    post("/ws/close") {
        val body = call.optionalBody()
        val code = (body.int("code") ?: Subscriber.CLOSE_GOING_AWAY.toInt()).toShort()
        val closed = world.hub.end(code, body.str("reason") ?: "closed by the mock's control API")
        call.ok(buildJsonObject { put("closed", closed) })
    }
    post("/live/close") {
        val body = call.optionalBody()
        val code = (body.int("code") ?: LiveViewer.CLOSE_STREAM_ENDED.toInt()).toShort()
        val closed = world.live.end(code, body.str("reason") ?: "closed by the mock's control API")
        call.ok(buildJsonObject { put("closed", closed) })
    }
    post("/live/error") {
        val body = call.optionalBody()
        val message = body.str("message") ?: "the camera did not answer"
        val next = body.int("next") ?: 0
        if (next < 0) throw ApiException(400, "bad_request", "next must be 0 or more")
        world.live.failNextOpens(next, message)
        call.ok(buildJsonObject { put("sent", world.live.errorAll(message)) })
    }
    post("/talk/error") {
        val body = call.jsonBody()
        val code =
            body.str("code")?.takeIf { it in TalkHub.CODES }
                ?: throw ApiException(400, "bad_request", "code is one of ${TalkHub.CODES}")
        val refusal = TalkRefusal(code, body.str("message") ?: TalkHub.defaultMessage(code))
        val next = body.int("next") ?: 0
        if (next < 0) throw ApiException(400, "bad_request", "next must be 0 or more")
        world.talk.failNextOpens(next, refusal)
        call.ok(buildJsonObject { put("sent", world.talk.failAll(refusal)) })
    }
    post("/talk/close") {
        val body = call.optionalBody()
        val code = (body.int("code") ?: Talker.CLOSE_GOING_AWAY.toInt()).toShort()
        val closed = world.talk.end(code, body.str("reason") ?: "closed by the mock's control API")
        call.ok(buildJsonObject { put("closed", closed) })
    }
    post("/scenario/tick") {
        call.ok(buildJsonObject { put("event", scenario.tick()) })
    }
    put("/scenario") {
        val interval = call.jsonBody().double("interval_sec") ?: throw ApiException(400, "bad_request", "send {\"interval_sec\": n}")
        scenario.configure(interval)
        call.ok(buildJsonObject { put("running", scenario.running) })
    }
}

private fun defaultCode(status: Int) =
    when (status) {
        400 -> "bad_request"
        401 -> "invalid_token"
        403 -> "forbidden"
        404 -> "not_found"
        409 -> "conflict"
        422 -> "refused"
        429 -> "too_many_attempts"
        500 -> "internal_error"
        502 -> "bad_upstream"
        503 -> "unavailable"
        else -> "error"
    }

/**
 * Everything no route took: inside `/app/v1` a JSON 404 (or 405 with `Allow` when the path exists
 * for another method) — the access policy already refused an unauthenticated caller; elsewhere a
 * plain JSON 404.
 */
fun Route.fallbackRoutes() {
    route("{...}") {
        handle {
            val path = call.request.path()
            if (path.startsWith("$PREFIX/")) {
                val allowed = methodsAllowed(path.removePrefix(PREFIX))
                if (allowed.isNotEmpty() && call.request.httpMethod !in allowed) {
                    call.response.header(HttpHeaders.Allow, allowed.joinToString(", ") { it.value })
                    throw ApiException(405, "method_not_allowed", "Method Not Allowed")
                }
            }
            throw ApiException(404, "not_found", "Not Found")
        }
    }
}

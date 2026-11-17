package io.github.gamso.petkitlocal.mock.http

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.state.Outgoing
import io.github.gamso.petkitlocal.mock.state.World
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * `GET /notifications/ws` (`handlers/notifications.py`): `hello`, what this session missed after
 * `after`, then each notification as it is sent ([World.sendNotification]). A bad `after` is
 * refused before the upgrade, as JSON.
 */

/** Longest `after` accepted: any id the server can have handed out fits. */
private const val MAX_AFTER_DIGITS = 18

/** The `after` query parameter: absent (or empty), or a non-negative integer. */
private fun ApplicationCall.after(): Long? {
    val raw = request.queryParameters["after"]
    if (raw.isNullOrEmpty()) return null
    if (raw.length > MAX_AFTER_DIGITS || !raw.all { it in '0'..'9' }) {
        throw ApiException(400, "bad_parameter", "after must be a non-negative integer")
    }
    return raw.toLong()
}

/** `after` is read first, upgrade or not, as petkit-local reads it before `prepare()`. */
private val AfterGate =
    createRouteScopedPlugin("NotifyAfterGate") {
        onCall { call -> call.after() }
    }

fun Route.notificationSocketRoute(world: World) {
    route("/notifications/ws") {
        install(AfterGate)
        webSocket {
            val sub = synchronized(world.lock) { world.notify.attach(call.session(), call.after()) }
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
                synchronized(world.lock) { world.notify.detach(sub) }
                writer.cancel()
            }
        }
        get { throw ApiException(400, "bad_request", "not a WebSocket upgrade request") }
    }
}

private const val PONG = """{"type":"pong"}"""

private fun isPing(text: String): Boolean =
    runCatching { Json.parseToJsonElement(text) == JsonObject(mapOf("type" to JsonPrimitive("ping"))) }.getOrDefault(false)

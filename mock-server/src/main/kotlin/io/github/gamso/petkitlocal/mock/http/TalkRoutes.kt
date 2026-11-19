package io.github.gamso.petkitlocal.mock.http

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.state.TalkHub
import io.github.gamso.petkitlocal.mock.state.TalkOut
import io.github.gamso.petkitlocal.mock.state.TalkRefusal
import io.github.gamso.petkitlocal.mock.state.Talker
import io.github.gamso.petkitlocal.mock.state.World
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * `/devices/{id}/talk`, as petkit-local's `app_api/handlers/talk.py` serves it: only what every
 * route refuses is refused before the upgrade (401, 404 `device_not_found`, 426); everything about
 * talking is said on the open socket, as `ready` or an `error` followed by close 4000.
 */

/** Refuses an upgrade for a device that does not exist before Ktor upgrades it, as JSON. */
private class TalkGateConfig {
    lateinit var world: World
}

private val TalkGate =
    createRouteScopedPlugin("TalkGate", ::TalkGateConfig) {
        val world = pluginConfig.world
        onCall { call -> world.deviceOr404(call) }
    }

fun Route.talkRoutes(world: World) {
    route("/devices/{id}/talk") {
        install(TalkGate) { this.world = world }
        webSocket { talkSocket(world) }
        get {
            throw ApiException(426, "upgrade_required", "open this route as a WebSocket")
        }
    }
}

/**
 * One talk: `ready` once the device's slot is taken, then the phone's binary frames, counted,
 * until `{"type":"stop"}` (answered `stopped`, close 1000), the phone closing, a frame over 64 KiB
 * (close 1009), [TalkHub.idleMillis] without audio (`idle_timeout`), or the control API.
 */
private suspend fun DefaultWebSocketServerSession.talkSocket(world: World) {
    val device = world.deviceOr404(call)
    val hub = world.talk
    val talker = Talker(call.session(), device.id)
    val writer =
        launch {
            for (item in talker.outbox) {
                when (item) {
                    is TalkOut.Text -> {
                        send(Frame.Text(item.text))
                    }

                    is TalkOut.Close -> {
                        close(CloseReason(item.code, item.reason))
                        break
                    }
                }
            }
        }
    val refusal = hub.open(talker, synchronized(world.lock) { device.talk })
    if (refusal != null) {
        talker.fail(refusal)
        writer.join()
        return
    }
    talker.text(TalkHub.READY)
    try {
        receive(hub, talker)
    } finally {
        hub.remove(talker)
        writer.join()
    }
}

private suspend fun DefaultWebSocketServerSession.receive(
    hub: TalkHub,
    talker: Talker,
) {
    while (talker.closeCode == null) {
        val frame =
            try {
                withTimeout(hub.idleMillis) { incoming.receiveCatching().getOrNull() }
            } catch (_: TimeoutCancellationException) {
                talker.fail(TalkRefusal(TalkHub.IDLE_TIMEOUT, TalkHub.defaultMessage(TalkHub.IDLE_TIMEOUT)))
                return
            } ?: return // the phone hung up
        when (frame) {
            is Frame.Binary -> {
                if (frame.data.size > TalkHub.MAX_FRAME) {
                    talker.end(Talker.CLOSE_TOO_BIG, "frame over 64 KiB")
                    return
                }
                hub.received(talker, frame.data.size)
            }

            is Frame.Text -> {
                if (isStop(frame.readText())) {
                    talker.text(TalkHub.STOPPED)
                    talker.end(Talker.CLOSE_NORMAL, "")
                    return
                }
            }

            else -> {} // ping, pong, close: Ktor answers them
        }
    }
}

/** Whether a text frame is `{"type":"stop"}`; anything else is ignored, as the server does. */
private fun isStop(text: String): Boolean {
    val message = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return false
    return (message["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content == "stop"
}

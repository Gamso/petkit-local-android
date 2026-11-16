package io.github.gamso.petkitlocal.mock.http

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.state.LiveHub
import io.github.gamso.petkitlocal.mock.state.LiveOut
import io.github.gamso.petkitlocal.mock.state.LiveViewer
import io.github.gamso.petkitlocal.mock.state.MockDevice
import io.github.gamso.petkitlocal.mock.state.World
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.max

/*
 * Live video, as petkit-local serves it from go2rtc: `/devices/{id}/live` (fMP4 over a WebSocket,
 * go2rtc's MSE protocol) and `/devices/{id}/live/hls/{file}` (HLS with fMP4 segments). Both play
 * the mock's test pattern (LiveHub), and every refusal happens before the upgrade, as JSON.
 */

private val VIDEO_MP4 = ContentType("video", "mp4")
private val ISO_SEGMENT = ContentType("video", "iso.segment")
private val MPEG_URL = ContentType("application", "vnd.apple.mpegurl")

/** Segments in a media playlist: 3 s of the stream, at the live edge. */
private const val PLAYLIST_SEGMENTS = 6

/** How far back a segment stays fetchable after it left the playlist. */
private const val SEGMENT_GRACE = 24

/** A camera, or the documented 404 `stream_not_found`. */
private fun World.cameraOr404(call: ApplicationCall): MockDevice {
    val device = deviceOr404(call)
    if (!device.model.isCamera) {
        throw ApiException(
            404,
            "stream_not_found",
            "this device has no live stream (not a camera, the camera patch is not applied, or the camera is not reachable)",
        )
    }
    return device
}

/**
 * Checks a live-socket request before Ktor upgrades it, so a refusal is the documented JSON
 * answer and never a socket that closes at once.
 */
private class LiveGateConfig {
    lateinit var world: World
}

private val LiveGate =
    createRouteScopedPlugin("LiveGate", ::LiveGateConfig) {
        val world = pluginConfig.world
        onCall { call ->
            val device = world.cameraOr404(call)
            val upgrade = call.request.headers[HttpHeaders.Upgrade]
            if (upgrade.equals("websocket", ignoreCase = true)) world.live.checkCapacity(call.session(), device.id)
        }
    }

fun Route.liveVideoRoutes(world: World) {
    route("/devices/{id}/live") {
        install(LiveGate) { this.world = world }
        webSocket { liveSocket(world) }
        get { throw ApiException(426, "upgrade_required", "send a WebSocket upgrade") }
    }
    get("/devices/{id}/live/hls/{file}") { hls(world, call) }
}

/**
 * One live socket: wait for the client's single `mse` request, answer the MIME type, then the
 * init segment and a fragment every 0.5 s, from the stream's live edge. Any other message, or a
 * second request, gets an `error`; binary messages are ignored.
 */
private suspend fun DefaultWebSocketServerSession.liveSocket(world: World) {
    val hub = world.live
    val viewer = LiveViewer(call.session(), world.cameraOr404(call).id)
    hub.open(viewer)
    val writer =
        launch {
            for (item in viewer.outbox) {
                when (item) {
                    is LiveOut.Text -> {
                        send(Frame.Text(item.text))
                    }

                    is LiveOut.Binary -> {
                        send(Frame.Binary(true, item.bytes))
                        viewer.sent(item.bytes.size)
                    }

                    is LiveOut.Close -> {
                        close(CloseReason(item.code, item.reason))
                        break
                    }
                }
            }
        }
    var requested = false
    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue
            if (requested || !isMseRequest(frame.readText())) {
                viewer.text(LiveHub.errorMessage("send one {\"type\":\"mse\",\"value\":\"<codecs>\"} request"))
            } else {
                requested = true
                start(hub, viewer)
            }
        }
    } finally {
        hub.remove(viewer)
        writer.cancel()
    }
}

/** Answers the `mse` request: the MIME type and the stream, or the `error` the control API queued. */
private fun DefaultWebSocketServerSession.start(
    hub: LiveHub,
    viewer: LiveViewer,
) {
    val busy = hub.takePendingError()
    if (busy != null) {
        viewer.streaming = false
        viewer.text(LiveHub.errorMessage(busy))
    } else {
        viewer.text(mseAnswer(hub.pattern.mime))
        launch { stream(hub, viewer) }
    }
}

private suspend fun stream(
    hub: LiveHub,
    viewer: LiveViewer,
) {
    viewer.binary(hub.pattern.init)
    var next = max(hub.newestFragment(), 0)
    while (viewer.streaming) {
        val wait = hub.nanosUntil(next)
        if (wait > 0) delay(wait / NANOS_PER_MILLI + 1)
        if (!viewer.streaming) break
        viewer.binary(hub.pattern.fragment(next++))
    }
}

private const val NANOS_PER_MILLI = 1_000_000L

private fun isMseRequest(text: String): Boolean {
    val message = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return false
    val type = (message["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val value = (message["value"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    return type == "mse" && value != null
}

private fun mseAnswer(mime: String): String = JsonObject(mapOf("type" to JsonPrimitive("mse"), "value" to JsonPrimitive(mime))).toString()

/**
 * HLS: `playlist.m3u8` without `id` opens a view and answers a master playlist naming
 * `playlist.m3u8?id=<view>`; with it, the media playlist of the last [PLAYLIST_SEGMENTS]
 * fragments. Every URI is relative to this route.
 */
private suspend fun hls(
    world: World,
    call: ApplicationCall,
) {
    val device = world.cameraOr404(call)
    val hub = world.live
    val session = call.session()
    val id = call.request.queryParameters["id"]
    when (call.pathParameters()["file"]) {
        "playlist.m3u8" -> {
            if (id == null) {
                hub.checkCapacity(session, device.id)
                val view = hub.openHls(session, device.id)
                call.respondText(masterPlaylist(hub, view.id), MPEG_URL)
            } else {
                hub.hlsView(id, session, device.id)
                call.respondText(mediaPlaylist(hub, id), MPEG_URL)
            }
        }

        "init.mp4" -> {
            hub.hlsView(id ?: throw noHlsSession(), session, device.id)
            call.respondBytes(hub.pattern.init, VIDEO_MP4)
        }

        "segment.m4s" -> {
            hub.hlsView(id ?: throw noHlsSession(), session, device.id)
            val newest = hub.newestFragment()
            val n =
                call.request.queryParameters["n"]
                    ?.toLongOrNull()
                    ?.takeIf { it in max(0, newest - SEGMENT_GRACE)..newest }
                    ?: throw ApiException(404, "not_found", "no such segment")
            call.respondBytes(hub.pattern.fragment(n), ISO_SEGMENT)
        }

        else -> {
            throw ApiException(404, "not_found", "no such file")
        }
    }
}

private fun noHlsSession() = ApiException(404, "hls_session_not_found", "start from playlist.m3u8 without id")

private fun masterPlaylist(
    hub: LiveHub,
    id: String,
): String =
    buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:7")
        appendLine("#EXT-X-STREAM-INF:BANDWIDTH=$BANDWIDTH,CODECS=\"${hub.pattern.codec.lowercase()}\"")
        appendLine("playlist.m3u8?id=$id")
    }

private fun mediaPlaylist(
    hub: LiveHub,
    id: String,
): String {
    val newest = hub.newestFragment()
    val first = max(0, newest - PLAYLIST_SEGMENTS + 1)
    val seconds = hub.pattern.fragmentMillis / MILLIS_PER_SECOND
    return buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:7")
        appendLine("#EXT-X-TARGETDURATION:${kotlin.math.ceil(seconds).toInt()}")
        appendLine("#EXT-X-MEDIA-SEQUENCE:$first")
        appendLine("#EXT-X-MAP:URI=\"init.mp4?id=$id\"")
        for (n in first..newest) {
            appendLine("#EXTINF:${"%.3f".format(java.util.Locale.ROOT, seconds)},")
            appendLine("segment.m4s?id=$id&n=$n")
        }
    }
}

private const val BANDWIDTH = 200_000
private const val MILLIS_PER_SECOND = 1000.0

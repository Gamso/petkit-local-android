package org.petkitlocal.core.network

import kotlinx.serialization.json.Json
import org.petkitlocal.core.network.dto.LiveVideoMessageDto
import org.petkitlocal.core.network.dto.UpdateMessageDto

/**
 * The one JSON configuration for the API, HTTP and WebSocket alike.
 *
 * - `ignoreUnknownKeys`: adding a field to a response is a compatible change for the server
 *   (api/openapi.yaml, "Adding a field ... is a compatible change"), so it must be for the app.
 * - `explicitNulls = false`: an optional request field left null is not sent at all, which is
 *   what "absent" means to the server (`ActionRequest.confirm`, `LoginRequest.device_name`).
 * - `classDiscriminator = "type"`: the field that tells WebSocket messages apart.
 */
val PetkitJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    classDiscriminator = "type"
}

/**
 * One `/ws` text frame, or null for a message type this app does not know yet (the server may
 * add one; skipping it is the compatible reading) or a frame that is not a valid message.
 */
fun decodeUpdateMessage(text: String): UpdateMessageDto? = try {
    PetkitJson.decodeFromString(UpdateMessageDto.serializer(), text)
} catch (_: IllegalArgumentException) { // SerializationException is one
    null
}

/** One live-video text frame, or null for one this app does not understand. */
fun decodeLiveVideoMessage(text: String): LiveVideoMessageDto? = try {
    PetkitJson.decodeFromString(LiveVideoMessageDto.serializer(), text)
} catch (_: IllegalArgumentException) { // SerializationException is one
    null
}

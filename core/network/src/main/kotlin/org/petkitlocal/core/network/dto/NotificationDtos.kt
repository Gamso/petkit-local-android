package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `NotificationSocketMessage`: one JSON text frame of the notification socket
 * (`/notifications/ws`), told apart by `type` (the class discriminator of
 * [org.petkitlocal.core.network.PetkitJson]).
 */
@Serializable
sealed interface NotificationSocketMessageDto {
    /** `hello`: first, once. Then [replayed] notifications with `replay` true follow at once. */
    @Serializable
    @SerialName("hello")
    data class Hello(
        @SerialName("server_time") val serverTime: Double,
        /** The protocol ping interval (45). */
        @SerialName("heartbeat_sec") val heartbeatSec: Double,
        /** The newest notification id logged, whoever it was for; 0 before the first. */
        @SerialName("last_id") val lastId: Long,
        @SerialName("replayed") val replayed: Long,
        /** Notifications after `after` were dropped from the log, or the log was lost. */
        @SerialName("gap") val gap: Boolean,
    ) : NotificationSocketMessageDto

    /** `notification`: one notification, live or replayed. */
    @Serializable
    @SerialName("notification")
    data class Delivery(
        /** Monotonic; the highest one received is passed back as `after`. */
        @SerialName("id") val id: Long,
        @SerialName("replay") val replay: Boolean,
        @SerialName("message") val message: PushMessageDto,
    ) : NotificationSocketMessageDto

    /** `pong`: the answer to a `{"type": "ping"}`. */
    @Serializable
    @SerialName("pong")
    data object Pong : NotificationSocketMessageDto
}

/**
 * `PushMessage`: stable keys only, never text; the app writes the notification from them. A key
 * added within `v` 1 is ignored (`ignoreUnknownKeys`).
 */
@Serializable
data class PushMessageDto(
    @SerialName("v") val v: Int,
    /** One of [NotificationKind.KNOWN], or a kind a newer server added. */
    @SerialName("kind") val kind: String,
    @SerialName("device_id") val deviceId: Long?,
    @SerialName("pet_id") val petId: Long?,
    /** An event code for an event, the state key of a condition, null for `offline`. */
    @SerialName("code") val code: String?,
    /** When it happened (Unix epoch seconds). */
    @SerialName("ts") val ts: Long,
)

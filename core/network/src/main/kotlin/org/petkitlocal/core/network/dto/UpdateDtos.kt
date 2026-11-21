package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `UpdateMessage`: one JSON text frame of the `/ws` live-updates socket, told apart by `type`
 * (the class discriminator of [org.petkitlocal.core.network.PetkitJson]).
 */
@Serializable
sealed interface UpdateMessageDto {
    /** `hello`: first, once. Every device; replaces local state. */
    @Serializable
    @SerialName("hello")
    data class Hello(
        @SerialName("server_time") val serverTime: Double,
        /** The protocol ping interval. */
        @SerialName("heartbeat_sec") val heartbeatSec: Double,
        @SerialName("devices") val devices: List<DeviceDetailDto>,
    ) : UpdateMessageDto

    /** `state`: the entities of one device whose value changed, and only those. */
    @Serializable
    @SerialName("state")
    data class State(
        @SerialName("device_id") val deviceId: Long,
        @SerialName("ts") val ts: Double,
        @SerialName("entities") val entities: List<EntityChangeDto>,
    ) : UpdateMessageDto

    /** `device`: a device's summary changed, or a new device appeared. */
    @Serializable
    @SerialName("device")
    data class Device(@SerialName("device") val device: DeviceSummaryDto) : UpdateMessageDto

    /** `device_removed`. */
    @Serializable
    @SerialName("device_removed")
    data class DeviceRemoved(@SerialName("device_id") val deviceId: Long) : UpdateMessageDto

    /** `timeline`: a new or changed card; drop the cards listed in [replaces]. */
    @Serializable
    @SerialName("timeline")
    data class Timeline(
        @SerialName("item") val item: TimelineItemDto,
        /** Ids of cards sent earlier that this one now stands for. */
        @SerialName("replaces") val replaces: List<Long>,
    ) : UpdateMessageDto

    /**
     * `pet`: a pet that is new or changed (renamed, its weight or devices, a face photo added or
     * removed), from the app or the panel. Replaces the pet with the same id.
     */
    @Serializable
    @SerialName("pet")
    data class Pet(@SerialName("pet") val pet: PetDto) : UpdateMessageDto

    /** `pet_removed`. */
    @Serializable
    @SerialName("pet_removed")
    data class PetRemoved(@SerialName("pet_id") val petId: Long) : UpdateMessageDto

    /** `pong`: the answer to [ClientUpdateMessageDto.Ping]. */
    @Serializable
    @SerialName("pong")
    data object Pong : UpdateMessageDto
}

/** What the app may send on `/ws`: only `{"type": "ping"}`, answered by a `pong`. */
@Serializable
sealed interface ClientUpdateMessageDto {
    @Serializable
    @SerialName("ping")
    data object Ping : ClientUpdateMessageDto
}

/**
 * A text frame of the live-video socket (`/devices/{id}/live`). The document describes this
 * protocol in prose, with no schema, so unit tests pin these shapes rather than the contract.
 */
@Serializable
sealed interface LiveVideoMessageDto {
    /**
     * Client to server, once: [value] is the comma-separated MSE codec list the player supports.
     * Server to client, once: [value] is the MIME type to create the `SourceBuffer` with.
     */
    @Serializable
    @SerialName("mse")
    data class Mse(@SerialName("value") val value: String) : LiveVideoMessageDto

    /** Server to client: the stream could not be opened, or a client message was refused. */
    @Serializable
    @SerialName("error")
    data class Error(@SerialName("value") val value: String) : LiveVideoMessageDto
}

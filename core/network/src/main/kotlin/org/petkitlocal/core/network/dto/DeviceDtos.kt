package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Values of `DeviceSummary.kind`. */
object DeviceKind {
    const val LITTER_BOX = "litter_box"
    const val FEEDER = "feeder"
    const val FOUNTAIN = "fountain"
    const val PURIFIER = "purifier"
    const val OTHER = "other"
    val KNOWN = setOf(LITTER_BOX, FEEDER, FOUNTAIN, PURIFIER, OTHER)
}

/** Values of `DeviceSummary.transport`. */
object DeviceTransport {
    const val MQTT = "mqtt"
    const val HTTP = "http"
    val KNOWN = setOf(MQTT, HTTP)
}

/** Values of `DeviceSummary.connection`. */
object DeviceConnection {
    const val WIFI = "wifi"

    /** A Bluetooth accessory, reached only through the device in `parent_id`. */
    const val BLE = "ble"
    val KNOWN = setOf(WIFI, BLE)
}

/** Values of `DeviceSummary.talk`: whether `/devices/{id}/talk` can start now. */
object DeviceTalk {
    const val AVAILABLE = "available"

    /** The Two-Way Talk patcher is not applied (petkit-local panel, Patchers tab). */
    const val NEEDS_PATCHER = "needs_patcher"

    /** The camera has not reported its network address yet: off, or not heard from. */
    const val DEVICE_UNREACHABLE = "device_unreachable"

    /** No Talk at all: not a camera. */
    const val UNSUPPORTED = "unsupported"
    val KNOWN = setOf(AVAILABLE, NEEDS_PATCHER, DEVICE_UNREACHABLE, UNSUPPORTED)
}

/** Values of `AccessoryRef.kind`. */
object AccessoryKind {
    const val FOUNTAIN = "fountain"
    const val PURIFIER = "purifier"
    const val OTHER = "other"
    val KNOWN = setOf(FOUNTAIN, PURIFIER, OTHER)
}

/** `AccessoryRef`: a Bluetooth accessory as the device relaying it lists it. */
@Serializable
data class AccessoryRefDto(
    /** Its own device id: `GET /devices/{id}` for its values. */
    @SerialName("id") val id: Long,
    /** Codename: `w5`, `w4`, `ctw2`, `ctw3` or `k3`. */
    @SerialName("type") val type: String,
    /** One of [AccessoryKind.KNOWN]. */
    @SerialName("kind") val kind: String,
)

/**
 * `DeviceSummary`. A Bluetooth accessory is a device too: [connection] `ble`, [parentId] the device
 * relaying it, and its `online`/`transport` are its parent's.
 */
@Serializable
data class DeviceSummaryDto(
    @SerialName("id") val id: Long,
    /** Codename, e.g. `t5`, `d4sh`. */
    @SerialName("type") val type: String,
    /**
     * The codename's commercial name, whatever the generation (`Pura Max` for every `t4`);
     * `Unknown` for an unlisted codename. Name the device by [modelName].
     */
    @SerialName("model") val model: String,
    /**
     * The full product name, generation included (`Pura Max 2`); [model] while the generation is
     * unknown. Required by the contract, but a server older than petkit-local#107 does not send
     * it: null then, and the app names the device by [model].
     */
    @SerialName("model_name") val modelName: String?,
    /**
     * Which generation of its codename this is (2 for a Pura Max 2); null while unknown, for a
     * codename sold in one generation only, for an accessory, and from a server that predates it.
     */
    @SerialName("generation") val generation: Int?,
    /** One of [DeviceKind.KNOWN]. */
    @SerialName("kind") val kind: String,
    @SerialName("online") val online: Boolean,
    /** One of [DeviceTransport.KNOWN]. */
    @SerialName("transport") val transport: String,
    /** Latest contact over either transport. */
    @SerialName("last_seen") val lastSeen: Double?,
    @SerialName("firmware") val firmware: String?,
    @SerialName("is_camera") val isCamera: Boolean,
    /**
     * One of [DeviceTalk.KNOWN]: whether `/devices/{id}/talk` can start now. Required by the
     * contract, but a server older than petkit-local#108 does not send it (nor serve the route):
     * null then.
     */
    @SerialName("talk") val talk: String?,
    /**
     * Whether it recognises pets by their face on its own camera: the devices a pet's face photos
     * are worth assigning to. Required by the contract; null from a server older than
     * petkit-local#131, which cannot assign photos either.
     */
    @SerialName("supports_ai") val supportsAi: Boolean?,
    /** One of [DeviceConnection.KNOWN]. */
    @SerialName("connection") val connection: String,
    /** The device relaying this accessory; null for a WiFi device or an unlinked accessory. */
    @SerialName("parent_id") val parentId: Long?,
    /** The accessories this device relays; the first `purifier` holds the box's K3 slot. */
    @SerialName("accessories") val accessories: List<AccessoryRefDto>,
)

/** `DeviceDetail`: `DeviceSummary` plus its serial, entities and actions. */
@Serializable
data class DeviceDetailDto(
    @SerialName("id") val id: Long,
    @SerialName("type") val type: String,
    @SerialName("model") val model: String,
    /** As in [DeviceSummaryDto.modelName]: null from a server that predates it. */
    @SerialName("model_name") val modelName: String?,
    @SerialName("generation") val generation: Int?,
    @SerialName("kind") val kind: String,
    @SerialName("online") val online: Boolean,
    @SerialName("transport") val transport: String,
    @SerialName("last_seen") val lastSeen: Double?,
    @SerialName("firmware") val firmware: String?,
    @SerialName("is_camera") val isCamera: Boolean,
    /** As in [DeviceSummaryDto.talk]: null from a server that predates it. */
    @SerialName("talk") val talk: String?,
    /** As in [DeviceSummaryDto.supportsAi]: null from a server that predates it. */
    @SerialName("supports_ai") val supportsAi: Boolean?,
    @SerialName("connection") val connection: String,
    @SerialName("parent_id") val parentId: Long?,
    @SerialName("accessories") val accessories: List<AccessoryRefDto>,
    @SerialName("serial") val serial: String?,
    @SerialName("entities") val entities: List<EntityDto>,
    /** The buttons it offers, destructive ones last. */
    @SerialName("actions") val actions: List<ActionDto>,
)

/** Values of `Entity.component`. */
object EntityComponent {
    const val SENSOR = "sensor"
    const val BINARY_SENSOR = "binary_sensor"
    const val SWITCH = "switch"
    const val NUMBER = "number"
    const val SELECT = "select"
    const val TEXT = "text"
    const val TIME = "time"
    val KNOWN = setOf(SENSOR, BINARY_SENSOR, SWITCH, NUMBER, SELECT, TEXT, TIME)
}

/** Values of `Entity.category`, besides null. */
object EntityCategory {
    const val CONFIG = "config"
    const val DIAGNOSTIC = "diagnostic"
    val KNOWN = setOf(CONFIG, DIAGNOSTIC)
}

/** `Entity`: one value a device exposes, keyed by its stable Home Assistant entity key. */
@Serializable
data class EntityDto(
    @SerialName("key") val key: String,
    @SerialName("name") val name: String,
    /** One of [EntityComponent.KNOWN]. */
    @SerialName("component") val component: String,
    /** Raw value: a boolean for `binary_sensor` and `switch`; JSON null if unreported. */
    @SerialName("value") val value: JsonElement,
    /** The option label for an enum value, when it has one. */
    @SerialName("display") val display: String?,
    @SerialName("unit") val unit: String?,
    @SerialName("device_class") val deviceClass: String?,
    /** One of [EntityCategory.KNOWN], or null. */
    @SerialName("category") val category: String?,
    /** The labels a `select` offers. */
    @SerialName("options") val options: List<String>?,
    /** Whether `PUT /devices/{id}/entities/{key}` accepts a value for it. */
    @SerialName("settable") val settable: Boolean,
    @SerialName("min") val min: Double?,
    @SerialName("max") val max: Double?,
    @SerialName("step") val step: Double?,
)

/** `EntityChange`: the changing part of an [EntityDto], as a live `state` message carries it. */
@Serializable
data class EntityChangeDto(
    @SerialName("key") val key: String,
    @SerialName("value") val value: JsonElement,
    @SerialName("display") val display: String?,
)

/** `Action`: one of a device's buttons. */
@Serializable
data class ActionDto(
    /** Pass to `POST /devices/{id}/actions/{action}`. */
    @SerialName("key") val key: String,
    @SerialName("name") val name: String,
    /** Cannot be undone: confirm with the user, then send `ActionRequestDto(confirm = true)`. */
    @SerialName("destructive") val destructive: Boolean,
)

/** `ActionRequest`. `confirm` is required, and true, for a destructive action. */
@Serializable
data class ActionRequestDto(@SerialName("confirm") val confirm: Boolean? = null)

/** `ActionResult`. */
@Serializable
data class ActionResultDto(
    @SerialName("ok") val ok: Boolean,
    @SerialName("action") val action: String,
    /** One of [Delivered.KNOWN]. */
    @SerialName("delivered") val delivered: String,
)

/** `EntityWriteRequest`: `value` is typed by the entity's component. */
@Serializable
data class EntityWriteRequestDto(@SerialName("value") val value: JsonElement)

/** `EntityWriteResult`: the entity with the value now stored. */
@Serializable
data class EntityWriteResultDto(
    @SerialName("ok") val ok: Boolean,
    /** One of [Delivered.KNOWN]. */
    @SerialName("delivered") val delivered: String,
    @SerialName("entity") val entity: EntityDto,
)

/** Body of `GET /devices`. */
@Serializable
data class DeviceListResponseDto(@SerialName("devices") val devices: List<DeviceSummaryDto>)

/** Body of `GET /devices/{id}`. */
@Serializable
data class DeviceResponseDto(@SerialName("device") val device: DeviceDetailDto)

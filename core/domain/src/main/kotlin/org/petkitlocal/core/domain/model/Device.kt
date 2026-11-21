package org.petkitlocal.core.domain.model

import java.time.Instant

/**
 * One device the server knows, with what it last reported. The summary fields come from
 * `GET /devices`; [entities], [actions] and [serial] from `GET /devices/{id}`, and stay empty
 * (null) until that detail has been read once.
 *
 * A Bluetooth accessory (a W5 or CTW3 fountain, a Pura Air) is a device too: its [connection] is
 * [DeviceConnection.Ble], [parentId] names the device relaying it, and that device lists it in
 * [accessories]. Its [online] and [transport] are its parent's.
 */
data class Device(
    val id: Long,
    /** Codename, e.g. `t5`, `d4sh`. */
    val type: String,
    /**
     * The full product name, generation included ("Pura Max 2"): the server has no user-given
     * device name, so this is what the app calls the device everywhere. While the generation is
     * unknown, and from a server that does not tell generations apart, the codename's own name
     * ("Pura Max").
     */
    val modelName: String,
    val family: DeviceFamily,
    val online: Boolean,
    val transport: DeviceTransport,
    val lastSeen: Instant?,
    val firmware: String?,
    val isCamera: Boolean,
    val serial: String? = null,
    val entities: List<Entity> = emptyList(),
    /** The buttons it offers, destructive ones last. */
    val actions: List<DeviceAction> = emptyList(),
    val connection: DeviceConnection = DeviceConnection.Wifi,
    /** The device relaying this accessory; null for a WiFi device or an accessory linked to none. */
    val parentId: Long? = null,
    /** The Bluetooth accessories this device relays, in the server's order. */
    val accessories: List<AccessoryRef> = emptyList(),
    /**
     * Which generation of its codename [type] this is (2 for a Pura Max 2); null while the server
     * does not know, for a codename sold in one generation only, for an accessory, and from a
     * server that does not tell generations apart.
     */
    val generation: Int? = null,
    /** Whether the camera can be talked through now, as the server judges it. */
    val talk: TalkAvailability = TalkAvailability.Unsupported,
    /**
     * Whether it recognises pets by their face on its own camera (`supports_ai`): the devices a
     * pet's reference photos are sent to. It can turn true later, once the device first asks for
     * its recognition settings.
     */
    val supportsAi: Boolean = false,
) {
    fun entity(key: String): Entity? = entities.firstOrNull { it.key == key }

    /** A Bluetooth accessory, reached only through [parentId]. */
    val isAccessory: Boolean get() = connection == DeviceConnection.Ble

    /**
     * The Pura Air (K3) this litter box holds in its one K3 slot: the first purifier it lists,
     * as the firmware takes the first (`BLERegistry.get_linked_k3`).
     */
    val puraAirId: Long? get() = accessories.firstOrNull { it.family == DeviceFamily.Purifier }?.id
}

/**
 * Whether a device can be talked through now (`DeviceSummary.talk`), judged by the server from
 * what it recorded: no request reaches the device to answer it, so [Available] is not a promise,
 * and the talk itself may still be refused.
 */
enum class TalkAvailability {
    Available,

    /** The Two-Way Talk patcher is not applied to it (petkit-local's panel, Patchers tab). */
    NeedsPatcher,

    /** The camera has not reported its network address: off, or not heard from since the server started. */
    DeviceUnreachable,

    /** No Talk at all: not a camera. */
    Unsupported,

    /** The server predates talking from the app: it says nothing, and has no talk route. */
    ServerTooOld,

    /** A value the server added later: not available, for a reason this app cannot name. */
    Unknown,
}

/** How a device reaches petkit-local. */
enum class DeviceConnection {
    Wifi,

    /** A Bluetooth accessory with no network of its own, relayed by its parent. */
    Ble,
}

/**
 * A Bluetooth accessory as the device relaying it lists it: its own device [id], its codename
 * and its [family] (a fountain, a purifier, or [DeviceFamily.Other]).
 */
data class AccessoryRef(val id: Long, val type: String, val family: DeviceFamily)

/** The kinds of device the app renders differently (SPEC.md §4). */
enum class DeviceFamily {
    LitterBox,
    Feeder,
    Fountain,
    Purifier,

    /** A kind this app does not know: shown with the generic screen. */
    Other,
}

enum class DeviceTransport {
    Mqtt,
    Http,

    /** A value the server added later. */
    Unknown,
}

/** One of a device's buttons. A [destructive] one is confirmed with the user first. */
data class DeviceAction(
    val key: String,
    val name: String,
    val destructive: Boolean,
    /**
     * A settable switch the page offers among its actions (the T4's Pura Air light): whether it is
     * on now, as the device last reported it; null for a plain button. Pressing it writes the
     * opposite to the entity of the same [key].
     */
    val switchedOn: Boolean? = null,
)

package org.petkitlocal.core.domain.model

import java.time.Instant

/**
 * One device the server knows, with what it last reported. The summary fields come from
 * `GET /devices`; [entities], [actions] and [serial] from `GET /devices/{id}`, and stay empty
 * (null) until that detail has been read once.
 */
data class Device(
    val id: Long,
    /** Codename, e.g. `t5`, `d4sh`. */
    val type: String,
    /** Commercial name; the server has no user-given device name, so this is what is shown. */
    val model: String,
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
) {
    fun entity(key: String): Entity? = entities.firstOrNull { it.key == key }
}

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
data class DeviceAction(val key: String, val name: String, val destructive: Boolean)

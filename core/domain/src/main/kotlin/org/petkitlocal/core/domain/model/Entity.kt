package org.petkitlocal.core.domain.model

/**
 * One value a device exposes, keyed by its stable entity [key] (the Home Assistant entity key
 * petkit-local publishes). [name] is the server's English label; screens translate by [key] and
 * fall back to it (SPEC.md §7).
 */
data class Entity(
    val key: String,
    val name: String,
    val component: EntityComponent,
    val value: EntityValue,
    /** The option label of an enum value, e.g. `idle` for `device_status` -1. */
    val display: String? = null,
    val unit: String? = null,
    val deviceClass: String? = null,
    val category: EntityCategory? = null,
    val options: List<String> = emptyList(),
    val settable: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
) {
    /** A primary value: neither a setting nor a diagnostic. */
    val isPrimary: Boolean get() = category == null
}

enum class EntityComponent {
    Sensor,
    BinarySensor,
    Switch,
    Number,
    Select,
    Text,
    Time,

    /** A component the server added later; shown read-only. */
    Unknown,
}

enum class EntityCategory {
    Config,
    Diagnostic,
}

/** An entity's raw value, typed as the server sent it. */
sealed interface EntityValue {
    /** Not reported (JSON null). */
    data object None : EntityValue

    data class Bool(val value: Boolean) : EntityValue

    data class Number(val value: Double) : EntityValue

    data class Text(val value: String) : EntityValue

    /** An object or array: kept as its JSON text, which no screen interprets yet. */
    data class Other(val json: String) : EntityValue
}

/** The value as a number, if it is one. */
val EntityValue.asDouble: Double?
    get() = (this as? EntityValue.Number)?.value

/** The value as a boolean, if it is one. */
val EntityValue.asBoolean: Boolean?
    get() = (this as? EntityValue.Bool)?.value

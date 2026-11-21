package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.AccessoryRef
import io.github.gamso.petkitlocal.mock.api.ActionDto
import io.github.gamso.petkitlocal.mock.api.DeviceDetail
import io.github.gamso.petkitlocal.mock.api.DeviceSummary
import io.github.gamso.petkitlocal.mock.api.EntityChange
import io.github.gamso.petkitlocal.mock.api.EntityDto
import io.github.gamso.petkitlocal.mock.api.plainNumber
import io.github.gamso.petkitlocal.mock.catalog.EntityDef
import io.github.gamso.petkitlocal.mock.catalog.Generations
import io.github.gamso.petkitlocal.mock.catalog.ModelDef
import kotlinx.coroutines.Job
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Components that carry a value; buttons are the device's `actions` instead. */
val VALUE_COMPONENTS = setOf("sensor", "binary_sensor", "switch", "number", "select", "text", "time")

/** `DeviceSummary.talk`: the camera can be talked through now. */
const val TALK_AVAILABLE = "available"

/** `DeviceSummary.talk`: the Two-Way Talk patcher is not applied. */
const val TALK_NEEDS_PATCHER = "needs_patcher"

/** `DeviceSummary.talk`: the camera has not reported its network address. */
const val TALK_DEVICE_UNREACHABLE = "device_unreachable"

/** `DeviceSummary.talk`: no camera to talk through. */
const val TALK_UNSUPPORTED = "unsupported"

/** Every value of `DeviceSummary.talk`. */
val TALK_VALUES = setOf(TALK_AVAILABLE, TALK_NEEDS_PATCHER, TALK_DEVICE_UNREACHABLE, TALK_UNSUPPORTED)

/** Bluetooth accessories: no network of their own, reached through a parent (`devices/ble/`). */
val BLE_TYPES = setOf("w5", "w4", "ctw2", "ctw3", "k3")

/**
 * One fake device. Its state is a document keyed by the entities' `value_path`s, exactly the
 * paths petkit-local resolves (`ha/state_doc.py`), so two entities reading one field (a W7H's
 * `disinfecting` and `deep_clean_stage`) can never disagree.
 */
class MockDevice(
    val id: Int,
    val model: ModelDef,
    val serial: String?,
    var firmware: String?,
    var transport: String,
) {
    val type: String get() = model.type

    /** A Bluetooth accessory, relayed by [parent] (`app_api/inventory.py`). */
    val isAccessory: Boolean get() = type in BLE_TYPES

    /** The device relaying this accessory; null for a WiFi device or an unlinked accessory. */
    var parent: MockDevice? = null

    /**
     * Which generation of its codename this is, as the owner set it in petkit-local's panel or as
     * learned there; null while unknown. Only a generation the server names is reported.
     */
    var generation: Int? = null

    /** Its full product name (`model_name`): its generation's, else its codename's (`model`). */
    val modelName: String get() = Generations.name(type, generation) ?: model.model

    /**
     * Whether it can be talked through now (`DeviceSummary.talk`), as petkit-local judges it from
     * what it recorded: `available` for a camera with the Two-Way Talk patcher and a known address,
     * `needs_patcher`, `device_unreachable`, and `unsupported` for anything without a camera.
     */
    var talk: String = if (model.isCamera && !isAccessory) TALK_AVAILABLE else TALK_UNSUPPORTED

    /**
     * Whether it recognises pets by their face (`DeviceSummary.supports_ai`), as petkit-local
     * judges it from the model and its generation. Always reported false for an accessory.
     */
    var supportsAi: Boolean = false

    /** The accessories this device relays, in the order the server lists them. */
    val accessories: MutableList<MockDevice> = mutableListOf()

    private var reachable: Boolean = true

    /** An accessory is online while the device relaying it is, as `accessory_summary` says. */
    var online: Boolean
        get() = if (isAccessory) parent?.online == true else reachable
        set(value) {
            reachable = value
        }
    var lastSeen: Double? = null
    val doc: MutableMap<String, JsonElement> = mutableMapOf()

    /** A feeder's recurring meals and one-off feeds; null for anything else. */
    var plan: FeedPlan? = null

    /** The job that returns the device to idle after an action or a cycle. */
    var busyJob: Job? = null
    val isBusy: Boolean get() = busyJob?.isActive == true

    val isLitter get() = model.kind == "litter_box"
    val isFeeder get() = model.kind == "feeder"
    val isFountain get() = model.kind == "fountain"
    val isDualHopper get() = type in DUAL_HOPPER

    fun has(key: String) = model.has(key)

    operator fun get(path: String): JsonElement? = doc[path]

    operator fun set(
        path: String,
        value: Any?,
    ) {
        doc[path] = json(value)
    }

    fun number(path: String): Double? = (doc[path] as? JsonPrimitive)?.doubleOrNull

    fun summary() =
        DeviceSummary(
            id = id,
            type = type,
            model = model.model,
            modelName = modelName,
            generation = generation?.takeIf { Generations.name(type, it) != null },
            kind = model.kind,
            online = online,
            // An accessory travels on its parent's transport (`http` when it has none).
            transport = if (isAccessory) parent?.transport ?: "http" else transport,
            // A K3's readings ride on its parent's reports: it has no contact of its own.
            lastSeen = if (type == "k3") null else lastSeen,
            firmware = if (isAccessory) null else firmware,
            isCamera = model.isCamera,
            talk = talk,
            // An accessory has no camera of its own: never.
            supportsAi = supportsAi && !isAccessory,
            connection = if (isAccessory) "ble" else "wifi",
            parentId = parent?.id,
            accessories = accessories.map { AccessoryRef(it.id, it.type, it.model.kind) },
        )

    /**
     * Its value entities: the model's, and `k3_light` while it is a T4 relaying a K3, as
     * petkit-local offers that switch only then (`k3LightSwitch`, petkit-local#133/#136).
     */
    fun valueEntities(): List<EntityDef> =
        model.entities.filter { it.component in VALUE_COMPONENTS } + listOfNotNull(K3_LIGHT.takeIf { hasK3Light })

    /** One of [valueEntities], by key. */
    fun valueEntity(key: String): EntityDef? = valueEntities().firstOrNull { it.key == key }

    /** A T4 relaying a K3: its "K3 Light" switch is offered. */
    val hasK3Light: Boolean get() = type == "t4" && accessories.any { it.type == "k3" }

    fun entities(): List<EntityDto> = valueEntities().map { render(it) }

    fun entity(key: String): EntityDto? = valueEntity(key)?.let { render(it) }

    fun changes(): Map<String, EntityChange> = entities().associate { it.key to it.change() }

    fun detail(): DeviceDetail {
        val s = summary()
        return DeviceDetail(
            id = s.id,
            type = s.type,
            model = s.model,
            modelName = s.modelName,
            generation = s.generation,
            kind = s.kind,
            online = s.online,
            transport = s.transport,
            lastSeen = s.lastSeen,
            firmware = s.firmware,
            isCamera = s.isCamera,
            talk = s.talk,
            supportsAi = s.supportsAi,
            connection = s.connection,
            parentId = s.parentId,
            accessories = s.accessories,
            serial = serial,
            entities = entities(),
            actions = model.actions.map { ActionDto(it.key, it.name, it.destructive) },
        )
    }

    /** One entity with its value, as `app_api/serializers.py::entity_value` builds it. */
    fun render(def: EntityDef): EntityDto {
        val raw = doc[def.valuePath]?.takeUnless { it is JsonNull }
        val value =
            if (def.component == "binary_sensor" || def.component == "switch") {
                raw?.let { JsonPrimitive(truthy(it)) }
            } else {
                raw
            }
        val isNumber = def.component == "number"
        return EntityDto(
            key = def.key,
            name = def.name,
            component = def.component,
            value = value,
            display = display(def, raw),
            unit = def.unit,
            deviceClass = def.deviceClass,
            category = def.category,
            options = if (def.component == "select") def.options else null,
            settable = def.settable,
            min = if (isNumber) def.min else null,
            max = if (isNumber) def.max else null,
            step = if (isNumber) def.step else null,
        )
    }

    companion object {
        val DUAL_HOPPER = setOf("d4s", "d4sh")

        /**
         * The T4's K3 light (petkit-local#136): on is `start_action 7`, off `end_action 7`, and
         * its state is the box's `k3LightSwitch`, which the K3 clears itself after its timer.
         */
        val K3_LIGHT =
            EntityDef(
                key = "k3_light",
                name = "K3 Light",
                component = "switch",
                valuePath = "state.k3LightSwitch",
                settable = true,
            )

        /**
         * The option label a raw enum value stands for: `option_values`, else the index; then a
         * value the select only shows (`readonly_options`, a CTW3's `battery`), whose label is
         * not among its options (`app_api/serializers.py::_display`).
         */
        fun display(
            def: EntityDef,
            raw: JsonElement?,
        ): String? {
            val options = def.options ?: return null
            val prim = raw as? JsonPrimitive ?: return null
            val values = def.optionValues ?: options.indices.map { JsonPrimitive(it) }
            values.forEachIndexed { i, candidate ->
                if (i < options.size && sameScalar(candidate, prim)) return options[i]
            }
            return def.readonlyOptions
                ?.entries
                ?.firstOrNull { (value, _) -> sameScalar(JsonPrimitive(value), prim) }
                ?.value
        }

        private fun sameScalar(
            a: JsonPrimitive,
            b: JsonPrimitive,
        ): Boolean {
            if (a.content == b.content) return true
            val x = a.doubleOrNull
            val y = b.doubleOrNull
            return x != null && y != null && x == y
        }

        /** Python truthiness, which is how HA and the server read a binary sensor or switch. */
        fun truthy(e: JsonElement): Boolean =
            when (e) {
                is JsonNull -> false
                is JsonPrimitive -> e.booleanOrNull ?: e.doubleOrNull?.let { it != 0.0 } ?: e.content.isNotEmpty()
                is JsonObject -> e.isNotEmpty()
                is JsonArray -> e.isNotEmpty()
            }
    }
}

/** A Kotlin value as JSON: numbers, strings, booleans, null, or an element already. */
fun json(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) JsonPrimitive(value.toLong()) else plainNumber(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> error("not a JSON value: $value")
    }

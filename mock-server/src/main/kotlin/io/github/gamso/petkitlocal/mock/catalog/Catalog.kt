package io.github.gamso.petkitlocal.mock.catalog

import io.github.gamso.petkitlocal.mock.api.MockJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * What each model exposes, generated from petkit-local's own entity and action definitions by
 * `tools/dump_catalog.py` (the `source` commit is recorded in the file). Nothing here is retyped
 * by hand, so the mock shows the entity keys and action keys the real server shows.
 */
@Serializable
data class Catalog(
    val source: String,
    val models: Map<String, ModelDef>,
) {
    fun model(type: String): ModelDef = models[type] ?: error("no model $type in the catalog")

    companion object {
        fun load(): Catalog {
            val text =
                Catalog::class.java.getResourceAsStream("/catalog.json")?.use { it.readBytes().decodeToString() }
                    ?: error("catalog.json is missing from the classpath")
            return MockJson.decodeFromString(serializer(), text)
        }
    }
}

@Serializable
data class ModelDef(
    val type: String,
    val model: String,
    val kind: String,
    @SerialName("is_camera") val isCamera: Boolean,
    val entities: List<EntityDef>,
    val actions: List<ActionDef>,
) {
    fun entity(key: String): EntityDef? = entities.firstOrNull { it.key == key }

    fun action(key: String): ActionDef? = actions.firstOrNull { it.key == key }

    fun has(key: String): Boolean = entity(key) != null
}

@Serializable
data class EntityDef(
    val key: String,
    val name: String,
    val component: String,
    @SerialName("value_path") val valuePath: String,
    val unit: String? = null,
    @SerialName("device_class") val deviceClass: String? = null,
    val category: String? = null,
    val options: List<String>? = null,
    @SerialName("option_values") val optionValues: List<JsonPrimitive>? = null,
    val settable: Boolean,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
) {
    /** The raw value standing for option label [label] (its index when the entity has no values). */
    fun optionValue(label: String): JsonPrimitive? {
        val index = options?.indexOf(label)?.takeIf { it >= 0 } ?: return null
        return optionValues?.getOrNull(index) ?: JsonPrimitive(index)
    }
}

@Serializable
data class ActionDef(
    val key: String,
    val name: String,
    val destructive: Boolean,
)

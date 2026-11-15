package org.petkitlocal.core.domain.device

import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory

/**
 * A device's entities as its screens show them: what it reports ([readings], [diagnostics]) and
 * what can be changed ([settings], grouped). Each entity is in exactly one place, in the server's
 * order; nothing is chosen by model (SPEC.md §4: capabilities come from the API).
 */
data class DeviceSections(
    /** Primary values it reports: levels, states, last events. */
    val readings: List<Entity>,
    /** Values for troubleshooting, out of the way. */
    val diagnostics: List<Entity>,
    /** Every setting with an editor, by [SettingCategory], in its order; empty ones left out. */
    val settings: List<SettingGroup>,
) {
    val settingsCount: Int get() = settings.sumOf { it.entities.size }

    companion object {
        fun of(device: Device): DeviceSections {
            val (editable, readOnly) = device.entities.partition(EntityWrites::isEditable)
            return DeviceSections(
                readings = readOnly.filter { it.category != EntityCategory.Diagnostic },
                diagnostics = readOnly.filter { it.category == EntityCategory.Diagnostic },
                settings = editable.groupBy(SettingCatalog::category)
                    .toSortedMap()
                    .map { (category, entities) -> SettingGroup(category, entities) },
            )
        }
    }
}

/** The settings of one [category], in the server's order. */
data class SettingGroup(val category: SettingCategory, val entities: List<Entity>)

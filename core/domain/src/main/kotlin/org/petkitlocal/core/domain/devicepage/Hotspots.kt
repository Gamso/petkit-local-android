package org.petkitlocal.core.domain.devicepage

import kotlin.math.roundToInt
import org.petkitlocal.core.domain.home.HomeAlerts
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.asBoolean
import org.petkitlocal.core.domain.model.asDouble

/**
 * The parts of a device drawn on its hero (SPEC.md §5.3, §5.6). Each family's illustration places
 * them; [Other] is a consumable this app has no place for, shown beside the others.
 */
enum class DevicePart {
    Litter,
    WasteBin,

    /** The N50 odour eliminator, counted in days. */
    Deodorizer,

    /** The N60 deodorizing spray, counted in days. */
    Spray,

    /** The N60 of a Pura Air (K3) paired with a box that has no sprayer of its own, by its level. */
    PuraAirSpray,

    /** Waste bags (Purobot Ultra). */
    Bags,
    Food,
    Hopper1,
    Hopper2,
    Bowl,
    Desiccant,

    /** A fountain's water: low or not, as the fountain says. */
    Water,

    /** A fountain's filter cartridge, by its level or its days left. */
    Filter,

    /** A fountain's circulation pump, running or not. */
    Pump,

    /** A cordless fountain's battery. */
    Battery,

    /** The W7H's drinking tray. */
    Tray,

    /** The W7H's clean water tank, which refills the tray. */
    CleanTank,

    /** The W7H's waste water tank. */
    WasteTank,
    Other,
}

/** What a part reports. */
sealed interface PartReading {
    data class Percent(val value: Int) : PartReading

    data class DaysLeft(val days: Int) : PartReading

    data class Count(val value: Int) : PartReading

    /** A word the server sends, e.g. a hopper's `Empty`: shown through the option labels. */
    data class Option(val label: String) : PartReading

    data class Condition(val condition: PartCondition) : PartReading
}

enum class PartCondition {
    Ok,
    Full,
    Removed,
    Missing,
    Low,

    /** A pump at work. */
    Running,

    /** A pump at rest: not a fault, a fountain in smart mode rests between runs. */
    Stopped,
}

/**
 * One hotspot pill: a [part], the entity it reads ([entityKey], [entityName] in English for the
 * labels' fallback), its [reading], whether it needs [attention], and the device's [actions] that
 * act on that part (level the litter, reset the N50...).
 */
data class Hotspot(
    val part: DevicePart,
    val entityKey: String,
    val entityName: String,
    val reading: PartReading,
    val attention: Boolean,
    val actions: List<DeviceAction>,
)

/**
 * Reads hotspots from a device's entities by their stable keys: a part shows only when the device
 * reports a value for it (nothing reported is no pill, never a guess), and, for a litter box or a
 * fountain, only when the model has that part ([LitterParts], [FountainParts]): the server lists
 * some entities on every model of a family.
 */
object HotspotRules {
    /** Keys already shown by a family's own pills, so [otherConsumables] does not repeat them. */
    private val knownKeys = setOf(
        "litter_percent",
        "n50_durability",
        "n60_spray_days",
        "package_count",
        "desiccant_days",
    )

    /**
     * [puraAir] is the Pura Air (K3) paired with [device], if any: on a box without a sprayer of
     * its own, its N60 is shown as the box's, by the liquid level it reports.
     */
    fun litterBox(device: Device, puraAir: Device? = null): List<Hotspot> =
        litterReadings(device).filter { LitterParts.has(device, it.part, puraAir) } +
            listOfNotNull(puraAirSpray(device, puraAir))

    private fun puraAirSpray(device: Device, puraAir: Device?): Hotspot? {
        val liquid = puraAir?.takeIf { LitterParts.has(device, DevicePart.PuraAirSpray, it) }
            ?.entity(PURA_AIR_LIQUID) ?: return null
        val percent = liquid.value.asDouble?.roundToInt() ?: return null
        return liquid.hotspot(
            DevicePart.PuraAirSpray,
            PartReading.Percent(percent),
            attention = percent <= HomeAlerts.LOW_PERCENT_ALERT,
            actions = LitterParts.actions(device, puraAir).filter { it.key in SPRAY_ACTIONS },
        )
    }

    private fun litterReadings(device: Device): List<Hotspot> = listOfNotNull(
        device.percent("litter_percent", DevicePart.Litter, "level_litter", "dump_litter"),
        wasteBin(device),
        device.daysLeft("n50_durability", DevicePart.Deodorizer, "reset_n50"),
        spray(device),
        device.entity("package_count")?.let { entity ->
            entity.value.asDouble?.let { count ->
                entity.hotspot(
                    DevicePart.Bags,
                    PartReading.Count(count.roundToInt()),
                    attention = count <= 0,
                    actions = device.actionsFor("pack_waste", "reset_bagbox"),
                )
            }
        },
    ) + otherConsumables(device)

    fun feeder(device: Device): List<Hotspot> = food(device) + listOfNotNull(
        device.percent("food_bowl_pct", DevicePart.Bowl, attentionAtOrBelow = null),
        device.daysLeft("desiccant_days", DevicePart.Desiccant, "reset_desiccant"),
    ) + otherConsumables(device)

    /**
     * A fountain's parts, read by the keys petkit-local gives them: the W5 family's `w5_*`, the
     * CTW3's `ctw3_*`, the W7H's own, and the plain keys of a WiFi fountain it has no model for.
     */
    fun fountain(device: Device): List<Hotspot> =
        fountainReadings(device).filter { FountainParts.has(device, it.part) } +
            otherConsumables(device).filterNot { it.entityKey == FILTER_DAYS }

    private fun wasteBin(device: Device): Hotspot? {
        val full = device.entity("waste_bin")?.takeIf { it.value.asBoolean != null }
        val present = device.entity("waste_bin_present")?.takeIf { it.value.asBoolean != null }
        val entity = full ?: present ?: return null
        val condition = when {
            present?.value?.asBoolean == false -> PartCondition.Removed
            full?.value?.asBoolean == true -> PartCondition.Full
            else -> PartCondition.Ok
        }
        return entity.hotspot(
            DevicePart.WasteBin,
            PartReading.Condition(condition),
            attention = condition != PartCondition.Ok,
            actions = device.actionsFor("pack_waste"),
        )
    }

    /**
     * The N60: "Missing" only when the box itself says so (`deodorizer_present`, its `sprayState`,
     * which petkit-local finds in every one of 1254 captured snapshots, 1 with a bottle in); else
     * its days left, when it reports them.
     */
    private fun spray(device: Device): Hotspot? {
        val present = device.entity("deodorizer_present")
        if (present?.value?.asBoolean == false) {
            return present.hotspot(
                DevicePart.Spray,
                PartReading.Condition(PartCondition.Missing),
                attention = true,
                actions = device.actionsFor("reset_n60"),
            )
        }
        return device.daysLeft("n60_spray_days", DevicePart.Spray, *SPRAY_ACTIONS.toTypedArray())
    }

    private val SPRAY_ACTIONS = listOf("reset_n60", "deodorize")

    /** A Pura Air's N60 level (`ha/entities/ble.py` `K3_ENTITIES`). */
    private const val PURA_AIR_LIQUID = "k3_liquid"

    /** Hoppers when the feeder reports them one by one, else its food level, else its low flag. */
    private fun food(device: Device): List<Hotspot> {
        val actions = device.actionsFor("food_replenished")
        val hoppers = listOf("hopper1_level" to DevicePart.Hopper1, "hopper2_level" to DevicePart.Hopper2)
            .mapNotNull { (key, part) -> device.entity(key)?.optionHotspot(part, actions) }
        val level = device.entity("hopper_level")?.optionHotspot(DevicePart.Food, actions)
        return when {
            hoppers.isNotEmpty() -> hoppers
            level != null -> listOf(level)
            else -> listOfNotNull(lowFood(device, actions))
        }
    }

    private fun lowFood(device: Device, actions: List<DeviceAction>): Hotspot? {
        val low = device.entity("food_low") ?: return null
        val isLow = low.value.asBoolean ?: return null
        return low.hotspot(
            DevicePart.Food,
            PartReading.Condition(if (isLow) PartCondition.Low else PartCondition.Ok),
            attention = isLow,
            actions = actions,
        )
    }

    /** Primary day counters no family pill covers: a consumable the server added later. */
    private fun otherConsumables(device: Device): List<Hotspot> = device.entities
        .filter { it.isPrimary && it.component == EntityComponent.Sensor && it.unit == DAYS && it.key !in knownKeys }
        .mapNotNull { entity ->
            entity.value.asDouble?.let { days ->
                entity.hotspot(
                    DevicePart.Other,
                    PartReading.DaysLeft(days.roundToInt()),
                    attention = days <= HomeAlerts.DAYS_LEFT_ALERT,
                    actions = emptyList(),
                )
            }
        }

    private const val DAYS = "days"
}

private fun Device.percent(
    key: String,
    part: DevicePart,
    vararg actionKeys: String,
    attentionAtOrBelow: Int? = HomeAlerts.LOW_PERCENT_ALERT,
): Hotspot? {
    val entity = entity(key) ?: return null
    val value = entity.value.asDouble?.roundToInt() ?: return null
    return entity.hotspot(
        part,
        PartReading.Percent(value),
        attention = attentionAtOrBelow != null && value <= attentionAtOrBelow,
        actions = actionsFor(*actionKeys),
    )
}

private fun Device.daysLeft(key: String, part: DevicePart, vararg actionKeys: String): Hotspot? {
    val entity = entity(key) ?: return null
    val days = entity.value.asDouble?.roundToInt() ?: return null
    return entity.hotspot(
        part,
        PartReading.DaysLeft(days),
        attention = days <= HomeAlerts.DAYS_LEFT_ALERT,
        actions = actionsFor(*actionKeys),
    )
}

private fun Entity.optionHotspot(part: DevicePart, actions: List<DeviceAction>): Hotspot? {
    val label = display ?: return null
    return hotspot(part, PartReading.Option(label), attention = label == EMPTY_OPTION, actions = actions)
}

internal fun Entity.hotspot(part: DevicePart, reading: PartReading, attention: Boolean, actions: List<DeviceAction>) =
    Hotspot(part, key, name, reading, attention, actions)

/** The device's actions among [keys], in the device's order. */
internal fun Device.actionsFor(vararg keys: String): List<DeviceAction> = actions.filter { it.key in keys }

/** A hopper's option label when it has no food left. */
private const val EMPTY_OPTION = "Empty"

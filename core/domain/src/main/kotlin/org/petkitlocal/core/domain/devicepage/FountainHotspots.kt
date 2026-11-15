package org.petkitlocal.core.domain.devicepage

import kotlin.math.roundToInt
import org.petkitlocal.core.domain.home.HomeAlerts
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.asBoolean
import org.petkitlocal.core.domain.model.asDouble

/*
 * A fountain's hotspots, read by the keys petkit-local gives them: the W5 family's `w5_*`, the
 * CTW3's `ctw3_*`, the W7H's own, and the plain keys of a WiFi fountain it has no model for.
 * [HotspotRules.fountain] keeps those of the parts the model has ([FountainParts]).
 */

/** Every fountain part the device reports a value for, in the order the hero lists them. */
internal fun fountainReadings(device: Device): List<Hotspot> = listOfNotNull(
    water(device),
    filter(device),
    pump(device),
    battery(device),
    tray(device),
    device.installed("clean_tank_installed", DevicePart.CleanTank, "fountain_refill"),
    device.installed("waste_tank_installed", DevicePart.WasteTank),
)

/** Water: "Low" only while the fountain raises its flag; lowered, it reads OK. */
private fun water(device: Device): Hotspot? {
    val flag = device.firstReported(WATER_LOW_KEYS) ?: return null
    val low = flag.value.asBoolean == true
    return flag.hotspot(
        DevicePart.Water,
        PartReading.Condition(if (low) PartCondition.Low else PartCondition.Ok),
        attention = low,
        actions = device.actionsFor("fountain_refill"),
    )
}

/**
 * The filter: its days left when reported, else its level; it needs attention when either runs
 * low or when the fountain raises its own replace flag. A flag with no level reads OK or Low.
 */
private fun filter(device: Device): Hotspot? {
    val warnFlag = device.firstReported(FILTER_WARN_KEYS)
    val warn = warnFlag?.value?.asBoolean == true
    val actions = device.actionsFor(*FILTER_RESETS.toTypedArray())
    val days = device.entity(FILTER_DAYS)?.takeIf { it.value.asDouble != null }
    val level = days ?: device.firstNumber(FILTER_LEVEL_KEYS)
    if (level != null) {
        val value = level.value.asDouble?.roundToInt() ?: 0
        val reading = if (level === days) PartReading.DaysLeft(value) else PartReading.Percent(value)
        val low = if (level === days) HomeAlerts.DAYS_LEFT_ALERT else HomeAlerts.LOW_PERCENT_ALERT
        return level.hotspot(DevicePart.Filter, reading, attention = warn || value <= low, actions = actions)
    }
    return warnFlag?.hotspot(
        DevicePart.Filter,
        PartReading.Condition(if (warn) PartCondition.Low else PartCondition.Ok),
        attention = warn,
        actions = actions,
    )
}

private fun pump(device: Device): Hotspot? {
    val running = device.firstReported(PUMP_KEYS) ?: return null
    val on = running.value.asBoolean == true
    return running.hotspot(
        DevicePart.Pump,
        PartReading.Condition(if (on) PartCondition.Running else PartCondition.Stopped),
        attention = false,
        actions = device.actionsFor("fountain_reset_circulation_pump"),
    )
}

/** The battery's level, needing attention when low or when the fountain says it is. */
private fun battery(device: Device): Hotspot? {
    val level = device.firstNumber(BATTERY_KEYS) ?: return null
    val percent = level.value.asDouble?.roundToInt() ?: return null
    val low = device.firstReported(LOW_BATTERY_KEYS)?.value?.asBoolean == true
    return level.hotspot(
        DevicePart.Battery,
        PartReading.Percent(percent),
        attention = low || percent <= HomeAlerts.LOW_PERCENT_ALERT,
        actions = emptyList(),
    )
}

/** The W7H's tray: removed, full or OK, from its two flags, as a litter box's waste bin. */
private fun tray(device: Device): Hotspot? {
    val full = device.entity("tray_full")?.takeIf { it.value.asBoolean != null }
    val present = device.entity("tray_installed")?.takeIf { it.value.asBoolean != null }
    val entity = full ?: present ?: return null
    val condition = when {
        present?.value?.asBoolean == false -> PartCondition.Removed
        full?.value?.asBoolean == true -> PartCondition.Full
        else -> PartCondition.Ok
    }
    return entity.hotspot(
        DevicePart.Tray,
        PartReading.Condition(condition),
        attention = condition != PartCondition.Ok,
        actions = device.actionsFor("fountain_flush", "fountain_drain"),
    )
}

/* A fountain's keys, the W5 family's first, then the CTW3's, then a WiFi fountain's. */
internal const val FILTER_DAYS = "filter_days"
private val WATER_LOW_KEYS = listOf("w5_water_missing", "ctw3_water_lack", "water_lack")
private val FILTER_LEVEL_KEYS = listOf("w5_filter", "ctw3_filter", "filter_percent")
private val FILTER_WARN_KEYS = listOf("w5_filter_warn", "ctw3_filter_warn", "replace_filter")
private val FILTER_RESETS = listOf("w5_reset_filter", "ctw3_reset_filter", "reset_filter")
private val PUMP_KEYS = listOf("w5_running", "ctw3_running", "pump_running")
private val BATTERY_KEYS = listOf("ctw3_battery", "battery")
private val LOW_BATTERY_KEYS = listOf("ctw3_low_battery", "low_battery")

/** The first of [keys] the device reports a yes or no for. */
private fun Device.firstReported(keys: List<String>): Entity? =
    keys.firstNotNullOfOrNull { key -> entity(key)?.takeIf { it.value.asBoolean != null } }

/** The first of [keys] the device reports a number for. */
private fun Device.firstNumber(keys: List<String>): Entity? =
    keys.firstNotNullOfOrNull { key -> entity(key)?.takeIf { it.value.asDouble != null } }

/** A part in place or not, by its "installed" flag [key]: "Removed" only when it says so. */
private fun Device.installed(key: String, part: DevicePart, vararg actionKeys: String): Hotspot? {
    val entity = entity(key) ?: return null
    val present = entity.value.asBoolean ?: return null
    return entity.hotspot(
        part,
        PartReading.Condition(if (present) PartCondition.Ok else PartCondition.Removed),
        attention = !present,
        actions = actionsFor(*actionKeys),
    )
}

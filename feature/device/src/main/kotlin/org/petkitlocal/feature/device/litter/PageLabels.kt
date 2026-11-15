package org.petkitlocal.feature.device.litter

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.devicepage.PartCondition
import org.petkitlocal.core.domain.devicepage.PartReading
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.feature.device.R

/*
 * The litter box and feeder pages' words for their parts and what each reads, by stable key
 * (through ServerLabels) where the server names them.
 */

/** A hotspot's name: the page's word for its part, else the server's entity name. */
@Composable
internal fun hotspotLabel(hotspot: Hotspot, labels: ServerLabels): String =
    partLabel(hotspot.part)?.let { stringResource(it) } ?: labels.entity(hotspot.entityKey, hotspot.entityName)

@StringRes
private fun partLabel(part: DevicePart): Int? = when (part) {
    DevicePart.Litter -> R.string.page_part_litter
    DevicePart.WasteBin -> R.string.page_part_waste_bin
    DevicePart.Deodorizer -> R.string.page_part_deodorizer
    DevicePart.Spray -> R.string.page_part_spray
    DevicePart.PuraAirSpray -> R.string.page_part_pura_air
    DevicePart.Bags -> R.string.page_part_bags
    DevicePart.Food -> R.string.page_part_food
    DevicePart.Hopper1 -> R.string.page_part_hopper1
    DevicePart.Hopper2 -> R.string.page_part_hopper2
    DevicePart.Bowl -> R.string.page_part_bowl
    DevicePart.Desiccant -> R.string.page_part_desiccant
    DevicePart.Other -> null
}

@StringRes
internal fun partExplanation(part: DevicePart): Int = when (part) {
    DevicePart.Litter -> R.string.page_explain_litter
    DevicePart.WasteBin -> R.string.page_explain_waste_bin
    DevicePart.Deodorizer -> R.string.page_explain_deodorizer
    DevicePart.Spray -> R.string.page_explain_spray
    DevicePart.PuraAirSpray -> R.string.page_explain_pura_air
    DevicePart.Bags -> R.string.page_explain_bags
    DevicePart.Food, DevicePart.Hopper1, DevicePart.Hopper2 -> R.string.page_explain_food
    DevicePart.Bowl -> R.string.page_explain_bowl
    DevicePart.Desiccant -> R.string.page_explain_desiccant
    DevicePart.Other -> R.string.page_explain_other
}

/** What a hotspot reads: "91 %", "21 days left" ([short]: "21 days", in a pill), "Full"... */
@Composable
internal fun readingText(reading: PartReading, labels: ServerLabels, short: Boolean = false): String = when (reading) {
    is PartReading.Percent -> stringResource(R.string.page_reading_percent, reading.value)

    is PartReading.DaysLeft -> pluralStringResource(
        if (short) R.plurals.page_reading_days_short else R.plurals.page_reading_days,
        reading.days,
        reading.days,
    )

    is PartReading.Count -> reading.value.toString()

    is PartReading.Option -> labels.option(reading.label)

    is PartReading.Condition -> stringResource(
        when (reading.condition) {
            PartCondition.Ok -> R.string.page_condition_ok
            PartCondition.Full -> R.string.page_condition_full
            PartCondition.Removed -> R.string.page_condition_removed
            PartCondition.Missing -> R.string.page_condition_missing
            PartCondition.Low -> R.string.page_condition_low
        },
    )
}

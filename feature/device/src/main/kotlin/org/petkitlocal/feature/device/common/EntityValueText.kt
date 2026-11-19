package org.petkitlocal.feature.device.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.device.EntityWrites
import org.petkitlocal.core.domain.home.BusyActivity
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.feature.device.R

/*
 * Entity values as screens show them: numbers with their unit, times and dates as the phone
 * writes them, booleans in words, enum values through ServerLabels.
 */

/** A number of [entity], in the locale, with its unit: `24.5 °C`, `24,5 °C`. */
@Composable
internal fun numberText(value: Double, entity: Entity): String {
    val number = NumberFormat.getNumberInstance(currentLocale()).apply { maximumFractionDigits = MAX_DECIMALS }
        .format(value)
    return entity.unit?.let { "$number $it" } ?: number
}

private const val MAX_DECIMALS = 2

/** A time of day, as the phone writes it (24-hour clock or not). */
@Composable
internal fun timeText(time: LocalTime): String {
    val pattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    return DateTimeFormatter.ofPattern(pattern, currentLocale()).format(time)
}

/** An instant as a short date and time in [zone], the phone's (from the injected clock). */
@Composable
internal fun instantText(instant: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(currentLocale()).format(instant.atZone(zone))

/** An entity's value as a screen shows it, in the app's language; a date-time in [zone]. */
@Composable
internal fun entityValueText(entity: Entity, labels: ServerLabels, zone: ZoneId): String {
    val value = entity.value
    val time = if (entity.component == EntityComponent.Time) EntityWrites.timeOfDay(value) else null
    val display = entity.display
    return when {
        value is EntityValue.None -> stringResource(R.string.device_value_none)

        time != null -> timeText(time)

        display != null -> optionText(display, labels, entity.key)

        value is EntityValue.Bool -> booleanText(entity, value.value)

        value is EntityValue.Number -> numberText(value.value, entity)

        value is EntityValue.Text ->
            timestamp(value.value)?.let { instantText(it, zone) } ?: labels.option(value.value, entity.key)

        else -> (value as EntityValue.Other).json
    }
}

@Composable
private fun booleanText(entity: Entity, on: Boolean): String = stringResource(
    when {
        entity.component == EntityComponent.Switch -> if (on) R.string.device_value_on else R.string.device_value_off
        entity.deviceClass == PROBLEM -> if (on) R.string.device_value_problem else R.string.device_value_ok
        else -> if (on) R.string.device_value_yes else R.string.device_value_no
    },
)

private const val PROBLEM = "problem"

/** An ISO date-time the server sends for "last ..." values, or null for any other text. */
private fun timestamp(text: String): Instant? = if (text.length < MIN_TIMESTAMP_LENGTH || 'T' !in text) {
    null
} else {
    try {
        OffsetDateTime.parse(text).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

/** `2026-11-15T07:30Z`, the shortest offset date-time. */
private const val MIN_TIMESTAMP_LENGTH = 17

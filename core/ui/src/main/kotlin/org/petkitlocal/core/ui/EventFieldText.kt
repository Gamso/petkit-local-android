package org.petkitlocal.core.ui

import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/*
 * An event field's text as the phone's reader expects it. The server renders `EventField.text` in
 * English for its own panel (`events/decode.py`): a time as `2026-11-15 10:03:12 +0100` in the
 * server's time zone, a quantity with a dot for decimals (`4.21 kg`), and a few words (`yes`,
 * `completed`). Those three shapes are rewritten in the phone's language, zone and number format;
 * anything else is shown as the server wrote it.
 */

private val SERVER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT)
private val TIME_SHAPE = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} [+-]\d{4}$""")
private val QUANTITY = Regex("""^(-?\d+(?:\.(\d+))?)( ?)(kg|g|s|%)$""")

/** [text] of a decoded event field, in [locale], times shown in [zone]. */
fun localizeEventFieldText(text: String, labels: ServerLabels, locale: Locale, zone: ZoneId): String {
    val trimmed = text.trim()
    if (TIME_SHAPE.matches(trimmed)) {
        val time = try {
            OffsetDateTime.parse(trimmed, SERVER_TIME)
        } catch (_: DateTimeParseException) {
            null
        }
        if (time != null) {
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale)
                .format(time.atZoneSameInstant(zone))
        }
    }
    QUANTITY.matchEntire(trimmed)?.let { match ->
        val number = match.groupValues[1]
        val decimals = match.groupValues[2]
        val space = match.groupValues[3]
        val unit = match.groupValues[4]
        val format = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals.length
            maximumFractionDigits = decimals.length
            isGroupingUsed = false
        }
        // French writes a space before % as before any unit.
        val gap = if (unit == "%" && locale.language == Locale.FRENCH.language) "\u202F" else space
        return format.format(number.toDouble()) + gap + unit
    }
    return labels.eventFieldValue(trimmed)
}

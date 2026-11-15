package org.petkitlocal.core.ui

import java.text.NumberFormat
import java.util.Locale

/*
 * Number formatting for screens. Units follow the locale (docs/DESIGN.md, Copy): "4,3 kg" in
 * French, "4.3 kg" in English. The server sends grams and seconds (api/openapi.yaml).
 */

private const val GRAMS_PER_KG = 1000.0
private const val SECONDS_PER_MINUTE = 60

/** A weight in grams, shown in kilograms with one decimal: `4.3 kg`, `4,3 kg`. */
fun formatWeightKg(grams: Double, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return "${format.format(grams / GRAMS_PER_KG)} kg"
}

/** A duration in seconds as `m:ss` (SPEC.md §7), e.g. `1:05` for 65 s. Negative is shown as 0:00. */
fun formatDurationMinSec(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, total / SECONDS_PER_MINUTE, total % SECONDS_PER_MINUTE)
}

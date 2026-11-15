package org.petkitlocal.feature.pets

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Today", "Yesterday", or the day as the locale writes it in short ("Nov 12", "12 nov."). */
@Composable
internal fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale): String = when (day) {
    today -> stringResource(R.string.pet_today)
    today.minusDays(1) -> stringResource(R.string.pet_yesterday)
    else -> shortDate(day, locale)
}

/** A day and month as the locale writes them in short: "Nov 12", "12 nov.". */
internal fun shortDate(day: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(day)

/** A day and month as the locale writes them in full, inside a sentence: "November 9", "9 novembre". */
internal fun longDate(day: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale).format(day)

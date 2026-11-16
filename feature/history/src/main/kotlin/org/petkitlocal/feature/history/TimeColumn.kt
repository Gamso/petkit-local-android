package org.petkitlocal.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * The time column's width: the widest short time of day the locale writes ("12:59 PM" in English,
 * "23:59" in French), measured at the user's font scale, so a time never wraps ("10:20 A / M") and
 * every card's text starts at the same place. Never narrower than [MIN_TIME_WIDTH].
 */
@Composable
internal fun rememberTimeColumnWidth(locale: Locale): Dp {
    val measurer = rememberTextMeasurer()
    val style = PkTheme.typography.labelLarge
    val density = LocalDensity.current
    return remember(locale, style, density) {
        val formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val widest = WIDEST_TIMES.maxOf { measurer.measure(formatter.format(it), style, softWrap = false).size.width }
        maxOf(with(density) { widest.toDp() } + TIME_GAP, MIN_TIME_WIDTH)
    }
}

/** Times whose digits and day-period markers are the widest a locale writes. */
private val WIDEST_TIMES = listOf(LocalTime.of(12, 58), LocalTime.of(23, 58), LocalTime.of(10, 48))
private val MIN_TIME_WIDTH = 56.dp
private val TIME_GAP = 6.dp

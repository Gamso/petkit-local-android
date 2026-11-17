package org.petkitlocal.feature.device.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import kotlin.math.abs
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.feature.device.R

/** Which way a figure went since yesterday. Neither is good or bad: its arrow stays neutral. */
enum class DayTrend { Up, Down, Same }

/**
 * Today's figure against yesterday's: the [trend] its arrow shows, the amount as shown next to it
 * ("2 since yesterday", "0:25 since yesterday", or "Same as yesterday" with no arrow), and the
 * same in words for TalkBack ("2 more than yesterday"), which cannot read an arrow.
 */
@Immutable
data class DayChangeText(val trend: DayTrend, val shown: String, val spoken: String)

/**
 * Words for today against yesterday, under the figures of the Today cards. Each answers null when
 * yesterday has nothing to compare with: the tile then says nothing rather than a guess.
 */
internal class DayChangeFormatter(private val resources: Resources) {

    /** A count: visits, drinks. */
    fun count(today: Int, yesterday: Int?): DayChangeText? = change(
        difference = yesterday?.let { today - it } ?: return null,
        amount = Int::toString,
        more = { resources.getQuantityString(R.plurals.today_more_than_yesterday, it, it) },
        less = { resources.getQuantityString(R.plurals.today_fewer_than_yesterday, it, it) },
    )

    /** A duration in seconds, shown "0:25" and read "25 seconds". */
    fun duration(todaySec: Int, yesterdaySec: Int?): DayChangeText? = change(
        difference = yesterdaySec?.let { todaySec - it } ?: return null,
        amount = ::formatDurationMinSec,
        more = { resources.getString(R.string.today_longer_than_yesterday, spokenDuration(it)) },
        less = { resources.getString(R.string.today_shorter_than_yesterday, spokenDuration(it)) },
    )

    private fun change(
        difference: Int,
        amount: (Int) -> String,
        more: (Int) -> String,
        less: (Int) -> String,
    ): DayChangeText {
        if (difference == 0) {
            val same = resources.getString(R.string.today_same_as_yesterday)
            return DayChangeText(DayTrend.Same, same, same)
        }
        val size = abs(difference)
        return DayChangeText(
            trend = if (difference > 0) DayTrend.Up else DayTrend.Down,
            shown = resources.getString(R.string.today_since_yesterday, amount(size)),
            spoken = if (difference > 0) more(size) else less(size),
        )
    }

    /** A duration in words, for TalkBack: "1 minute 5 seconds", "2 minutes", "25 seconds". */
    fun spokenDuration(seconds: Int): String {
        val total = seconds.coerceAtLeast(0)
        val minutes = total / SECONDS_PER_MINUTE
        val rest = total % SECONDS_PER_MINUTE
        val parts = buildList {
            if (minutes > 0) add(resources.getQuantityString(R.plurals.today_minutes, minutes, minutes))
            if (rest > 0 || minutes == 0) add(resources.getQuantityString(R.plurals.today_seconds, rest, rest))
        }
        return parts.joinToString(" ")
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}

/** The formatter in the phone's language, following a language switch. */
@Composable
internal fun rememberDayChangeFormatter(): DayChangeFormatter {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { DayChangeFormatter(resources) }
}

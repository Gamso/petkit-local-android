package org.petkitlocal.feature.device.feeder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import java.time.ZoneId
import org.petkitlocal.core.designsystem.component.PkFilterChip
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.feeder.FeederDay
import org.petkitlocal.core.domain.feeder.FeederDayFilter
import org.petkitlocal.core.domain.feeder.FeederDayRow
import org.petkitlocal.core.domain.feeder.FeederEating
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.ActivityCard
import org.petkitlocal.feature.device.common.ActivityRowExtras

/**
 * The feeder's day (SPEC.md §5.6, Today): the device's activity card, named Today, over the whole
 * day rather than its last rows, with counted chips (All, Meals, and At the camera on a camera
 * feeder), each meal's portions per hopper in the hopper's colour, and a clip's thumbnail where the
 * event has one. On a camera feeder, what its camera saw the pets eat ([eating], #68) goes between
 * the title and the chips: the card is already the day's Today, which a second card would repeat,
 * and its figures sit over the rows they count.
 */
@Composable
internal fun FeederDayCard(
    day: FeederDay,
    filter: FeederDayFilter,
    zone: ZoneId,
    onFilter: (FeederDayFilter) -> Unit,
    onOpenClip: (MediaFile) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    eating: FeederEating? = null,
    petFilter: Long? = null,
    onPetFilter: (Long?) -> Unit = {},
) {
    val rows = day.rows(filter)
    val byId = rows.associateBy { it.id }
    ActivityCard(
        entries = rows.map { it.entry },
        zone = zone,
        onOpenHistory = onOpenHistory,
        modifier = modifier.testTag(FeederTestTags.DAY),
        title = stringResource(R.string.feeder_day_title),
        maxRows = Int.MAX_VALUE,
        onOpenClip = onOpenClip,
        extras = { entry -> byId[entry.item.id]?.let { rowExtras(it) } },
        header = {
            eating?.let { EatingSection(it, petFilter, onPetFilter) }
            DayFilterChips(day, filter, onFilter)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun DayFilterChips(day: FeederDay, selected: FeederDayFilter, onFilter: (FeederDayFilter) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = Modifier.padding(top = PkTheme.spacing.s),
    ) {
        day.filters.forEach { filter ->
            val name = stringResource(
                when (filter) {
                    FeederDayFilter.All -> R.string.feeder_day_all
                    FeederDayFilter.Meals -> R.string.feeder_day_meals
                    FeederDayFilter.Camera -> R.string.feeder_day_camera
                },
            )
            PkFilterChip(
                stringResource(R.string.feeder_day_chip, name, day.count(filter)),
                selected = filter == selected,
                onClick = { onFilter(filter) },
                modifier = Modifier.testTag(FeederTestTags.dayFilter(filter)),
            )
        }
    }
}

/** A meal's portions under its sentence, and the clip of any row that has one. */
@Composable
private fun rowExtras(row: FeederDayRow): ActivityRowExtras? {
    val portions = row.portions?.takeIf { it.total > 0 }
    if (portions == null && row.clip == null) return null
    return ActivityRowExtras(
        details = portions?.let { servedText(it) },
        spokenDetails = portions?.let { spokenServed(it) },
        clip = row.clip,
    )
}

/**
 * "2 portions", and on a Dual-Hopper the hoppers that gave some, each in its colour on the
 * illustration: "2 portions · Hopper 1 ×1 · Hopper 2 ×1".
 */
@Composable
private fun servedText(portions: Portions): AnnotatedString {
    val total = pluralStringResource(R.plurals.feeder_portions, portions.total, portions.total)
    val hoppers = hopperCounts(portions).map { (hopper, count) ->
        hopper to stringResource(R.string.feeder_day_hopper, stringResource(R.string.feeder_hopper, hopper), count)
    }
    val colors = PkTheme.colors
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(total) }
        hoppers.forEach { (hopper, text) ->
            append(SEPARATOR)
            // A hopper's count stays on one line with its name: the line breaks between hoppers.
            withStyle(SpanStyle(color = colors.hopper(hopper), fontWeight = FontWeight.SemiBold)) {
                append(text.replace(' ', NO_BREAK))
            }
        }
    }
}

/** What TalkBack reads: "2 portions, 1 from hopper 1, 1 from hopper 2". */
@Composable
private fun spokenServed(portions: Portions): String {
    val total = pluralStringResource(R.plurals.feeder_portions, portions.total, portions.total)
    val hoppers = hopperCounts(portions).map { (hopper, count) ->
        pluralStringResource(R.plurals.feeder_day_hopper_spoken, count, count, hopper)
    }
    return (listOf(total) + hoppers).joinToString(", ")
}

/** The hoppers that gave something, with their count; none on a single hopper. */
private fun hopperCounts(portions: Portions): List<Pair<Int, Int>> = when (portions) {
    is Portions.Single -> emptyList()
    is Portions.Dual -> listOf(1 to portions.hopper1, 2 to portions.hopper2).filter { (_, count) -> count > 0 }
}

private const val SEPARATOR = " · "
private const val NO_BREAK = '\u00A0'

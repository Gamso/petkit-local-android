package org.petkitlocal.feature.device.feeder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.feeder.FeederEating
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DayChart
import org.petkitlocal.feature.device.common.DayDot
import org.petkitlocal.feature.device.common.StatCompareTile
import org.petkitlocal.feature.device.common.UntimedWords
import org.petkitlocal.feature.device.common.rememberDayChangeFormatter

/**
 * What a camera feeder saw its pets eat (#68), at the top of its Today card: the meals eaten and
 * the average meal, each against yesterday as on the litter box (a day with none yesterday has
 * nothing to compare with), then the day's chart, one stem per meal eaten in its pet's colour,
 * with its pet filter. Meals eaten with no duration keep their count, have no average when none
 * of them has one, and lie on the chart's axis, as a fountain's drinks do.
 */
@Composable
internal fun EatingSection(
    eating: FeederEating,
    petFilter: Long?,
    onPetFilter: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val changes = rememberDayChangeFormatter()
    Column(modifier.testTag(FeederTestTags.EATING)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier
                .padding(top = PkTheme.spacing.s)
                .height(IntrinsicSize.Min),
        ) {
            StatCompareTile(
                label = stringResource(R.string.feeder_eaten),
                figure = eating.today.meals.toString(),
                change = changes.count(eating.today.meals, eating.yesterday.meals.takeIf { it > 0 }),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (eating.durationsKnown) {
                val average = eating.today.averageDurationSec
                StatCompareTile(
                    label = stringResource(R.string.feeder_average_meal),
                    figure = average?.let(::formatDurationMinSec) ?: stringResource(R.string.litter_no_average),
                    change = average?.let { changes.duration(it, eating.yesterday.averageDurationSec) },
                    spokenFigure = average?.let(changes::spokenDuration)
                        ?: stringResource(R.string.litter_no_average),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        DayChart(
            title = stringResource(R.string.feeder_eating_chart_title),
            emptyText = stringResource(R.string.feeder_eating_chart_empty),
            description = R.plurals.feeder_eating_chart_description,
            untimed = UntimedWords(R.plurals.feeder_eating_chart_untimed, R.string.feeder_eating_chart_no_durations),
            dots = eating.episodes.map { DayDot(it.minuteOfDay, it.durationSec, it.petId) },
            pets = eating.chartPets,
            colorIndexOf = eating::colorIndexOf,
            petFilter = petFilter,
            onPetFilter = onPetFilter,
            chartTag = FeederTestTags.EATING_CHART,
            modifier = Modifier.padding(top = PkTheme.spacing.m),
            minScaleMinutes = 1,
            titleStyle = PkTheme.typography.labelLarge,
        )
        // The chips below filter the day's list, not the chart: a line keeps the two apart.
        HorizontalDivider(color = PkTheme.colors.outline, modifier = Modifier.padding(top = PkTheme.spacing.s))
    }
}

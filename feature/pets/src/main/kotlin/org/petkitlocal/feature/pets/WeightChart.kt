package org.petkitlocal.feature.pets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkFilterChip
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.pets.WeightRange
import org.petkitlocal.core.domain.pets.WeightTrend
import org.petkitlocal.core.ui.formatWeightKg

/**
 * The weight card (SPEC.md §5.5): 30 days or 6 months, then the chart of the pet's weighed visits
 * with what a point stands for, or why there is none. Nothing is drawn where there is no data.
 */
@Suppress("LongParameterList") // The trend, how its reading went, and the two things to do.
@Composable
internal fun WeightCard(
    trend: WeightTrend,
    colorIndex: Int,
    loading: Boolean,
    error: DomainError?,
    today: LocalDate,
    onRange: (WeightRange) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = PkTheme.colors.onSurfaceMuted
    val locale = LocalConfiguration.current.locales[0]
    PkCard(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.pet_weight_chart),
            style = PkTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        RangeChips(trend.range, onRange, Modifier.padding(top = PkTheme.spacing.s))
        val note = @Composable { text: String ->
            Text(
                text,
                style = PkTheme.typography.bodyMedium,
                color = muted,
                modifier = Modifier.padding(top = PkTheme.spacing.s),
            )
        }
        when {
            trend.points.isNotEmpty() -> {
                WeightChart(trend, colorIndex, today, Modifier.padding(top = PkTheme.spacing.m))
                note(
                    stringResource(
                        if (trend.range ==
                            WeightRange.Month
                        ) {
                            R.string.pet_chart_per_day
                        } else {
                            R.string.pet_chart_per_week
                        },
                    ),
                )
                trend.dataSince?.let { note(stringResource(R.string.pet_chart_since, longDate(it, locale))) }
            }

            loading -> note(stringResource(R.string.pet_chart_loading))

            error != null -> {
                note(stringResource(R.string.pet_chart_failed))
                PkButton(stringResource(R.string.pet_retry), onClick = onRetry, style = PkButtonStyle.Text)
            }

            else -> note(
                stringResource(
                    if (trend.range == WeightRange.Month) {
                        R.string.pet_chart_empty_month
                    } else {
                        R.string.pet_chart_empty_half_year
                    },
                ),
            )
        }
    }
}

/** 30 days or 6 months, single-select. */
@Composable
private fun RangeChips(range: WeightRange, onRange: (WeightRange) -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs), modifier = modifier) {
        WeightRange.entries.forEach { option ->
            val label = when (option) {
                WeightRange.Month -> R.string.pet_range_month
                WeightRange.HalfYear -> R.string.pet_range_half_year
            }
            PkFilterChip(stringResource(label), range == option, onClick = { onRange(option) })
        }
    }
}

/**
 * The trend as a line in the pet's colour, a dot per point, across the range's buckets (the
 * oldest on the left, today on the right), against kilograms rounded to 100 g. Two points are
 * joined only when their buckets are next to each other: a gap stays a gap. TalkBack reads the
 * number of points and the lightest and heaviest.
 */
@Composable
private fun WeightChart(trend: WeightTrend, colorIndex: Int, today: LocalDate, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val lightest = trend.points.minOf { it.medianG }
    val heaviest = trend.points.maxOf { it.medianG }
    val (bottom, top) = axis(lightest, heaviest)
    val spoken = pluralStringResource(
        R.plurals.pet_chart_description,
        trend.points.size,
        trend.points.size,
        formatWeightKg(lightest, locale),
        formatWeightKg(heaviest, locale),
    )
    val line = colors.pet(colorIndex)
    Column(
        modifier
            .testTag(PetsTestTags.CHART)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        AxisLabel(formatWeightKg(top, locale))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT),
        ) {
            val radius = DOT_RADIUS.toPx()
            val plotTop = radius
            val plotBottom = size.height - radius
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = plotTop + (plotBottom - plotTop) * fraction
                drawLine(colors.outline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
            }
            val bucketWidth = size.width / trend.range.buckets
            val offsets = trend.points.map { point ->
                val x = bucketWidth * (point.index + 0.5f)
                val fraction = ((point.medianG - bottom) / (top - bottom)).toFloat()
                point.index to Offset(x, plotBottom - (plotBottom - plotTop) * fraction)
            }
            val path = Path()
            offsets.forEachIndexed { i, (index, offset) ->
                val joined = i > 0 && offsets[i - 1].first == index - 1
                if (joined) path.lineTo(offset.x, offset.y) else path.moveTo(offset.x, offset.y)
            }
            drawPath(
                path,
                line,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            offsets.forEach { (_, offset) -> drawDot(offset, radius, line, colors.surface) }
        }
        AxisLabel(formatWeightKg(bottom, locale))
        DayAxis(trend, today)
    }
}

/** The range's first day, its middle, and today. */
@Composable
private fun DayAxis(trend: WeightTrend, today: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        AxisLabel(shortDate(trend.firstDay, locale))
        AxisLabel(shortDate(trend.firstDay.plusDays(trend.range.days / 2L), locale))
        AxisLabel(if (trend.lastDay == today) stringResource(R.string.pet_today) else shortDate(trend.lastDay, locale))
    }
}

private fun DrawScope.drawDot(at: Offset, radius: Float, fill: Color, ring: Color) {
    drawCircle(ring, radius + 1.5.dp.toPx(), at)
    drawCircle(fill, radius, at)
}

@Composable
private fun AxisLabel(text: String) {
    Text(text, style = PkTheme.typography.labelSmall, color = PkTheme.colors.onSurfaceMuted)
}

/**
 * The weight axis, grams: [lightest] and [heaviest] with some room, rounded out to 100 g so its
 * labels are exact, and never less than [MIN_SPAN_G], so a steady weight draws a flat line.
 */
internal fun axis(lightest: Double, heaviest: Double): Pair<Double, Double> {
    val middle = (lightest + heaviest) / 2
    val half = maxOf((heaviest - lightest) / 2 + MARGIN_G, MIN_SPAN_G / 2)
    val bottom = floor((middle - half) / STEP_G) * STEP_G
    val top = ceil((middle + half) / STEP_G) * STEP_G
    return bottom to top
}

private val CHART_HEIGHT = 140.dp
private val DOT_RADIUS = 4.dp
private const val STEP_G = 100.0
private const val MARGIN_G = 50.0
private const val MIN_SPAN_G = 400.0

package org.petkitlocal.feature.device.common

import androidx.annotation.PluralsRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkFilterChip
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.feature.device.R

/** One dot of a 24-hour chart: a litter box visit or a drink. */
data class DayDot(
    /** Local minute of the day, 0 to 1439. */
    val minuteOfDay: Int,
    val durationSec: Int?,
    val petId: Long?,
)

/**
 * The 24-hour chart card (docs/DESIGN.md, DayScatterChart): one dot per visit or drink, at its
 * time across and its duration up, in its pet's colour, with a single-select filter on [pets]
 * (those with a dot). TalkBack reads the count, worded by [description]; the page says the same
 * in words elsewhere, so nothing rests on colour.
 *
 * @param colorIndexOf a pet's place in the household's order, which picks its colour.
 * @param minScaleMinutes the least the duration axis goes up to: five minutes for a visit, one
 *   for a drink, which lasts seconds.
 */
@Composable
internal fun DayChartCard(
    title: String,
    emptyText: String,
    @PluralsRes description: Int,
    dots: List<DayDot>,
    pets: List<Pet>,
    colorIndexOf: (Long?) -> Int?,
    petFilter: Long?,
    onPetFilter: (Long?) -> Unit,
    chartTag: String,
    modifier: Modifier = Modifier,
    minScaleMinutes: Int = MIN_SCALE_MINUTES,
) {
    PkCard(modifier.fillMaxWidth()) {
        Text(title, style = PkTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        if (pets.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
                modifier = Modifier.padding(top = PkTheme.spacing.s),
            ) {
                item(key = "all") {
                    PkFilterChip(stringResource(R.string.litter_chart_all), petFilter == null, onClick = {
                        onPetFilter(null)
                    })
                }
                items(pets, key = { it.id }) { pet ->
                    PetFilterChip(
                        name = pet.name,
                        color = PkTheme.colors.pet(colorIndexOf(pet.id) ?: 0),
                        selected = petFilter == pet.id,
                        onClick = { onPetFilter(pet.id) },
                    )
                }
            }
        }
        if (dots.isEmpty()) {
            Text(
                emptyText,
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            )
        }
        DayScatterChart(
            dots,
            colorIndexOf,
            petFilter,
            description,
            chartTag,
            minScaleMinutes,
            Modifier.padding(top = PkTheme.spacing.s),
        )
    }
}

@Suppress("LongParameterList") // The dots, how to colour and filter them, and their words.
@Composable
private fun DayScatterChart(
    dots: List<DayDot>,
    colorIndexOf: (Long?) -> Int?,
    petFilter: Long?,
    @PluralsRes description: Int,
    chartTag: String,
    minScaleMinutes: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    val shown = dots.filter { petFilter == null || it.petId == petFilter }
    val spoken = pluralStringResource(description, shown.size, shown.size)
    // The duration axis: whole minutes, at least the minimum, enough for the longest dot.
    val topMinutes = max(minScaleMinutes, ceil((dots.maxOfOrNull { it.durationSec ?: 0 } ?: 0) / 60.0).toInt())
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
        Row {
            Text(
                "$topMinutes ${stringResource(R.string.litter_chart_minutes)}",
                style = PkTheme.typography.labelSmall,
                color = colors.onSurfaceMuted,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .testTag(chartTag),
        ) {
            val radius = DOT_RADIUS.toPx()
            val plotTop = radius
            val plotBottom = size.height - radius
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            listOf(0f, 0.5f).forEach { fraction ->
                val y = plotTop + (plotBottom - plotTop) * fraction
                drawLine(colors.outline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
            }
            drawLine(colors.outline, Offset(0f, plotBottom), Offset(size.width, plotBottom), 1.dp.toPx())
            shown.forEach { dot ->
                val x = radius + (size.width - 2 * radius) * dot.minuteOfDay / MINUTES_PER_DAY
                val minutes = (dot.durationSec ?: 0) / 60f
                val y = plotBottom - (plotBottom - plotTop) * (minutes / topMinutes).coerceIn(0f, 1f)
                val color = colorIndexOf(dot.petId)?.let(colors::pet) ?: colors.onSurfaceMuted
                drawCircle(colors.surface, radius + 1.5.dp.toPx(), Offset(x, y))
                drawCircle(color, radius, Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            AXIS_HOURS.forEach { hour ->
                Text(
                    stringResource(R.string.litter_hour, hour),
                    style = PkTheme.typography.labelSmall,
                    color = colors.onSurfaceMuted,
                )
            }
        }
    }
}

/**
 * A pet of the filter: its colour as on the chart (the legend), its name, and a check mark when
 * selected, so the choice never rests on colour alone.
 */
@Composable
private fun PetFilterChip(name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val colors = PkTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clip(PkTheme.shapes.chip)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .background(if (selected) colors.accentContainer else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.outline, PkTheme.shapes.chip)
            .padding(horizontal = PkTheme.spacing.m),
    ) {
        if (selected) {
            Icon(
                PkIcons.Check,
                contentDescription = null,
                tint = colors.onAccentContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(PkTheme.spacing.xxs))
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(PkTheme.spacing.xs))
        Text(
            name,
            style = PkTheme.typography.labelLarge,
            color = if (selected) colors.onAccentContainer else colors.onSurface,
        )
    }
}

private val CHART_HEIGHT = 120.dp
private val DOT_RADIUS = 6.dp
private const val MIN_SCALE_MINUTES = 5
private val AXIS_HOURS = listOf(0, 6, 12, 18, 24)
private const val MINUTES_PER_DAY = 24 * 60

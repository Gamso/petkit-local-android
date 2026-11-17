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
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkFilterChip
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.feature.device.R

/** One event of a 24-hour chart: a litter box visit or a drink. */
data class DayDot(
    /** Local minute of the day, 0 to 1439. */
    val minuteOfDay: Int,
    val durationSec: Int?,
    val petId: Long?,
)

/**
 * The 24-hour chart card (docs/DESIGN.md, DayStemChart): one stem per visit or drink, standing
 * from the axis at its time to its duration and capped with a dot, in its pet's colour (an
 * unrecognised pet's in neutral), with a single-select filter on [pets] (those with an event).
 * TalkBack reads the count, worded by [description]; the page says the same in words elsewhere,
 * so nothing rests on colour.
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
        DayStemChart(
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
private fun DayStemChart(
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
    val topMinutes = DayChartLayout.topMinutes(dots, minScaleMinutes)
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
            val frame = StemFrame(
                width = size.width,
                top = radius,
                bottom = size.height - STEM_WIDTH.toPx() / 2,
                inset = radius,
                minGap = MIN_GAP.toPx(),
            )
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            listOf(0f, 0.5f).forEach { fraction ->
                val y = frame.top + (frame.bottom - frame.top) * fraction
                drawLine(colors.outline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
            }
            drawLine(colors.outline, Offset(0f, frame.bottom), Offset(size.width, frame.bottom), 1.dp.toPx())
            val stems = DayChartLayout.stems(shown, topMinutes, frame)
            val colorOf = { stem: Stem -> colorIndexOf(stem.petId)?.let(colors::pet) ?: colors.onSurfaceMuted }
            // Every stem first, then every dot, each ringed in the surface colour: a dot stays
            // whole over a neighbour's stem, and two dots that touch stay two. The ring's cut in
            // the dot's own stem is drawn again, so stem and dot stay one mark.
            val ring = radius + DOT_RING.toPx()
            stems.forEach { stem ->
                drawLine(colorOf(stem), Offset(stem.x, frame.bottom), Offset(stem.x, stem.top), STEM_WIDTH.toPx())
            }
            stems.forEach { stem ->
                val color = colorOf(stem)
                val centre = Offset(stem.x, stem.top)
                drawCircle(colors.surface, ring, centre)
                val joint = minOf(stem.top + ring + 1.dp.toPx(), frame.bottom)
                drawLine(color, centre, Offset(stem.x, joint), STEM_WIDTH.toPx())
                drawCircle(color, radius, centre)
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
private val DOT_RADIUS = 5.dp
private val DOT_RING = 1.5.dp
private val STEM_WIDTH = 3.dp

/** Two stems closer than this are spread apart: their own width and as much again between them. */
private val MIN_GAP = 6.dp
private const val MIN_SCALE_MINUTES = 5
private val AXIS_HOURS = listOf(0, 6, 12, 18, 24)

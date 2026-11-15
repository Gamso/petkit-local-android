package org.petkitlocal.feature.device.litter

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkFilterChip
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.litter.LitterBoxPage
import org.petkitlocal.feature.device.R

/**
 * The 24-hour chart (docs/DESIGN.md, DayScatterChart): one dot per visit, at its time across and
 * its duration up, in its pet's colour, with a single-select pet filter. TalkBack reads the count;
 * the activity list below says the same visits in words, so nothing rests on colour.
 */
@Composable
internal fun VisitsChartCard(
    page: LitterBoxPage,
    petFilter: Long?,
    onPetFilter: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chartPets = page.chartPets
    PkCard(modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.litter_chart_title))
        if (chartPets.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
                modifier = Modifier.padding(top = PkTheme.spacing.s),
            ) {
                item(key = "all") {
                    PkFilterChip(stringResource(R.string.litter_chart_all), petFilter == null, onClick = {
                        onPetFilter(null)
                    })
                }
                items(chartPets, key = { it.id }) { pet ->
                    PetFilterChip(
                        name = pet.name,
                        color = PkTheme.colors.pet(page.colorIndexOf(pet.id) ?: 0),
                        selected = petFilter == pet.id,
                        onClick = { onPetFilter(pet.id) },
                    )
                }
            }
        }
        if (page.visits.isEmpty()) {
            Text(
                stringResource(R.string.litter_chart_empty),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            )
        }
        DayScatterChart(page, petFilter, Modifier.padding(top = PkTheme.spacing.s))
    }
}

@Composable
private fun DayScatterChart(page: LitterBoxPage, petFilter: Long?, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val shown = page.visits.filter { petFilter == null || it.petId == petFilter }
    val description = pluralStringResource(R.plurals.litter_chart_description, shown.size, shown.size)
    // The duration axis: whole minutes, at least five, enough for the longest visit.
    val topMinutes = max(MIN_SCALE_MINUTES, ceil((page.visits.maxOfOrNull { it.durationSec ?: 0 } ?: 0) / 60.0).toInt())
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
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
                .testTag(LitterTestTags.CHART),
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
            shown.forEach { visit ->
                val x = radius + (size.width - 2 * radius) * visit.minuteOfDay / MINUTES_PER_DAY
                val minutes = (visit.durationSec ?: 0) / 60f
                val y = plotBottom - (plotBottom - plotTop) * (minutes / topMinutes).coerceIn(0f, 1f)
                val color = page.colorIndexOf(visit.petId)?.let(colors::pet) ?: colors.onSurfaceMuted
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

/** Test tags of the litter box page. */
object LitterTestTags {
    const val CHART = "litter:chart"
}

private val CHART_HEIGHT = 120.dp
private val DOT_RADIUS = 6.dp
private const val MIN_SCALE_MINUTES = 5
private val AXIS_HOURS = listOf(0, 6, 12, 18, 24)
private const val MINUTES_PER_DAY = 24 * 60

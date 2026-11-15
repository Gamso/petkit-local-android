package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import kotlin.math.abs
import kotlin.time.toKotlinInstant
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.litter.LitterBoxPage
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.core.ui.formatTimeOfDay
import org.petkitlocal.core.ui.formatWeightKg
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R

/** A card's title. */
@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = PkTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/** Today's visits and average visit, each with the difference from yesterday (SPEC.md §5.3). */
@Composable
internal fun TodayCard(page: LitterBoxPage, modifier: Modifier = Modifier) {
    PkCard(modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.litter_today))
        Row(
            horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier
                .padding(top = PkTheme.spacing.s)
                .height(IntrinsicSize.Min),
        ) {
            StatCompareTile(
                label = stringResource(R.string.litter_visits),
                figure = page.today.visits.toString(),
                comparison = comparison(page.today.visits, page.yesterday.visits.takeIf { it > 0 }) { signed(it) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCompareTile(
                label = stringResource(R.string.litter_average_visit),
                figure = page.today.averageDurationSec?.let(::formatDurationMinSec)
                    ?: stringResource(R.string.litter_no_average),
                comparison = page.today.averageDurationSec?.let { today ->
                    comparison(today, page.yesterday.averageDurationSec) { signedDuration(it) }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/** A big figure and how it compares with yesterday (docs/DESIGN.md, StatCompareTile). */
@Composable
internal fun StatCompareTile(label: String, figure: String, comparison: String?, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
        modifier = modifier
            .clip(PkTheme.shapes.tile)
            .background(PkTheme.colors.surfaceMuted)
            .padding(PkTheme.spacing.s)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = PkTheme.typography.labelLarge, color = PkTheme.colors.onSurfaceMuted)
        Text(figure, style = PkTheme.typography.headlineLarge, color = PkTheme.colors.onSurface, maxLines = 1)
        if (comparison != null) {
            Text(comparison, style = PkTheme.typography.labelMedium, color = PkTheme.colors.onSurfaceMuted)
        }
    }
}

/** "+2 vs yesterday", "Same as yesterday", or "No visit yesterday" when there is nothing to compare. */
@Composable
private fun comparison(today: Int, yesterday: Int?, format: (Int) -> String): String = when {
    yesterday == null -> stringResource(R.string.litter_no_visit_yesterday)
    today == yesterday -> stringResource(R.string.litter_same_as_yesterday)
    else -> stringResource(R.string.litter_vs_yesterday, format(today - yesterday))
}

/** A difference with its sign, the minus a real one: "+2", "−1". */
internal fun signed(value: Int): String = if (value < 0) "$MINUS${abs(value)}" else "+$value"

internal fun signedDuration(seconds: Int): String =
    (if (seconds < 0) MINUS else "+") + formatDurationMinSec(abs(seconds))

private const val MINUS = "−"

/** Today's cards of this box, newest first, and the way to History (SPEC.md §5.3, Activity). */
@Composable
internal fun ActivityCard(page: LitterBoxPage, zone: ZoneId, onOpenHistory: () -> Unit, modifier: Modifier = Modifier) {
    PkCard(modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.litter_activity))
        if (page.activity.isEmpty()) {
            Text(
                stringResource(R.string.litter_activity_empty),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            )
        }
        page.activity.take(MAX_ACTIVITY).forEach { item -> ActivityRow(item, page, zone) }
        PkButton(
            stringResource(R.string.litter_see_history),
            onClick = onOpenHistory,
            style = PkButtonStyle.Text,
            icon = PkIcons.History,
            modifier = Modifier.padding(top = PkTheme.spacing.xs),
        )
    }
}

private const val MAX_ACTIVITY = 6

@Composable
private fun ActivityRow(item: TimelineItem, page: LitterBoxPage, zone: ZoneId) {
    val colors = PkTheme.colors
    val labels = rememberServerLabels()
    val locale = LocalConfiguration.current.locales[0]
    val visit = TimelineCategory.Toileting in item.categories
    val title = if (visit) {
        item.pet?.name ?: stringResource(R.string.litter_unknown_pet)
    } else {
        labels.eventKind(item.eventKind ?: item.kind, item.label)
    }
    val details = buildList {
        if (visit) add(stringResource(R.string.litter_visit))
        item.durationSec?.let { add(formatDurationMinSec(it)) }
        item.weightG?.let { add(formatWeightKg(it.toDouble(), locale)) }
    }.joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PkTheme.spacing.s)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            item.shownAt?.let { formatTimeOfDay(it.toKotlinInstant(), zone, locale) }.orEmpty(),
            style = PkTheme.typography.labelLarge,
            color = colors.onSurfaceMuted,
            modifier = Modifier.width(56.dp),
        )
        ActivityMark(item, page)
        Column(Modifier.padding(start = PkTheme.spacing.s).weight(1f)) {
            Text(title, style = PkTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details.isNotEmpty()) {
                Text(details, style = PkTheme.typography.labelMedium, color = colors.onSurfaceMuted)
            }
        }
    }
}

/** A visit's pet colour, or the symbol of the card's kind. */
@Composable
private fun ActivityMark(item: TimelineItem, page: LitterBoxPage) {
    val colors = PkTheme.colors
    val categories = item.categories
    if (TimelineCategory.Toileting in categories) {
        val color = page.colorIndexOf(item.pet?.id)?.let(colors::pet) ?: colors.onSurfaceMuted
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        }
        return
    }
    val (icon, tint) = when {
        TimelineCategory.Fault in categories || TimelineCategory.HealthAlert in categories ->
            PkIcons.Warning to
                colors.warning

        TimelineCategory.Cleaning in categories -> PkIcons.Clean to colors.onSurfaceMuted

        TimelineCategory.Pet in categories -> PkIcons.Pets to colors.onSurfaceMuted

        else -> PkIcons.History to colors.onSurfaceMuted
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
}

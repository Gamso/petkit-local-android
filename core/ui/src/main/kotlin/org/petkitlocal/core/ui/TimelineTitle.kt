package org.petkitlocal.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem

/**
 * What a History card is about, as History and a camera's recent clips name it: the pet for a
 * visit, otherwise its kind, translated by key, with the pet when there is one.
 */
@Composable
fun timelineTitle(item: TimelineItem, labels: ServerLabels): String {
    val visit = TimelineCategory.Toileting in item.categories
    return if (visit) {
        item.pet?.name ?: stringResource(R.string.timeline_unknown_pet)
    } else {
        val kind = labels.eventKind(item.eventKind ?: item.kind, item.label)
        item.pet?.name?.let { "$kind · $it" } ?: kind
    }
}

/**
 * The symbol of a card's kind and its tint, as History and a device's activity draw a card that is
 * not about a pet: a fault or a health alert in the warning colour, every other kind muted.
 */
@Composable
fun timelineSymbol(categories: Set<TimelineCategory>): Pair<ImageVector, Color> {
    val colors = PkTheme.colors
    return when {
        TimelineCategory.Fault in categories || TimelineCategory.HealthAlert in categories ->
            PkIcons.Warning to
                colors.warning

        TimelineCategory.Cleaning in categories -> PkIcons.Clean to colors.onSurfaceMuted

        TimelineCategory.Feeding in categories -> PkIcons.Feed to colors.onSurfaceMuted

        TimelineCategory.Drinking in categories -> PkIcons.WaterDrop to colors.onSurfaceMuted

        TimelineCategory.Pet in categories -> PkIcons.Pets to colors.onSurfaceMuted

        else -> PkIcons.History to colors.onSurfaceMuted
    }
}

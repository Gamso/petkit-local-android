package org.petkitlocal.feature.device.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * A big figure and how it compares with yesterday (docs/DESIGN.md, StatCompareTile): a neutral
 * arrow and the amount ("↑ 2 since yesterday"), nothing when yesterday has nothing to compare
 * with, or a [note] where the figure has no yesterday at all.
 *
 * TalkBack reads the tile as one sentence of words, "Visits, 4, 2 more than yesterday": an arrow
 * cannot be read, and "0:25" reads better as "25 seconds" ([spokenFigure]).
 */
@Composable
internal fun StatCompareTile(
    label: String,
    figure: String,
    change: DayChangeText?,
    modifier: Modifier = Modifier,
    spokenFigure: String = figure,
    note: String? = null,
) {
    val colors = PkTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
        modifier = modifier
            .clip(PkTheme.shapes.tile)
            .background(colors.surfaceMuted)
            .padding(PkTheme.spacing.s)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(label, spokenFigure, change?.spoken, note).joinToString(", ")
            },
    ) {
        Text(label, style = PkTheme.typography.labelLarge, color = colors.onSurfaceMuted)
        Text(figure, style = PkTheme.typography.headlineLarge, color = colors.onSurface, maxLines = 1)
        if (change != null) DayChangeLine(change)
        if (note != null) Text(note, style = PkTheme.typography.labelMedium, color = colors.onSurfaceMuted)
    }
}

/**
 * The arrow, as tall as the text's line so it follows the font scale, then the amount. Both start
 * at the top, so the arrow stays on the first line when the amount wraps.
 */
@Composable
private fun DayChangeLine(change: DayChangeText) {
    val colors = PkTheme.colors
    val style = PkTheme.typography.labelMedium
    val arrow = when (change.trend) {
        DayTrend.Up -> PkIcons.ArrowUpward
        DayTrend.Down -> PkIcons.ArrowDownward
        DayTrend.Same -> null
    }
    Row(verticalAlignment = Alignment.Top) {
        if (arrow != null) {
            Icon(
                arrow,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(with(LocalDensity.current) { style.lineHeight.toDp() }),
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(change.shown, style = style, color = colors.onSurfaceMuted, modifier = Modifier.weight(1f, fill = false))
    }
}

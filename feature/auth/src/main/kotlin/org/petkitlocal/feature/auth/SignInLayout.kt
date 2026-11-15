package org.petkitlocal.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * The welcome above the form: the app's mark (a paw, in a tile of the accent's tint), its name as
 * the screen's heading, and what it is in one line. The mark is decoration; the name says it all.
 */
@Composable
internal fun AppWelcome(modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs), modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(MARK_TILE)
                .clip(PkTheme.shapes.tile)
                .background(PkTheme.colors.accentContainer),
        ) {
            Icon(
                PkIcons.Pets,
                contentDescription = null,
                tint = PkTheme.colors.onAccentContainer,
                modifier = Modifier.size(MARK_ICON),
            )
        }
        Text(
            stringResource(R.string.auth_app_name),
            style = PkTheme.typography.display,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.auth_tagline),
            style = PkTheme.typography.bodyLarge,
            color = PkTheme.colors.onSurfaceMuted,
        )
    }
}

private val MARK_TILE = 56.dp
private val MARK_ICON = 28.dp

/**
 * [content] placed at [bias] of the height left free (0 at the top, 0.5 in the middle), the whole
 * at least as tall as the incoming minimum (the window, inside a scroll). When [content] is taller,
 * it simply starts at the top and the page scrolls.
 */
@Composable
internal fun BiasedCentre(bias: Float, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val contentHeight = placeables.sumOf { it.height }
        val height = maxOf(constraints.minHeight, contentHeight)
        layout(constraints.maxWidth, height) {
            var y = ((height - contentHeight) * bias).roundToInt()
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

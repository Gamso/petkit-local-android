package org.petkitlocal.feature.device.fountain

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.fountain.FountainModeBar
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.SheetTile

/**
 * The fountain's mode in the collapsed action sheet, in place of the quick actions: one tile per
 * option the server lists, the one the fountain REPORTED filled and in bold (ADR 0002: never the
 * one tapped). A tapped option shows a spinner until the fountain's report carries it; TalkBack
 * says it is waiting. Taps are ignored while a write is on its way ([sending]).
 */
@Composable
internal fun RowScope.FountainModeSelector(
    bar: FountainModeBar,
    pending: String?,
    sending: Boolean,
    onMode: (String) -> Unit,
) {
    val labels = rememberServerLabels()
    Row(
        Modifier
            .weight(bar.options.size.toFloat())
            .selectableGroup()
            .testTag(FountainTestTags.MODE_BAR),
    ) {
        bar.options.forEach { option ->
            ModeTile(
                label = modeLabel(option, labels),
                icon = modeIcon(option),
                selected = option == bar.selected,
                pending = option == pending,
                onClick = { if (!sending) onMode(option) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(FountainTestTags.mode(option)),
            )
        }
    }
}

@Composable
private fun ModeTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    val waiting = stringResource(R.string.fountain_mode_pending)
    SheetTile(
        label = label,
        background = if (selected) colors.accent else colors.surfaceMuted,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        state = waiting.takeIf { pending },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (selected) colors.onAccent else colors.onSurface)
            if (pending) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(44.dp))
            }
        }
    }
}

/** An option's name on its tile: ours for the options petkit-local has, else the server's own. */
@Composable
private fun modeLabel(option: String, labels: ServerLabels): String =
    MODE_LABELS[option]?.let { stringResource(it) } ?: labels.option(option)

private val MODE_LABELS: Map<String, Int> = mapOf(
    "do_not_flow" to R.string.fountain_mode_option_do_not_flow,
    "continuous" to R.string.fountain_mode_option_continuous,
    "intermittent" to R.string.fountain_mode_option_intermittent,
    "motion_activated" to R.string.fountain_mode_option_motion_activated,
    "normal" to R.string.fountain_mode_option_normal,
    "smart" to R.string.fountain_mode_option_smart,
)

/** Smart mode runs then rests, as intermittent does; normal runs all the time, as continuous. */
private fun modeIcon(option: String): ImageVector = when (option) {
    "do_not_flow" -> PkIcons.Pause
    "continuous", "normal" -> PkIcons.Water
    "intermittent", "smart" -> PkIcons.Timelapse
    "motion_activated" -> PkIcons.MotionSensor
    else -> PkIcons.WaterDrop
}

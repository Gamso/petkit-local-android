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
import org.petkitlocal.core.domain.fountain.FountainBatteryMode
import org.petkitlocal.core.domain.fountain.FountainModeBar
import org.petkitlocal.core.domain.fountain.FountainPause
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.SheetTile

/** What the bottom bar's tiles do, one callback per tile kind. */
internal data class FountainBarCallbacks(
    /** An option of the mode select. */
    val onMode: (String) -> Unit = {},
    /** The Pause tile: pauses a running fountain, or runs a paused one again. */
    val onPause: () -> Unit = {},
    /** The battery mode tile: its working and sleep times. */
    val onBatteryMode: () -> Unit = {},
)

/**
 * The fountain's mode in the collapsed action sheet, in place of the quick actions, laid out as
 * PetKit's bar: Pause when the fountain has one, one tile per option the server lists, then the
 * battery mode when the fountain has one. The option the fountain REPORTED is filled and in bold
 * (ADR 0002: never the one tapped); a tapped one shows a spinner until the fountain's report
 * carries it, and TalkBack says it is waiting. Taps on a mode or on Pause are ignored while its
 * write is on its way ([sending], the keys in flight).
 *
 * Nothing here is ever disabled (AGENTS.md): on battery, Continuous and Intermittent stay
 * enabled and a tap is sent, so the server's refusal is what the banner above the bar says. The
 * battery mode is no option: the fountain enters it by itself, so its tile is filled while the
 * fountain reports it, and a tap opens its times.
 */
@Composable
internal fun RowScope.FountainModeSelector(
    bar: FountainModeBar,
    pending: String?,
    sending: Set<String>,
    callbacks: FountainBarCallbacks,
) {
    val labels = rememberServerLabels()
    val tiles = bar.options.size + listOfNotNull(bar.pause, bar.battery).size
    Row(Modifier.weight(tiles.toFloat()).testTag(FountainTestTags.MODE_BAR)) {
        bar.pause?.let { pause ->
            PauseTile(pause, pending = pause.key in sending) {
                if (pause.key !in sending) callbacks.onPause()
            }
        }
        Row(
            Modifier
                .weight(bar.options.size.toFloat())
                .selectableGroup()
                .testTag(FountainTestTags.MODE_OPTIONS),
        ) {
            bar.options.forEach { option ->
                ModeTile(
                    label = modeLabel(bar.key, option, labels),
                    icon = modeIcon(option),
                    highlighted = option == bar.selected,
                    pending = option == pending,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(FountainTestTags.mode(option)),
                    selected = option == bar.selected,
                    onClick = { if (bar.key !in sending) callbacks.onMode(option) },
                )
            }
        }
        bar.battery?.let { BatteryModeTile(it, callbacks.onBatteryMode) }
    }
}

/** PetKit's Pause: a switch, on (filled) while the fountain is paused, whatever its mode. */
@Composable
private fun RowScope.PauseTile(pause: FountainPause, pending: Boolean, onClick: () -> Unit) {
    val paused = pause.paused == true
    ModeTile(
        label = stringResource(R.string.fountain_mode_pause),
        icon = PkIcons.Pause,
        highlighted = paused,
        pending = pending,
        modifier = Modifier
            .weight(1f)
            .testTag(FountainTestTags.PAUSE),
        toggled = paused,
        onClick = onClick,
    )
}

/**
 * The battery mode: a state the fountain enters by itself unplugged, never selected, so a button
 * rather than an option. Filled while the fountain is on battery; a tap opens its times.
 */
@Composable
private fun RowScope.BatteryModeTile(battery: FountainBatteryMode, onClick: () -> Unit) {
    ModeTile(
        label = stringResource(R.string.fountain_mode_battery),
        icon = PkIcons.BatteryFull,
        highlighted = battery.active,
        pending = false,
        modifier = Modifier
            .weight(1f)
            .testTag(FountainTestTags.BATTERY_MODE),
        state = stringResource(
            if (battery.active) R.string.fountain_battery_mode_on else R.string.fountain_battery_mode_off,
        ),
        onClick = onClick,
    )
}

/**
 * One tile of the bar, filled when [highlighted]: an option ([selected] not null), a switch
 * ([toggled] not null) or a button. [pending] shows a spinner and says to TalkBack that it waits.
 */
@Composable
@Suppress("LongParameterList") // A tile's look, its role and what it says.
private fun ModeTile(
    label: String,
    icon: ImageVector,
    highlighted: Boolean,
    pending: Boolean,
    modifier: Modifier,
    selected: Boolean? = null,
    toggled: Boolean? = null,
    state: String? = null,
    onClick: () -> Unit,
) {
    val colors = PkTheme.colors
    val waiting = stringResource(R.string.fountain_mode_pending)
    SheetTile(
        label = label,
        background = if (highlighted) colors.accent else colors.surfaceMuted,
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        toggled = toggled,
        emphasised = highlighted,
        state = if (pending) waiting else state,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = if (highlighted) colors.onAccent else colors.onSurface)
            if (pending) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(44.dp))
            }
        }
    }
}

/**
 * An option's name on its tile: the name its select gives it when that differs (a CTW3's `normal`
 * and `smart` are PetKit's Continuous and Intermittent), ours for the options petkit-local has,
 * else the server's own.
 */
@Composable
private fun modeLabel(key: String, option: String, labels: ServerLabels): String =
    (ENTITY_MODE_LABELS[key]?.get(option) ?: MODE_LABELS[option])?.let { stringResource(it) }
        ?: labels.option(option, key)

/** The CTW3's modes, as PetKit's app names them (Gamso/petkit-local#116): the keys stay. */
private val ENTITY_MODE_LABELS: Map<String, Map<String, Int>> = mapOf(
    CTW3_MODE to mapOf(
        "normal" to R.string.fountain_mode_option_continuous,
        "smart" to R.string.fountain_mode_option_intermittent,
    ),
)

/** The W5's names are PetKit's for that model: Normal and Smart. */
private val MODE_LABELS: Map<String, Int> = mapOf(
    "do_not_flow" to R.string.fountain_mode_option_do_not_flow,
    "continuous" to R.string.fountain_mode_option_continuous,
    "intermittent" to R.string.fountain_mode_option_intermittent,
    "motion_activated" to R.string.fountain_mode_option_motion_activated,
    "normal" to R.string.fountain_mode_option_normal,
    "smart" to R.string.fountain_mode_option_smart,
)

/** A CTW3's mode select (petkit-local's `ha/entities/ble.py`). */
internal const val CTW3_MODE = "ctw3_mode"

/** Smart mode runs then rests, as intermittent does; normal runs all the time, as continuous. */
private fun modeIcon(option: String): ImageVector = when (option) {
    "do_not_flow" -> PkIcons.Pause
    "continuous", "normal" -> PkIcons.Water
    "intermittent", "smart" -> PkIcons.Timelapse
    "motion_activated" -> PkIcons.MotionSensor
    else -> PkIcons.WaterDrop
}

package org.petkitlocal.feature.device.fountain

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.Instant
import org.petkitlocal.core.designsystem.component.PkModeChip
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.devicepage.PartReading
import org.petkitlocal.core.domain.fountain.FountainMode
import org.petkitlocal.core.domain.fountain.FountainModeKind
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.litter.PageTestTags

/**
 * The top of the fountain page, under its header: the battery when the fountain reports its
 * level, then a chip per feature it reports ON (smart mode, do not disturb, child lock), nothing
 * for one that is off. Chips say what is on; they are changed in the settings, behind the gear.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
internal fun FountainStatusChips(battery: Hotspot?, modes: List<FountainMode>, modifier: Modifier = Modifier) {
    val level = (battery?.reading as? PartReading.Percent)?.value
    if (level == null && modes.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (level != null) BatteryPill(level, low = battery.attention)
        modes.forEach { mode ->
            PkModeChip(
                label = stringResource(modeLabel(mode)),
                on = true,
                modifier = Modifier.testTag(PageTestTags.mode(mode.key)),
            )
        }
    }
}

/** A chip's name. The CTW3's mode 2 (`smart`) is Intermittent, as PetKit calls it; the W5's is Smart. */
@StringRes
private fun modeLabel(mode: FountainMode): Int = when (mode.kind) {
    FountainModeKind.Smart ->
        if (mode.key == CTW3_MODE) R.string.fountain_mode_intermittent else R.string.fountain_mode_smart

    FountainModeKind.DoNotDisturb -> R.string.fountain_mode_dnd

    FountainModeKind.ChildLock -> R.string.fountain_mode_lock
}

/** The battery: a symbol filled to its level and the percentage, "low" in words when it is. */
@Composable
private fun BatteryPill(percent: Int, low: Boolean) {
    val colors = PkTheme.colors
    val (container, content) = if (low) {
        colors.warningContainer to colors.onWarningContainer
    } else {
        colors.accentContainer to colors.onAccentContainer
    }
    val spoken = stringResource(if (low) R.string.fountain_battery_low else R.string.fountain_battery, percent)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clip(PkTheme.shapes.chip)
            .background(container)
            .padding(horizontal = PkTheme.spacing.m)
            .testTag(FountainTestTags.BATTERY)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Icon(batteryIcon(percent, low), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(PkTheme.spacing.xxs))
        Text(
            if (low) spoken else stringResource(R.string.page_reading_percent, percent),
            style = PkTheme.typography.labelLarge,
            color = content,
        )
    }
}

private fun batteryIcon(percent: Int, low: Boolean): ImageVector = when {
    low -> PkIcons.BatteryAlert
    percent >= FULL -> PkIcons.BatteryFull
    percent >= HALF -> PkIcons.Battery5Bar
    percent >= QUARTER -> PkIcons.Battery3Bar
    else -> PkIcons.Battery1Bar
}

private const val FULL = 80
private const val HALF = 50
private const val QUARTER = 25

/**
 * A Bluetooth fountain's freshness, "Seen 2 min ago · via YumShare Dual-Hopper (online)": when its
 * last frame was decoded, and whether the device relaying it is online, which a write to it needs.
 * A tap opens that device.
 */
@Composable
internal fun RelayFreshness(
    relay: Device,
    lastSeen: Instant?,
    now: Instant,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    val seen = lastSeen?.let { seenText(SeenAgo.of(it, now)) }
    val via = stringResource(
        when {
            seen == null && relay.online -> R.string.fountain_via_online_alone
            seen == null -> R.string.fountain_via_offline_alone
            relay.online -> R.string.fountain_via_online
            else -> R.string.fountain_via_offline
        },
        relay.modelName,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(PkTheme.shapes.chip)
            .background(colors.surface)
            .clickable(
                onClickLabel = stringResource(R.string.device_relay_open, relay.modelName),
                role = Role.Button,
                onClick = onOpen,
            )
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .padding(horizontal = PkTheme.spacing.m, vertical = PkTheme.spacing.xxs)
            .testTag(DeviceTestTags.RELAY),
    ) {
        // The relay's state, also in words: a dot only repeats them.
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (relay.online) colors.success else colors.onSurfaceMuted),
        )
        Spacer(Modifier.width(PkTheme.spacing.xs))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                listOfNotNull(seen, via).joinToString(" · "),
                style = PkTheme.typography.labelLarge,
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(PkTheme.spacing.xxs))
        Icon(
            PkIcons.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun seenText(ago: SeenAgo): String = when (ago) {
    SeenAgo.JustNow -> stringResource(R.string.fountain_seen_now)
    is SeenAgo.Minutes -> pluralStringResource(R.plurals.fountain_seen_minutes, ago.count, ago.count)
    is SeenAgo.Hours -> pluralStringResource(R.plurals.fountain_seen_hours, ago.count, ago.count)
    is SeenAgo.Days -> pluralStringResource(R.plurals.fountain_seen_days, ago.count, ago.count)
}

/** How long ago a device was last seen, in the largest whole unit: "2 min", "3 h", "2 days". */
internal sealed interface SeenAgo {
    data object JustNow : SeenAgo

    data class Minutes(val count: Int) : SeenAgo

    data class Hours(val count: Int) : SeenAgo

    data class Days(val count: Int) : SeenAgo

    companion object {
        /** [seen] against [now]; a time ahead of the phone's clock (a few seconds of skew) is now. */
        fun of(seen: Instant, now: Instant): SeenAgo {
            val minutes = Duration.between(seen, now).toMinutes()
            return when {
                minutes < 1 -> JustNow
                minutes < MINUTES_PER_HOUR -> Minutes(minutes.toInt())
                minutes < MINUTES_PER_DAY -> Hours((minutes / MINUTES_PER_HOUR).toInt())
                else -> Days((minutes / MINUTES_PER_DAY).toInt())
            }
        }

        private const val MINUTES_PER_HOUR = 60L
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}

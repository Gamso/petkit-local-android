package org.petkitlocal.feature.device.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.ui.ServerLabels

/*
 * A switch the action sheet offers among its buttons (issue #100, the T4's Pura Air light): a tile
 * in the row, a line with a switch behind More. Its state is the device's report.
 */

/**
 * A switch among the actions (the T4's Pura Air light): its tile is lit while [on], as the device
 * reports it, and its name is in bold then; TalkBack hears a switch, on or off. Never disabled:
 * a refusal is said in the banner, with the server's reason.
 */
@Composable
internal fun SwitchTile(
    action: DeviceAction,
    on: Boolean,
    labels: ServerLabels,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    SheetTile(
        label = labels.action(action.key, action.name),
        background = if (on) colors.accentContainer else colors.surfaceMuted,
        onClick = onClick,
        modifier = modifier.testTag(DeviceTestTags.actionSwitch(action.key)),
        toggled = on,
    ) {
        Icon(
            actionIcon(action.key),
            contentDescription = null,
            tint = if (on) colors.onAccentContainer else colors.onSurface,
        )
    }
}

/** A switch among the other actions: its name, and the switch the device's report sets. */
@Composable
internal fun MoreSwitchRow(action: DeviceAction, on: Boolean, labels: ServerLabels, onToggle: () -> Unit) {
    val colors = PkTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clip(PkTheme.shapes.tile)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .padding(horizontal = PkTheme.spacing.xs, vertical = PkTheme.spacing.xxs)
            .testTag(DeviceTestTags.actionSwitch(action.key)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(LIST_ICON_SIZE)
                .clip(PkTheme.shapes.tile)
                .background(if (on) colors.accentContainer else colors.surfaceMuted),
        ) {
            Icon(
                actionIcon(action.key),
                contentDescription = null,
                tint = if (on) colors.onAccentContainer else colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            labels.action(action.key, action.name),
            style = PkTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
        )
    }
}

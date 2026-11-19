package org.petkitlocal.feature.device.fountain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import java.time.ZoneId
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.fountain.FountainBatteryMode
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.settings.EditRow
import org.petkitlocal.feature.device.settings.settingHelp

/**
 * What the battery mode tile opens, as PetKit's battery-mode window: whether the fountain is on
 * battery now, what the mode is, and its working and sleep times, each opening the setting's own
 * editor (the settings screen's, the same rows and ranges).
 */
@Composable
internal fun FountainBatteryModeDialog(
    device: Device,
    battery: FountainBatteryMode,
    zone: ZoneId,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        FountainBatteryModeContent(device, battery, zone, onEdit, onDismiss)
    }
}

/** The dialog's card, outside any window: what screenshot tests render. */
@Composable
internal fun FountainBatteryModeContent(
    device: Device,
    battery: FountainBatteryMode,
    zone: ZoneId,
    onEdit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = rememberServerLabels()
    val colors = PkTheme.colors
    Surface(
        shape = PkTheme.shapes.dialog,
        color = colors.surface,
        contentColor = colors.onSurface,
        modifier = modifier.testTag(FountainTestTags.BATTERY_MODE_DIALOG),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier.padding(PkTheme.spacing.xl),
        ) {
            Text(stringResource(R.string.fountain_mode_battery), style = PkTheme.typography.titleLarge)
            val now = if (battery.active) {
                R.string.fountain_battery_mode_now_on
            } else {
                R.string.fountain_battery_mode_now_off
            }
            Text(
                stringResource(now),
                style = PkTheme.typography.titleMedium,
                color = colors.accent,
            )
            Text(
                stringResource(R.string.fountain_battery_mode_explain),
                style = PkTheme.typography.bodyMedium,
                color = colors.onSurfaceMuted,
            )
            val settings = battery.settings.mapNotNull(device::entity)
            if (settings.isEmpty()) {
                Text(
                    stringResource(R.string.fountain_battery_mode_no_settings),
                    style = PkTheme.typography.bodyMedium,
                    color = colors.onSurfaceMuted,
                )
            }
            settings.forEach { entity ->
                EditRow(entity, settingHelp(entity, device.family), labels, zone, onEdit = { onEdit(entity.key) })
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PkButton(stringResource(R.string.page_close), onClick = onDismiss, style = PkButtonStyle.Text)
            }
        }
    }
}

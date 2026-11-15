package org.petkitlocal.feature.device.litter

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.actionIcon

/**
 * What a hotspot pill opens: the part, what it reports, what it is for, the actions on it (a
 * destructive one is still confirmed), and a contextual way to the device's settings.
 */
@Composable
internal fun HotspotDialog(
    hotspot: Hotspot,
    sending: Set<String>,
    onAction: (DeviceAction) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        HotspotDialogContent(hotspot, sending, onAction, onOpenSettings, onDismiss)
    }
}

/** The dialog's card, outside any window: what screenshot tests render. */
@Composable
internal fun HotspotDialogContent(
    hotspot: Hotspot,
    sending: Set<String>,
    onAction: (DeviceAction) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = rememberServerLabels()
    val colors = PkTheme.colors
    Surface(
        shape = PkTheme.shapes.dialog,
        color = colors.surface,
        contentColor = colors.onSurface,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier.padding(PkTheme.spacing.xl),
        ) {
            Text(hotspotLabel(hotspot, labels), style = PkTheme.typography.titleLarge)
            val reading = readingText(hotspot.reading, labels)
            Text(
                if (hotspot.attention) "$reading · ${stringResource(R.string.page_attention)}" else reading,
                style = PkTheme.typography.titleMedium,
                color = if (hotspot.attention) colors.warning else colors.accent,
            )
            Text(
                stringResource(partExplanation(hotspot.part)),
                style = PkTheme.typography.bodyMedium,
                color = colors.onSurfaceMuted,
            )
            hotspot.actions.forEach { action ->
                PkButton(
                    labels.action(action.key, action.name),
                    onClick = { onAction(action) },
                    style = PkButtonStyle.Secondary,
                    icon = actionIcon(action.key),
                    enabled = action.key !in sending,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PkButton(
                    stringResource(R.string.page_all_settings),
                    onClick = onOpenSettings,
                    style = PkButtonStyle.Text,
                )
                PkButton(stringResource(R.string.page_close), onClick = onDismiss, style = PkButtonStyle.Text)
            }
        }
    }
}

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import java.time.ZoneId
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.actionIcon
import org.petkitlocal.feature.device.common.entityValueText

/**
 * What a hotspot pill opens: the part, what it reports, what it is for, and the actions on it (a
 * destructive one is still confirmed). The way to the settings is the header's gear only. A part that
 * belongs to an [accessory] (a Pura Air's N60) also shows the accessory's other readings and leads
 * to its own screen, where its own buttons and settings are.
 */
@Composable
internal fun HotspotDialog(
    hotspot: Hotspot,
    sending: Set<String>,
    onAction: (DeviceAction) -> Unit,
    onDismiss: () -> Unit,
    accessory: Device? = null,
    onOpenAccessory: () -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        HotspotDialogContent(
            hotspot,
            sending,
            onAction,
            onDismiss,
            accessory = accessory,
            onOpenAccessory = onOpenAccessory,
        )
    }
}

/** The dialog's card, outside any window: what screenshot tests render. */
@Composable
internal fun HotspotDialogContent(
    hotspot: Hotspot,
    sending: Set<String>,
    onAction: (DeviceAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    accessory: Device? = null,
    onOpenAccessory: () -> Unit = {},
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
            accessory?.let { AccessorySection(it, hotspot.entityKey, onOpenAccessory) }
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
                PkButton(stringResource(R.string.page_close), onClick = onDismiss, style = PkButtonStyle.Text)
            }
        }
    }
}

/**
 * An accessory's primary readings besides the one the dialog is about (a Pura Air's battery), as
 * its own screen shows them (offline, it says so instead of last known values passing for news),
 * and the way to that screen.
 */
@Composable
private fun AccessorySection(accessory: Device, shownKey: String, onOpen: () -> Unit) {
    val labels = rememberServerLabels()
    val others = accessory.entities.filter {
        it.isPrimary && it.key != shownKey && it.component == EntityComponent.Sensor
    }
    if (!accessory.online) {
        Text(
            stringResource(R.string.page_accessory_offline, accessory.modelName),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.warning,
        )
    }
    others.forEach { entity: Entity ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                labels.entity(entity.key, entity.name),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Text(entityValueText(entity, labels, ZoneId.systemDefault()), style = PkTheme.typography.bodyMedium)
        }
    }
    OpenAccessoryButton(accessory, Modifier.fillMaxWidth(), onOpen)
}

/** The way to [accessory]'s own page: its N60 pill's dialog, or a camera page, which has no pills. */
@Composable
internal fun OpenAccessoryButton(accessory: Device, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    PkButton(
        stringResource(R.string.page_open_accessory, accessory.modelName),
        onClick = onOpen,
        style = PkButtonStyle.Secondary,
        icon = PkIcons.ChevronRight,
        modifier = modifier.testTag(DeviceTestTags.OPEN_ACCESSORY),
    )
}

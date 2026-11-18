package org.petkitlocal.feature.device.settings

import android.content.ClipData
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.DeviceIdentity
import org.petkitlocal.core.domain.device.DeviceLink
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DeviceSectionTitle
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.actionIcon
import org.petkitlocal.feature.device.litter.hotspotLabel
import org.petkitlocal.feature.device.litter.readingText

/*
 * The top of a device's settings (issue #59): who the device is, then its consumables, before
 * the categories of settings.
 */

/** Who the device is, then its consumables, when it has any. */
internal fun LazyListScope.overview(uiState: DeviceSettingsUiState, actions: DeviceSettingsActions) {
    uiState.identity?.let { identity ->
        item(key = "identity") { IdentityCard(identity, uiState.serialCopied, onCopied = actions.onSerialCopied) }
    }
    if (uiState.consumables.isNotEmpty()) {
        item(key = "title-consumables") { DeviceSectionTitle(stringResource(R.string.device_settings_consumables)) }
        item(key = "consumables") {
            ConsumablesCard(
                uiState.consumables,
                uiState.puraAir,
                sending = uiState.commands.sending,
                onAction = actions.onAction,
            )
        }
    }
}

/**
 * The device's name and whether it is online, how it reaches the server, its firmware and its
 * serial number, which Copy puts on the clipboard. A field the server does not send is left out.
 */
@Composable
internal fun IdentityCard(
    identity: DeviceIdentity,
    copied: Boolean,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PkCard(modifier.fillMaxWidth().testTag(DeviceTestTags.IDENTITY)) {
        Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    identity.name,
                    style = PkTheme.typography.titleMedium,
                    color = PkTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(PkTheme.spacing.xs))
                OnlinePill(identity.online)
            }
            identity.link?.let { IdentityField(stringResource(R.string.device_identity_link), linkText(it)) }
            identity.firmware?.let { IdentityField(stringResource(R.string.device_identity_firmware), it) }
            identity.serial?.let { serial ->
                IdentityField(stringResource(R.string.device_identity_serial), serial) {
                    CopyButton(serial, copied, onCopied)
                }
            }
        }
    }
}

@Composable
private fun linkText(link: DeviceLink): String = when (link) {
    DeviceLink.Http -> stringResource(R.string.device_identity_link_http)

    DeviceLink.Mqtt -> stringResource(R.string.device_identity_link_mqtt)

    is DeviceLink.Bluetooth ->
        link.via
            ?.let { stringResource(R.string.device_identity_link_ble_via, it) }
            ?: stringResource(R.string.device_identity_link_ble)
}

/** "Online" on the accent, or "Offline" muted: a dot and the word, never the colour alone. */
@Composable
private fun OnlinePill(online: Boolean) {
    val colors = PkTheme.colors
    val (container, content) = if (online) {
        colors.accentContainer to colors.onAccentContainer
    } else {
        colors.surfaceMuted to colors.onSurfaceMuted
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(PkTheme.shapes.chip)
            .background(container)
            .padding(horizontal = PkTheme.spacing.s, vertical = PkTheme.spacing.xxs),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(content),
        )
        Spacer(Modifier.width(PkTheme.spacing.xs))
        Text(
            stringResource(if (online) R.string.device_identity_online else R.string.device_status_offline),
            style = PkTheme.typography.labelLarge,
            color = content,
        )
    }
}

/**
 * A label above its value, read together by TalkBack. [trailing] sits at the row's end, or under
 * the value when both do not fit side by side (a serial number at a large font), so the value
 * never breaks mid-word.
 */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun IdentityField(label: String, value: String, trailing: (@Composable () -> Unit)? = null) {
    FlowRow(itemVerticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(label, style = PkTheme.typography.labelMedium, color = PkTheme.colors.onSurfaceMuted)
            Text(value, style = PkTheme.typography.bodyLarge, color = PkTheme.colors.onSurface)
        }
        trailing?.invoke()
    }
}

/**
 * Puts [serial] on the clipboard, then says "Copied" for a moment ([copied], which the ViewModel
 * turns back off), read out by TalkBack as it changes.
 */
@Composable
private fun CopyButton(serial: String, copied: Boolean, onCopied: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.device_identity_serial)
    val action = stringResource(R.string.device_identity_copy_action)
    PkButton(
        text = stringResource(if (copied) R.string.device_identity_copied else R.string.device_identity_copy),
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, serial).toClipEntry())
                onCopied()
            }
        },
        style = PkButtonStyle.Text,
        icon = if (copied) PkIcons.Check else PkIcons.ContentCopy,
        modifier = Modifier
            .semantics {
                if (!copied) contentDescription = action
                liveRegion = LiveRegionMode.Polite
            }
            .testTag(DeviceTestTags.COPY_SERIAL),
    )
}

/**
 * The parts its page draws, each with what it reads and, when the server offers one, its reset
 * (a destructive one is confirmed first, by the screen). [puraAir] names the accessory a litter
 * box's N60 is read from.
 */
@Composable
internal fun ConsumablesCard(
    consumables: List<Hotspot>,
    puraAir: Device?,
    sending: Set<String>,
    onAction: (DeviceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PkCard(modifier.fillMaxWidth().testTag(DeviceTestTags.CONSUMABLES)) {
        Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
            consumables.forEach { hotspot ->
                ConsumableRow(
                    hotspot,
                    via = puraAir?.modelName?.takeIf { hotspot.part == DevicePart.PuraAirSpray },
                    sending = sending,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ConsumableRow(hotspot: Hotspot, via: String?, sending: Set<String>, onAction: (DeviceAction) -> Unit) {
    val labels = rememberServerLabels()
    val colors = PkTheme.colors
    val details = listOfNotNull(
        via?.let { stringResource(R.string.device_consumable_via, it) },
        stringResource(R.string.page_attention).takeIf { hotspot.attention },
    )
    Column(Modifier.testTag(DeviceTestTags.consumable(hotspot.part.name))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
                .padding(vertical = PkTheme.spacing.xxs)
                .semantics(mergeDescendants = true) {},
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    hotspotLabel(hotspot, labels),
                    style = PkTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = PkTheme.typography.bodyMedium,
                        color = colors.onSurfaceMuted,
                    )
                }
            }
            Spacer(Modifier.width(PkTheme.spacing.s))
            Text(
                readingText(hotspot.reading, labels, short = true),
                style = PkTheme.typography.labelLarge,
                color = if (hotspot.attention) colors.warning else colors.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = READING_MAX_WIDTH),
            )
        }
        hotspot.actions.forEach { action ->
            PkButton(
                labels.action(action.key, action.name),
                onClick = { onAction(action) },
                style = PkButtonStyle.Secondary,
                icon = actionIcon(action.key),
                enabled = action.key !in sending,
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag(DeviceTestTags.consumableAction(action.key)),
            )
        }
    }
}

private val READING_MAX_WIDTH = 120.dp

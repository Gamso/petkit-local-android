package org.petkitlocal.feature.device.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.photo.DevicePhoto
import org.petkitlocal.core.designsystem.photo.PkDevicePhoto
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.feature.device.R

/**
 * A Bluetooth accessory's line to the device relaying it, "Reports through Pura Max", which opens
 * that device's screen. A real touch target, unlike the discreet "via" on Home's card.
 */
@Composable
fun RelayLink(relay: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(PkTheme.shapes.tile)
            .clickable(onClickLabel = stringResource(R.string.device_relay_open, relay), onClick = onClick)
            .defaultMinSize(minHeight = ROW_HEIGHT)
            .padding(vertical = PkTheme.spacing.xxs)
            .testTag(DeviceTestTags.RELAY),
    ) {
        Icon(PkIcons.Link, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(PkTheme.spacing.xs))
        Text(
            stringResource(R.string.device_relay, relay),
            style = PkTheme.typography.bodyMedium,
            color = colors.accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            PkIcons.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A device screen's top row: the way back, the title and its subtitle, the settings shortcut. */
@Composable
fun DeviceHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        PkBackButton(onClick = onBack)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = PkTheme.typography.titleLarge,
                color = PkTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
            }
        }
        if (onOpenSettings != null) {
            Spacer(Modifier.width(PkTheme.spacing.xs))
            PkIconButton(PkIcons.Settings, stringResource(R.string.device_open_settings), onOpenSettings)
        }
    }
}

/**
 * A device's picture: the model's product [photo] when the app has one, else its family's symbol
 * on a tinted tile, our own drawing. In its own composable so the user's photo can replace it later.
 */
@Composable
fun DeviceIllustration(family: DeviceFamily, modifier: Modifier = Modifier, photo: DevicePhoto? = null) {
    val colors = PkTheme.colors
    val tint = when (family) {
        DeviceFamily.LitterBox -> colors.heroLitter
        DeviceFamily.Feeder -> colors.heroFeeder
        else -> colors.accentContainer
    }
    if (photo != null) {
        PkDevicePhoto(photo, ILLUSTRATION_SIZE, modifier.size(ILLUSTRATION_SIZE), backdrop = tint)
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(ILLUSTRATION_SIZE)
            .clip(PkTheme.shapes.tile)
            .background(tint),
    ) {
        Icon(
            familyIcon(family),
            contentDescription = null,
            tint = colors.onAccentContainer,
            modifier = Modifier.size(ILLUSTRATION_SIZE / 2),
        )
    }
}

private val ILLUSTRATION_SIZE = 88.dp

/** The symbol of a family; litter boxes and feeders get the symbol of their main action. */
internal fun familyIcon(family: DeviceFamily): ImageVector = when (family) {
    DeviceFamily.LitterBox -> PkIcons.Clean
    DeviceFamily.Feeder -> PkIcons.Feed
    DeviceFamily.Fountain -> PkIcons.WaterDrop
    DeviceFamily.Purifier -> PkIcons.AirPurifier
    DeviceFamily.Other -> PkIcons.DeviceUnknown
}

/**
 * The live status line (SPEC.md §5.3): a dot and the words (never the colour alone), then how
 * the device reaches the server and when it last did, which says how fast a command arrives.
 * A page that says when the device was last seen elsewhere leaves it out ([showLastSeen]).
 */
@Composable
fun DeviceStatusLine(
    device: Device,
    status: DeviceStatus,
    labels: ServerLabels,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    showLastSeen: Boolean = true,
) {
    val colors = PkTheme.colors
    val color = when (status) {
        DeviceStatus.Idle -> colors.success
        is DeviceStatus.Busy -> colors.accent
        DeviceStatus.Offline -> colors.onSurfaceMuted
        is DeviceStatus.Error -> colors.danger
    }
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(PkTheme.spacing.xs))
            Text(statusText(status, labels), style = PkTheme.typography.titleMedium, color = colors.onSurface)
        }
        val details = listOfNotNull(
            // An accessory's transport is its parent's, which the relay line names.
            when (device.transport.takeUnless { device.isAccessory }) {
                null -> stringResource(R.string.device_transport_ble)
                DeviceTransport.Mqtt -> stringResource(R.string.device_transport_mqtt)
                DeviceTransport.Http -> stringResource(R.string.device_transport_http)
                DeviceTransport.Unknown -> null
            },
            device.lastSeen?.takeIf { showLastSeen }?.let {
                stringResource(R.string.device_last_seen, instantText(it, zone))
            },
        )
        if (details.isNotEmpty()) {
            Text(details.joinToString(" · "), style = PkTheme.typography.bodyMedium, color = colors.onSurfaceMuted)
        }
        device.firmware?.let {
            Text(
                stringResource(R.string.device_firmware, it),
                style = PkTheme.typography.bodyMedium,
                color = colors.onSurfaceMuted,
            )
        }
    }
}

/** One entity and its value, side by side; long values wrap under their own column. */
@Composable
fun EntityRow(entity: Entity, labels: ServerLabels, zone: ZoneId, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ROW_HEIGHT)
            .padding(vertical = PkTheme.spacing.xxs),
    ) {
        Text(
            labels.entity(entity.key, entity.name),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            entityValueText(entity, labels, zone),
            style = PkTheme.typography.labelLarge,
            color = PkTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.End,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private val ROW_HEIGHT = 40.dp

/** A section's title above its card. */
@Composable
fun DeviceSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = PkTheme.typography.titleMedium,
        color = PkTheme.colors.onSurface,
        modifier = modifier
            .padding(top = PkTheme.spacing.xs)
            .semantics { heading() },
    )
}

/** The screen for a device the server no longer has (removed while the screen was open). */
@Composable
fun DeviceGoneView(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.device_gone),
        style = PkTheme.typography.bodyLarge,
        color = PkTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(PkTheme.spacing.xl),
    )
}

/** Test tags shared by the device screens, for UI and end-to-end tests. */
object DeviceTestTags {
    const val SCREEN = "device:screen"
    const val LIST = "device:list"
    const val SETTINGS_SCREEN = "device:settings"
    const val SETTINGS_LIST = "device:settings:list"
    const val ACTION_SHEET = "device:actions"
    const val SHEET_HANDLE = "device:actions:handle"
    const val SHEET_MORE = "device:actions:more"

    /** A switch among the sheet's actions (the T4's Pura Air light), by its entity's key. */
    fun actionSwitch(key: String): String = "device:actions:switch:$key"
    const val FEEDBACK = "device:feedback"
    const val STATUS = "device:status"
    const val EDITOR = "device:editor"
    const val EDITOR_SAVE = "device:editor:save"

    /** A Bluetooth accessory's way to the device relaying it, or a part's way to its accessory. */
    const val RELAY = "device:relay"
    const val OPEN_ACCESSORY = "device:open-accessory"

    /** The settings' identity card, its Copy button, and the consumables after it. */
    const val IDENTITY = "device:settings:identity"
    const val COPY_SERIAL = "device:settings:copy-serial"
    const val CONSUMABLES = "device:settings:consumables"

    /** A consumable's row, by its part's name (`Deodorizer`, `PuraAirSpray`...). */
    fun consumable(part: String): String = "device:settings:consumable:$part"

    fun consumableAction(key: String): String = "device:settings:consumable-action:$key"

    fun entity(key: String): String = "device:entity:$key"

    fun option(value: String): String = "device:option:$value"

    fun folded(category: String): String = "device:settings:folded:$category"
}

package org.petkitlocal.feature.device.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkConfirmDialog
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R

/** The symbol of an action, by its key; a kind no symbol says gets a plain "run" arrow. */
internal fun actionIcon(key: String): ImageVector =
    ACTION_ICONS[key] ?: ACTION_ICON_PREFIXES.firstOrNull { (prefix, _) -> key.startsWith(prefix) }?.second
        ?: PkIcons.PlayArrow

private val ACTION_ICONS: Map<String, ImageVector> = mapOf(
    "deodorize" to PkIcons.Deodorize,
    "level_litter" to PkIcons.Level,
    "finish_litter_calibration" to PkIcons.Level,
    "cancel_manual_feed" to PkIcons.Feed,
    "food_replenished" to PkIcons.Feed,
    "fountain_deep_clean" to PkIcons.Clean,
    "cleaning_start" to PkIcons.Clean,
    "pack_waste" to PkIcons.Clean,
    "dump_litter" to PkIcons.Clean,
    "light" to PkIcons.Lightbulb,
    // The feeder page's own entries of the sheet (FeederScreen).
    "feeder:plan" to PkIcons.Calendar,
    "feeder:later" to PkIcons.Clock,
    "feeder:history" to PkIcons.History,
)

/** In order: the first prefix that matches wins. */
private val ACTION_ICON_PREFIXES: List<Pair<String, ImageVector>> = listOf(
    "feed" to PkIcons.Feed,
    "fountain_" to PkIcons.WaterDrop,
    "power_" to PkIcons.Power,
    "reset" to PkIcons.Refresh,
    "maintenance_" to PkIcons.Settings,
)

/** Asks before an action that cannot be undone; the confirm button names it. */
@Composable
fun ActionConfirmation(action: DeviceAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val name = rememberServerLabels().action(action.key, action.name)
    PkConfirmDialog(
        title = stringResource(R.string.device_confirm_title, name),
        message = stringResource(R.string.device_confirm_message),
        confirmLabel = name,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * What became of the last command (SPEC.md §6): sending, then how petkit-local passed it on
 * (`mqtt`, `queued` within ~10 s, `local`), or the server's refusal. Read out by TalkBack as it
 * changes; a tap dismisses it.
 */
@Composable
fun CommandFeedbackBanner(feedback: CommandFeedback, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val labels = rememberServerLabels()
    val colors = PkTheme.colors
    val result = feedback.result
    val (container, content) = when (result) {
        CommandResult.Sending -> colors.surface to colors.onSurface
        is CommandResult.Sent -> colors.accentContainer to colors.onAccentContainer
        is CommandResult.Failed -> colors.warningContainer to colors.onWarningContainer
    }
    val message = when (result) {
        CommandResult.Sending -> stringResource(R.string.device_feedback_sending)
        is CommandResult.Sent -> deliveryText(result.delivery)
        is CommandResult.Failed -> errorText(result.error)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(PkTheme.shapes.tile)
            .background(container)
            .clickable(onClickLabel = stringResource(R.string.device_feedback_dismiss), onClick = onDismiss)
            .padding(horizontal = PkTheme.spacing.m, vertical = PkTheme.spacing.s)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(DeviceTestTags.FEEDBACK),
    ) {
        when (result) {
            CommandResult.Sending -> CircularProgressIndicator(
                color = colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )

            is CommandResult.Sent -> Icon(PkIcons.Check, contentDescription = null, tint = content)

            is CommandResult.Failed -> Icon(PkIcons.Warning, contentDescription = null, tint = content)
        }
        Spacer(Modifier.width(PkTheme.spacing.s))
        Column(Modifier.weight(1f)) {
            Text(subjectName(feedback.subject, labels), style = PkTheme.typography.labelLarge, color = content)
            Text(message, style = PkTheme.typography.bodyMedium, color = content)
        }
    }
}

@Composable
private fun subjectName(subject: CommandSubject, labels: ServerLabels): String = when (subject) {
    is CommandSubject.Labelled -> stringResource(subject.label)
    is CommandSubject.Action -> labels.action(subject.key, subject.name)
    is CommandSubject.Setting -> labels.entity(subject.key, subject.name)
}

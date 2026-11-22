package org.petkitlocal.feature.device.fountain

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.drinksync.DrinkSyncFailure
import org.petkitlocal.core.domain.drinksync.DrinkSyncStep
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.currentLocale
import org.petkitlocal.feature.device.common.errorText
import org.petkitlocal.feature.device.litter.CardTitle

/**
 * A CTW3's drink history (issue #104): the fountain keeps its drinks and gives them only over a
 * direct Bluetooth link, so the phone reads them and sends them to the server. The card says where
 * a sync stands, step by step (Searching for the fountain, Connected over Bluetooth, Reading N
 * drinks, Done), or why it failed, and when one last succeeded on this phone. Its button is always
 * there: what keeps a sync from running is said on the card once pressed, never by a grey button;
 * it waits only while a sync runs.
 */
@Composable
internal fun DrinkSyncCard(
    state: DrinkSyncState,
    today: LocalDate,
    zone: ZoneId,
    onSync: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PkCard(modifier.fillMaxWidth().testTag(FountainTestTags.DRINK_SYNC)) {
        CardTitle(stringResource(R.string.fountain_sync_title))
        Text(
            stringResource(R.string.fountain_sync_explain),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(top = PkTheme.spacing.xs),
        )
        SyncStatus(state, onOpenSettings, Modifier.padding(top = PkTheme.spacing.s))
        Text(
            lastSyncText(state.lastSync, today, zone),
            style = PkTheme.typography.labelMedium,
            color = PkTheme.colors.onSurfaceMuted,
            modifier = Modifier
                .padding(top = PkTheme.spacing.s)
                .testTag(FountainTestTags.DRINK_SYNC_LAST),
        )
        PkButton(
            text = stringResource(R.string.fountain_sync_button),
            onClick = onSync,
            enabled = !state.running,
            icon = PkIcons.Bluetooth,
            modifier = Modifier
                .padding(top = PkTheme.spacing.s)
                .fillMaxWidth()
                .testTag(FountainTestTags.DRINK_SYNC_BUTTON),
        )
    }
}

/** The step, or the reason, in a band TalkBack announces as it changes; nothing before the first press. */
@Composable
private fun SyncStatus(state: DrinkSyncState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val step = state.step
    val colors = PkTheme.colors
    val failed = step is DrinkSyncStep.Failed
    if (step == null && !state.askingPermission) return
    val (container, content) = when {
        failed -> colors.warningContainer to colors.onWarningContainer
        step is DrinkSyncStep.Done -> colors.accentContainer to colors.onAccentContainer
        else -> colors.surfaceMuted to colors.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(PkTheme.shapes.tile)
            .background(container)
            .padding(horizontal = PkTheme.spacing.m, vertical = PkTheme.spacing.s)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(FountainTestTags.DRINK_SYNC_STATUS),
    ) {
        StatusIcon(state, content)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs)) {
            Text(statusText(state), style = PkTheme.typography.labelLarge, color = content)
            detailText(step)?.let { Text(it, style = PkTheme.typography.labelMedium, color = content) }
            if (step.isPermissionRefused()) {
                PkButton(
                    text = stringResource(R.string.fountain_sync_open_settings),
                    onClick = onOpenSettings,
                    style = PkButtonStyle.Text,
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(state: DrinkSyncState, tint: Color) {
    val icon: ImageVector? = when (state.step) {
        is DrinkSyncStep.Done -> PkIcons.Check
        is DrinkSyncStep.Failed -> PkIcons.Warning
        DrinkSyncStep.Connected, is DrinkSyncStep.Reading, is DrinkSyncStep.Uploading -> PkIcons.Bluetooth
        else -> null
    }
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    } else {
        SearchingIndicator()
    }
}

/**
 * The spinner while the fountain is searched for. Still in inspection mode (previews, screenshot
 * tests): animated, it never lets Robolectric's looper go idle and the capture hangs.
 */
@Composable
private fun SearchingIndicator() {
    val modifier = Modifier.size(20.dp)
    if (LocalInspectionMode.current) {
        CircularProgressIndicator(
            progress = { STILL_PROGRESS },
            color = PkTheme.colors.accent,
            strokeWidth = 2.dp,
            modifier = modifier,
        )
    } else {
        CircularProgressIndicator(color = PkTheme.colors.accent, strokeWidth = 2.dp, modifier = modifier)
    }
}

private const val STILL_PROGRESS = 0.25f

@Composable
private fun statusText(state: DrinkSyncState): String {
    val nearby = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return when (val step = state.step) {
        null -> stringResource(
            if (nearby) R.string.fountain_sync_permission_reason else R.string.fountain_sync_location_reason,
        )

        DrinkSyncStep.Searching -> stringResource(R.string.fountain_sync_searching)

        DrinkSyncStep.Connected -> stringResource(R.string.fountain_sync_connected)

        is DrinkSyncStep.Reading -> pluralStringResource(R.plurals.fountain_sync_reading, step.drinks, step.drinks)

        is DrinkSyncStep.Uploading -> pluralStringResource(R.plurals.fountain_sync_uploading, step.drinks, step.drinks)

        is DrinkSyncStep.Done -> if (step.newDrinks == 0) {
            stringResource(R.string.fountain_sync_done_none)
        } else {
            pluralStringResource(R.plurals.fountain_sync_done, step.newDrinks, step.newDrinks)
        }

        is DrinkSyncStep.Failed -> failureText(step.reason, nearby)
    }
}

@Composable
private fun failureText(reason: DrinkSyncFailure, nearby: Boolean): String = when (reason) {
    is DrinkSyncFailure.Link -> stringResource(
        if (reason.reason == FountainLinkFailure.PermissionMissing && !nearby) {
            R.string.fountain_sync_location_refused
        } else {
            LINK_FAILURE_TEXTS.getValue(reason.reason)
        },
    )

    is DrinkSyncFailure.SecretUnknown -> stringResource(R.string.fountain_sync_secret_unknown)

    is DrinkSyncFailure.SecretInvalid -> stringResource(R.string.fountain_sync_secret_invalid)

    DrinkSyncFailure.Unsupported -> stringResource(R.string.fountain_sync_unsupported)

    is DrinkSyncFailure.Server -> stringResource(R.string.fountain_sync_server_failed, errorText(reason.error))
}

/** What each way the link failed says (a refused permission names location before Android 12). */
private val LINK_FAILURE_TEXTS: Map<FountainLinkFailure, Int> = mapOf(
    FountainLinkFailure.BluetoothUnavailable to R.string.fountain_sync_bluetooth_unavailable,
    FountainLinkFailure.BluetoothOff to R.string.fountain_sync_bluetooth_off,
    FountainLinkFailure.PermissionMissing to R.string.fountain_sync_permission_refused,
    FountainLinkFailure.NotFound to R.string.fountain_sync_not_found,
    FountainLinkFailure.SecretRefused to R.string.fountain_sync_secret_refused,
    FountainLinkFailure.ClockRefused to R.string.fountain_sync_clock_refused,
    FountainLinkFailure.StreamRefused to R.string.fountain_sync_stream_refused,
    FountainLinkFailure.Timeout to R.string.fountain_sync_timeout,
    FountainLinkFailure.ConnectionLost to R.string.fountain_sync_connection_lost,
)

/** Under a refusal of the secret: the server's own words when it gave some, else what to do in its panel. */
@Composable
private fun detailText(step: DrinkSyncStep?): String? {
    val reason = (step as? DrinkSyncStep.Failed)?.reason
    return when (reason) {
        is DrinkSyncFailure.SecretUnknown -> reason.message ?: stringResource(R.string.fountain_sync_secret_hint)
        is DrinkSyncFailure.SecretInvalid -> reason.message ?: stringResource(R.string.fountain_sync_secret_hint)
        else -> null
    }
}

private fun DrinkSyncStep?.isPermissionRefused(): Boolean =
    ((this as? DrinkSyncStep.Failed)?.reason as? DrinkSyncFailure.Link)?.reason == FountainLinkFailure.PermissionMissing

/** "Last synced today at 14:32", yesterday, or on a date; never on this phone. */
@Composable
private fun lastSyncText(at: Instant?, today: LocalDate, zone: ZoneId): String {
    if (at == null) return stringResource(R.string.fountain_sync_never)
    val locale = currentLocale()
    val local = at.atZone(zone)
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(local)
    return when (val day = local.toLocalDate()) {
        today -> stringResource(R.string.fountain_sync_last_today, time)

        today.minusDays(1) -> stringResource(R.string.fountain_sync_last_yesterday, time)

        else -> stringResource(
            R.string.fountain_sync_last_date,
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day),
            time,
        )
    }
}

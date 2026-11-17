package org.petkitlocal.feature.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.ui.rememberNotificationPermissionRequest
import org.petkitlocal.core.ui.startFirst

/*
 * What Settings asks Android for on behalf of the notifications (issue #23): the notification
 * permission (Android 13+) and, once, the battery-optimisation exemption, each with its reason,
 * next to the switch that needs them.
 */

/**
 * Carries out [SettingsViewModel.effects] and reads Android's settings again each time the screen
 * comes back to the front, where the user may have changed them.
 */
@Composable
internal fun NotificationPromptsEffects(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermissionRequest(viewModel::onNotificationPermissionResult)
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.RequestNotificationPermission -> requestPermission(effect.askedBefore)
                SettingsEffect.RequestBatteryExemption -> requestBatteryExemption(context)
            }
        }
    }
}

/**
 * Android's own dialog exempting this app from battery optimisation, as the ntfy app asks: the
 * app is distributed on GitHub, not on a store whose policy restricts it (SPEC.md decision 4).
 */
@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    startFirst(
        context,
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null),
        ),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )
}

/** The one-time explanation before Android's battery request, over the screen. */
@Composable
internal fun BatteryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { BatteryDialogContent(onConfirm, onDismiss) }
}

/** The dialog's card, outside any window: what screenshot tests and previews render. */
@Composable
internal fun BatteryDialogContent(onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = PkTheme.shapes.dialog,
        color = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        modifier = modifier.testTag(SettingsTestTags.BATTERY_DIALOG),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier.padding(PkTheme.spacing.xl),
        ) {
            Text(
                stringResource(R.string.settings_battery_title),
                style = PkTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.settings_battery_message),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = PkTheme.spacing.s),
            ) {
                PkButton(
                    stringResource(R.string.settings_battery_not_now),
                    onClick = onDismiss,
                    style = PkButtonStyle.Text,
                )
                PkButton(
                    stringResource(R.string.settings_battery_continue),
                    onClick = onConfirm,
                    modifier = Modifier.testTag(SettingsTestTags.BATTERY_CONTINUE),
                )
            }
        }
    }
}

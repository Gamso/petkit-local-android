package org.petkitlocal.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkConfirmDialog
import org.petkitlocal.core.designsystem.component.PkPreviewSurface
import org.petkitlocal.core.designsystem.component.PkPreviews
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.feature.settings.licences.LicenceDialog

/** The callbacks of [SettingsScreen], one per user intent. */
internal class SettingsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onKindToggle: (kind: String, on: Boolean) -> Unit = { _, _ -> },
    val onChangePassword: () -> Unit = {},
    val onOpenLicences: () -> Unit = {},
    val onAppLicenceClick: () -> Unit = {},
    val onAppLicenceDismiss: () -> Unit = {},
    val onSignOutClick: () -> Unit = {},
    val onSignOutConfirm: () -> Unit = {},
    val onSignOutDismiss: () -> Unit = {},
)

@Composable
internal fun SettingsRoute(navigation: SettingsNavigation, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState,
        SettingsActions(
            onBack = navigation.onBack,
            onRetry = viewModel::onRetry,
            onKindToggle = viewModel::onKindToggle,
            onChangePassword = navigation.onChangePassword,
            onOpenLicences = navigation.onOpenLicences,
            onAppLicenceClick = viewModel::onAppLicenceClick,
            onAppLicenceDismiss = viewModel::onAppLicenceDismiss,
            onSignOutClick = viewModel::onSignOutClick,
            onSignOutConfirm = viewModel::onSignOutConfirm,
            onSignOutDismiss = viewModel::onSignOutDismiss,
        ),
    )
}

/**
 * Settings in categories, each setting with the line on what it does (SPEC.md §5.8): the server,
 * this phone's notification kinds, this phone, the account, and About. No language or theme: the
 * app follows the phone's.
 */
@Composable
internal fun SettingsScreen(uiState: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m),
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(PkTheme.spacing.gutter)
            .testTag(SettingsTestTags.SCREEN),
    ) {
        PkBackButton(onClick = actions.onBack)
        Text(
            stringResource(R.string.settings_title),
            style = PkTheme.typography.display,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        ServerSection(uiState, actions.onRetry)
        NotificationsSection(uiState, actions)
        PhoneSection(uiState)
        AccountSection(uiState, actions)
        AboutSection(uiState, actions)
    }
    val appLicence = uiState.appLicence
    if (uiState.showingAppLicence && appLicence != null) {
        LicenceDialog(
            stringResource(R.string.settings_app_licence_title),
            listOf(appLicence),
            actions.onAppLicenceDismiss,
        )
    }
    if (uiState.confirmingSignOut) {
        PkConfirmDialog(
            title = stringResource(R.string.settings_sign_out_title),
            message = stringResource(R.string.settings_sign_out_message),
            confirmLabel = stringResource(R.string.settings_sign_out),
            onConfirm = actions.onSignOutConfirm,
            onDismiss = actions.onSignOutDismiss,
        )
    }
}

/** A failed read, said where it failed, with the way to try again. */
@Composable
internal fun RetryNote(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs), modifier = modifier) {
        SectionNote(text, error = true)
        PkButton(
            stringResource(R.string.settings_retry),
            onClick = onRetry,
            style = PkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.RETRY),
        )
    }
}

/** Tags for UI tests and the end-to-end flows. */
object SettingsTestTags {
    const val SCREEN = "settings"
    const val SERVER_VERSION = "settings_server_version"
    const val PHONE_NAME = "settings_phone_name"
    const val NOTIFICATIONS = "settings_notifications"
    const val NOTIFICATIONS_ERROR = "settings_notifications_error"
    const val RETRY = "settings_retry"
    const val CHANGE_PASSWORD = "settings_change_password"
    const val SIGN_OUT = "settings_sign_out"
    const val APP_VERSION = "settings_app_version"
    const val LICENCES = "settings_licences"
    const val APP_LICENCE = "settings_app_licence"

    fun kind(kind: String) = "settings_kind_$kind"
}

@PkPreviews
@Composable
private fun SettingsScreenPreview() = PkPreviewSurface {
    SettingsScreen(SettingsPreviewData.loaded, SettingsActions())
}

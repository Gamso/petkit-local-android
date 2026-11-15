package org.petkitlocal.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkConfirmDialog
import org.petkitlocal.core.designsystem.component.PkPreviewSurface
import org.petkitlocal.core.designsystem.component.PkPreviews
import org.petkitlocal.core.designsystem.theme.PkTheme

/** The callbacks of [SettingsScreen], one per user intent. */
internal class SettingsActions(
    val onBack: () -> Unit = {},
    val onSignOutClick: () -> Unit = {},
    val onSignOutConfirm: () -> Unit = {},
    val onSignOutDismiss: () -> Unit = {},
)

@Composable
internal fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState,
        SettingsActions(
            onBack = onBack,
            onSignOutClick = viewModel::onSignOutClick,
            onSignOutConfirm = viewModel::onSignOutConfirm,
            onSignOutDismiss = viewModel::onSignOutDismiss,
        ),
    )
}

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
        Section(stringResource(R.string.settings_section_account)) {
            uiState.server?.let {
                Text(
                    stringResource(R.string.settings_signed_in_to, it),
                    style = PkTheme.typography.bodyMedium,
                    color = PkTheme.colors.onSurfaceMuted,
                )
            }
            PkButton(
                stringResource(if (uiState.signingOut) R.string.settings_signing_out else R.string.settings_sign_out),
                onClick = actions.onSignOutClick,
                style = PkButtonStyle.Danger,
                enabled = !uiState.signingOut,
                modifier = Modifier.fillMaxWidth().testTag(SettingsTestTags.SIGN_OUT),
            )
        }
        Text(
            stringResource(R.string.settings_more_later),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurfaceMuted,
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

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
        Text(
            title,
            style = PkTheme.typography.titleMedium,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        PkCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s), content = content)
        }
    }
}

/** Tags for UI tests and the end-to-end flows. */
object SettingsTestTags {
    const val SCREEN = "settings"
    const val SIGN_OUT = "settings_sign_out"
}

@PkPreviews
@Composable
private fun SettingsScreenPreview() = PkPreviewSurface {
    SettingsScreen(SettingsUiState(server = "https://pets.example.com"), SettingsActions())
}

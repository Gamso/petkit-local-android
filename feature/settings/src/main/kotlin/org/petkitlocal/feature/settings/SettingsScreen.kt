package org.petkitlocal.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkPreviewSurface
import org.petkitlocal.core.designsystem.component.PkPreviews
import org.petkitlocal.core.ui.PkPlaceholderScreen

@Composable
internal fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(uiState, onBack)
}

@Composable
internal fun SettingsScreen(uiState: SettingsUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PkPlaceholderScreen(
        title = stringResource(R.string.settings_title),
        milestone = (uiState as? SettingsUiState.Placeholder)?.milestone,
        modifier = modifier,
        onBack = onBack,
    )
}

@PkPreviews
@Composable
private fun SettingsScreenPreview() = PkPreviewSurface {
    SettingsScreen(SettingsUiState.Placeholder("M5"), onBack = {})
}

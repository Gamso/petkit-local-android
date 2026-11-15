package org.petkitlocal.feature.live

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkPreviewSurface
import org.petkitlocal.core.designsystem.component.PkPreviews
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.ui.PkPlaceholderScreen

@Composable
internal fun LiveRoute(onBack: () -> Unit, viewModel: LiveViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LiveScreen(uiState, onBack)
}

@Composable
internal fun LiveScreen(uiState: LiveUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PkPlaceholderScreen(
        title = stringResource(R.string.live_title),
        milestone = (uiState as? LiveUiState.Placeholder)?.milestone,
        modifier = modifier,
        onBack = onBack,
    ) {
        val deviceId = (uiState as? LiveUiState.Placeholder)?.deviceId
        if (deviceId != null) {
            Text(
                stringResource(R.string.live_device_number, deviceId),
                style = PkTheme.typography.titleMedium,
                color = PkTheme.colors.onSurface,
            )
        }
    }
}

@PkPreviews
@Composable
private fun LiveScreenPreview() = PkPreviewSurface {
    LiveScreen(LiveUiState.Placeholder("M4", deviceId = 1), onBack = {})
}

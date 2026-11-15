package org.petkitlocal.feature.device

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
internal fun DeviceRoute(onBack: () -> Unit, viewModel: DeviceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceScreen(uiState, onBack)
}

@Composable
internal fun DeviceScreen(uiState: DeviceUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PkPlaceholderScreen(
        title = stringResource(R.string.device_title),
        milestone = (uiState as? DeviceUiState.Placeholder)?.milestone,
        modifier = modifier,
        onBack = onBack,
    ) {
        val deviceId = (uiState as? DeviceUiState.Placeholder)?.deviceId
        if (deviceId != null) {
            Text(
                stringResource(R.string.device_device_number, deviceId),
                style = PkTheme.typography.titleMedium,
                color = PkTheme.colors.onSurface,
            )
        }
    }
}

@PkPreviews
@Composable
private fun DeviceScreenPreview() = PkPreviewSurface {
    DeviceScreen(DeviceUiState.Placeholder("M2", deviceId = 1), onBack = {})
}

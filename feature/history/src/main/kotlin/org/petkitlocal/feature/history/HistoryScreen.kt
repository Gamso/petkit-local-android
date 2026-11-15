package org.petkitlocal.feature.history

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
internal fun HistoryRoute(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(uiState, onBack)
}

@Composable
internal fun HistoryScreen(uiState: HistoryUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PkPlaceholderScreen(
        title = stringResource(R.string.history_title),
        milestone = (uiState as? HistoryUiState.Placeholder)?.milestone,
        modifier = modifier,
        onBack = onBack,
    )
}

@PkPreviews
@Composable
private fun HistoryScreenPreview() = PkPreviewSurface { HistoryScreen(HistoryUiState.Placeholder("M3"), onBack = {}) }

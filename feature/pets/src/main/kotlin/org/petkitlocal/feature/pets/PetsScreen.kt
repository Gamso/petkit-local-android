package org.petkitlocal.feature.pets

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
internal fun PetsRoute(onBack: () -> Unit, viewModel: PetsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PetsScreen(uiState, onBack)
}

@Composable
internal fun PetsScreen(uiState: PetsUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PkPlaceholderScreen(
        title = stringResource(R.string.pets_title),
        milestone = (uiState as? PetsUiState.Placeholder)?.milestone,
        modifier = modifier,
        onBack = onBack,
    )
}

@PkPreviews
@Composable
private fun PetsScreenPreview() = PkPreviewSurface { PetsScreen(PetsUiState.Placeholder("M3"), onBack = {}) }

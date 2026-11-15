package org.petkitlocal.feature.auth

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
internal fun SignInRoute(onBack: () -> Unit, viewModel: SignInViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SignInScreen(uiState, onBack)
}

@Composable
internal fun SignInScreen(uiState: SignInUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    PkPlaceholderScreen(
        title = stringResource(R.string.auth_title),
        milestone = (uiState as? SignInUiState.Placeholder)?.milestone,
        modifier = modifier,
        onBack = onBack,
    )
}

@PkPreviews
@Composable
private fun SignInScreenPreview() = PkPreviewSurface { SignInScreen(SignInUiState.Placeholder("M1"), onBack = {}) }

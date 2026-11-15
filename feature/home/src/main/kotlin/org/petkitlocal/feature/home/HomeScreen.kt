package org.petkitlocal.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkPreviewSurface
import org.petkitlocal.core.designsystem.component.PkPreviews
import org.petkitlocal.core.ui.PkPlaceholderScreen

/** The other screens, reachable from Home until the real navigation arrives with M1. */
enum class HomeShortcut(@StringRes val label: Int) {
    SignIn(R.string.home_shortcut_sign_in),
    Device(R.string.home_shortcut_device),
    History(R.string.home_shortcut_history),
    Pets(R.string.home_shortcut_pets),
    Live(R.string.home_shortcut_live),
    Settings(R.string.home_shortcut_settings),
}

@Composable
internal fun HomeRoute(onShortcut: (HomeShortcut) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState, onShortcut)
}

@Composable
internal fun HomeScreen(uiState: HomeUiState, onShortcut: (HomeShortcut) -> Unit, modifier: Modifier = Modifier) {
    PkPlaceholderScreen(
        title = stringResource(R.string.home_title),
        milestone = (uiState as? HomeUiState.Placeholder)?.milestone,
        modifier = modifier,
    ) {
        HomeShortcut.entries.forEach { shortcut ->
            PkButton(
                stringResource(shortcut.label),
                onClick = { onShortcut(shortcut) },
                style = PkButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PkPreviews
@Composable
private fun HomeScreenPreview() = PkPreviewSurface { HomeScreen(HomeUiState.Placeholder("M1"), onShortcut = {}) }

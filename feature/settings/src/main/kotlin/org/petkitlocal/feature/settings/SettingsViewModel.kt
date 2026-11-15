package org.petkitlocal.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the settings screen shows. A placeholder until M5 (docs/ROADMAP.md). */
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Placeholder(val milestone: String) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = flowOf<SettingsUiState>(SettingsUiState.Placeholder(MILESTONE))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState.Loading)

    companion object {
        const val MILESTONE = "M5"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

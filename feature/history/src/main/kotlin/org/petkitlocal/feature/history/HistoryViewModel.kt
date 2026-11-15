package org.petkitlocal.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the history screen shows. A placeholder until M3 (docs/ROADMAP.md). */
sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Placeholder(val milestone: String) : HistoryUiState
}

@HiltViewModel
class HistoryViewModel @Inject constructor() : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = flowOf<HistoryUiState>(HistoryUiState.Placeholder(MILESTONE))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState.Loading)

    companion object {
        const val MILESTONE = "M3"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

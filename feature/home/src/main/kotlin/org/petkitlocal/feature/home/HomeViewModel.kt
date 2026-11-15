package org.petkitlocal.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the home screen shows. A placeholder until M1 (docs/ROADMAP.md). */
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Placeholder(val milestone: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {
    val uiState: StateFlow<HomeUiState> = flowOf<HomeUiState>(HomeUiState.Placeholder(MILESTONE))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState.Loading)

    companion object {
        const val MILESTONE = "M1"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

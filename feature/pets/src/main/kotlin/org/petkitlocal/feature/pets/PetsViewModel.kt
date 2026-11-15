package org.petkitlocal.feature.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the pets screen shows. A placeholder until M3 (docs/ROADMAP.md). */
sealed interface PetsUiState {
    data object Loading : PetsUiState

    data class Placeholder(val milestone: String) : PetsUiState
}

@HiltViewModel
class PetsViewModel @Inject constructor() : ViewModel() {
    val uiState: StateFlow<PetsUiState> = flowOf<PetsUiState>(PetsUiState.Placeholder(MILESTONE))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PetsUiState.Loading)

    companion object {
        const val MILESTONE = "M3"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

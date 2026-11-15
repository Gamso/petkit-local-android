package org.petkitlocal.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the sign in screen shows. A placeholder until M1 (docs/ROADMAP.md). */
sealed interface SignInUiState {
    data object Loading : SignInUiState

    data class Placeholder(val milestone: String) : SignInUiState
}

@HiltViewModel
class SignInViewModel @Inject constructor() : ViewModel() {
    val uiState: StateFlow<SignInUiState> = flowOf<SignInUiState>(SignInUiState.Placeholder(MILESTONE))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SignInUiState.Loading)

    companion object {
        const val MILESTONE = "M1"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

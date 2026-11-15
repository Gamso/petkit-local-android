package org.petkitlocal.feature.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the live video screen shows. A placeholder until M4 (docs/ROADMAP.md). */
sealed interface LiveUiState {
    data object Loading : LiveUiState

    data class Placeholder(val milestone: String, val deviceId: Long?) : LiveUiState
}

@HiltViewModel
class LiveViewModel @Inject constructor(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val uiState: StateFlow<LiveUiState> = flowOf<LiveUiState>(
        LiveUiState.Placeholder(MILESTONE, deviceId = savedStateHandle.get<Long>(DEVICE_ID_KEY)),
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LiveUiState.Loading)

    companion object {
        const val MILESTONE = "M4"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The [LiveDestination] property the navigation library stores the argument under. */
        const val DEVICE_ID_KEY = "deviceId"
    }
}

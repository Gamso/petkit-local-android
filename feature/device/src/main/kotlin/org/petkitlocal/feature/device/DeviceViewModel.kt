package org.petkitlocal.feature.device

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the device screen shows. A placeholder until M2 (docs/ROADMAP.md). */
sealed interface DeviceUiState {
    data object Loading : DeviceUiState

    data class Placeholder(val milestone: String, val deviceId: Long?) : DeviceUiState
}

@HiltViewModel
class DeviceViewModel @Inject constructor(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val uiState: StateFlow<DeviceUiState> = flowOf<DeviceUiState>(
        DeviceUiState.Placeholder(MILESTONE, deviceId = savedStateHandle.get<Long>(DEVICE_ID_KEY)),
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DeviceUiState.Loading)

    companion object {
        const val MILESTONE = "M2"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** The [DeviceDestination] property the navigation library stores the argument under. */
        const val DEVICE_ID_KEY = "deviceId"
    }
}

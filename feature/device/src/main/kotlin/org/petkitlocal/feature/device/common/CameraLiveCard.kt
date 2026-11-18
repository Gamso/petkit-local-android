package org.petkitlocal.feature.device.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.video.ObserveLatestStillUseCase
import org.petkitlocal.core.ui.LiveCard
import org.petkitlocal.core.ui.LiveCardState
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.DeviceNavigation

/**
 * The live card of a camera's page: the latest still its cache holds (ADR 0002). It asks the
 * server for nothing itself: the page's recent clips refresh ([RecentClipsViewModel]) reads the
 * device's newest cards when the page opens and when it is pulled down, and a live update writes
 * a new one, so the still follows both.
 */
@HiltViewModel
class LiveCardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeLatestStill: ObserveLatestStillUseCase,
    clock: Clock,
) : ViewModel() {
    private val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY]) { "no $DEVICE_ID_KEY argument" }

    val state: StateFlow<LiveCardState> = observeLatestStill(deviceId)
        .map { still -> LiveCardState(still, clock.zone, LocalDate.now(clock)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            LiveCardState(zone = clock.zone, today = LocalDate.now(clock)),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The page's device's live card, as its cache shows it. */
@Composable
internal fun rememberLiveCard(viewModel: LiveCardViewModel = hiltViewModel()): LiveCardState {
    val state by viewModel.state.collectAsStateWithLifecycle()
    return state
}

/**
 * The live card at the top of [device]'s page, leading to its live video: for a device the server
 * says has a camera (`is_camera`) only.
 */
@Composable
internal fun CameraLiveCard(device: Device, state: LiveCardState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    if (device.isCamera) LiveCard(device.modelName, state, onOpen, modifier)
}

/** The live card of [device]'s family page, opening the live video through [navigation]. */
@Composable
internal fun CameraLiveCard(
    device: Device,
    state: LiveCardState,
    navigation: DeviceNavigation,
    modifier: Modifier = Modifier,
) = CameraLiveCard(device, state, onOpen = { navigation.onOpenLive(device.id) }, modifier = modifier)

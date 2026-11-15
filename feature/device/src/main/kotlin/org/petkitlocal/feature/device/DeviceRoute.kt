package org.petkitlocal.feature.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.feature.device.common.DeviceGoneView
import org.petkitlocal.feature.device.generic.GenericDeviceRoute

/** Which screen a device gets: known once the cache has been read. */
sealed interface DeviceKindState {
    data object Loading : DeviceKindState

    /** Not (or no longer) on the server. */
    data object Gone : DeviceKindState

    data class Known(val family: DeviceFamily) : DeviceKindState
}

/** Reads only the device's family, so the dispatch is not recomposed by every live update. */
@HiltViewModel
class DeviceRouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDevice: ObserveDeviceUseCase,
) : ViewModel() {
    private val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY])

    val kind: StateFlow<DeviceKindState> = observeDevice(deviceId)
        .map { device -> device?.let { DeviceKindState.Known(it.family) } ?: DeviceKindState.Gone }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DeviceKindState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * The device destination: the screen of the device's family. Every family shows the generic
 * screen for now; the litter box and feeder screens take over theirs when they land (M2).
 */
@Composable
internal fun DeviceRoute(navigation: DeviceNavigation, viewModel: DeviceRouteViewModel = hiltViewModel()) {
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    when (val state = kind) {
        DeviceKindState.Loading, DeviceKindState.Gone -> DevicePlaceholder(state, navigation.onBack)

        is DeviceKindState.Known -> when (state.family) {
            DeviceFamily.LitterBox,
            DeviceFamily.Feeder,
            DeviceFamily.Fountain,
            DeviceFamily.Purifier,
            DeviceFamily.Other,
            -> GenericDeviceRoute(navigation)
        }
    }
}

/** Before the cache answers (a few milliseconds), or for a device that is gone. */
@Composable
private fun DevicePlaceholder(state: DeviceKindState, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .safeDrawingPadding()
            .padding(PkTheme.spacing.gutter),
    ) {
        PkBackButton(onClick = onBack)
        if (state == DeviceKindState.Gone) {
            DeviceGoneView()
        } else {
            PkLoadingView(modifier = Modifier.padding(top = PkTheme.spacing.m), cards = 2)
        }
    }
}

/** A title for a screen whose device is not known yet. */
@Composable
internal fun deviceFallbackTitle(): String = stringResource(R.string.device_title)

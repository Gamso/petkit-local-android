package org.petkitlocal.feature.device.common

import androidx.compose.foundation.lazy.LazyListScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.video.ObserveRecentClipsUseCase
import org.petkitlocal.core.domain.video.RefreshRecentClipsUseCase
import org.petkitlocal.core.ui.RecentClipsCard
import org.petkitlocal.core.ui.RecentClipsState
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.DeviceNavigation

/**
 * A camera's recent clips on its page (SPEC.md §5.7), beside whichever family's page it is. It
 * reads the cache (ADR 0002) and, for a device the server says has a camera (`is_camera`), asks
 * the server for the newest clips when the page opens and when it is pulled down.
 */
@HiltViewModel
class RecentClipsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeDevice: ObserveDeviceUseCase,
    observeRecentClips: ObserveRecentClipsUseCase,
    private val refreshRecentClips: RefreshRecentClipsUseCase,
    private val clock: Clock,
) : ViewModel() {
    val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY]) { "no $DEVICE_ID_KEY argument" }

    private var refresh: Job? = null
    private val local = MutableStateFlow(RecentClipsState(zone = clock.zone, today = LocalDate.now(clock)))

    val state: StateFlow<RecentClipsState> = combine(observeRecentClips(deviceId), local) { clips, state ->
        state.copy(clips = clips, cacheLoaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        onRefresh()
    }

    /** Reads the newest clips from the server, for a camera only; one refresh at a time. */
    fun onRefresh() {
        if (refresh?.isActive == true) return
        refresh = viewModelScope.launch {
            if (observeDevice(deviceId).first()?.isCamera != true) return@launch
            local.update { it.copy(refreshing = true, error = null, today = LocalDate.now(clock)) }
            val result = refreshRecentClips(deviceId)
            local.update {
                it.copy(
                    refreshing = false,
                    refreshed = it.refreshed || result is Outcome.Success,
                    error = (result as? Outcome.Failure)?.error,
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** A camera page's recent clips and where they lead. */
data class RecentClipsUi(
    val state: RecentClipsState,
    val onOpenClip: (MediaFile) -> Unit = {},
    /** History, narrowed to this device. */
    val onSeeAll: () -> Unit = {},
    /** Asks the server again: a retry, or the page pulled down. */
    val onRefresh: () -> Unit = {},
)

/** The recent clips of the page's device, wired to [navigation]. */
@Composable
internal fun rememberRecentClips(
    navigation: DeviceNavigation,
    viewModel: RecentClipsViewModel = hiltViewModel(),
): RecentClipsUi {
    val state by viewModel.state.collectAsStateWithLifecycle()
    return RecentClipsUi(
        state = state,
        onOpenClip = navigation.onOpenClip,
        onSeeAll = { navigation.onOpenHistory(viewModel.deviceId) },
        onRefresh = viewModel::onRefresh,
    )
}

/** The recent clips card, on the page of a device the server says has a camera only. */
internal fun LazyListScope.recentClips(device: Device, clips: RecentClipsUi?, modifier: Modifier = Modifier) {
    if (clips == null || !device.isCamera) return
    item(key = "recent-clips") {
        RecentClipsCard(
            state = clips.state,
            onOpenClip = clips.onOpenClip,
            onSeeAll = clips.onSeeAll,
            onRetry = clips.onRefresh,
            modifier = modifier,
        )
    }
}

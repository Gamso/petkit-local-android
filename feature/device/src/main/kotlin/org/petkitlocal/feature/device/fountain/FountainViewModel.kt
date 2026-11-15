package org.petkitlocal.feature.device.fountain

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.devicepage.RefreshDevicePageUseCase
import org.petkitlocal.core.domain.fountain.FountainPage
import org.petkitlocal.core.domain.fountain.ObserveFountainUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.DeviceCommandController
import org.petkitlocal.feature.device.generic.isUnreachable
import org.petkitlocal.feature.device.litter.PageLoadState

/**
 * What the fountain page shows. [page] is the cache (null until read, or when the device is gone,
 * which [loaded] tells apart); [commands] is what the actions and switches did; the rest is the
 * page's own state.
 */
data class FountainUiState(
    val page: FountainPage? = null,
    override val loaded: Boolean = false,
    override val refreshing: Boolean = false,
    /** Why the last refresh failed; null after a successful one. */
    override val refreshError: DomainError? = null,
    /** The live socket cannot reach the server: what is shown is the last known data. */
    val liveUnreachable: Boolean = false,
    val commands: CommandState = CommandState(),
    /** The phone's time zone, which the chart places the drinks in. */
    val zone: ZoneId = ZoneId.systemDefault(),
    /** The pet the chart shows, or every pet. */
    val petFilter: Long? = null,
    /** The part whose explanation is open. */
    val openPart: DevicePart? = null,
) : PageLoadState {
    val offline: Boolean get() = liveUnreachable || refreshError is DomainError.Network

    /** The hotspot of [openPart], as the cache has it now. */
    val openHotspot: Hotspot? get() = page?.hotspots?.firstOrNull { it.part == openPart }
}

/**
 * The fountain page. It observes the cache only (ADR 0002), keeps the live socket open while
 * shown, and refreshes when it opens. Actions, their confirmation and the mode chips go through
 * the device screens' shared [DeviceCommandController]: nothing changes on screen but its feedback
 * until the device's next report.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@HiltViewModel
class FountainViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeFountain: ObserveFountainUseCase,
    observeLiveUpdates: ObserveLiveUpdatesUseCase,
    private val refreshPage: RefreshDevicePageUseCase,
    runAction: RunDeviceActionUseCase,
    setEntityValue: SetEntityValueUseCase,
    private val today: TodayUseCase,
    clock: Clock,
) : ViewModel() {
    val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY]) { "no $DEVICE_ID_KEY argument" }

    private val commands = DeviceCommandController(viewModelScope, deviceId, runAction, setEntityValue)
    private val day = MutableStateFlow(today())
    private val local = MutableStateFlow(FountainUiState(zone = clock.zone))

    val uiState: StateFlow<FountainUiState> = combine(
        day.flatMapLatest { observeFountain(deviceId, it) },
        observeLiveUpdates(),
        commands.state,
        local,
    ) { page, live, commands, state ->
        state.copy(page = page, loaded = true, liveUnreachable = live.isUnreachable(), commands = commands)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        onRefresh()
    }

    fun onRefresh() {
        if (local.value.refreshing) return
        local.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            day.value = today()
            val result = refreshPage(day.value)
            local.update { it.copy(refreshing = false, refreshError = (result as? Outcome.Failure)?.error) }
        }
    }

    /** A button of the action sheet or of a hotspot's explanation (which it closes). */
    fun onAction(action: DeviceAction) {
        local.update { it.copy(openPart = null) }
        commands.onAction(action)
    }

    fun onConfirmAction() = commands.onConfirm()

    fun onDismissConfirmation() = commands.onDismissConfirmation()

    fun onDismissFeedback() = commands.onDismissFeedback()

    /**
     * A mode chip: writes its switch, or its mode select's `smart` or `normal`. The chip changes
     * when the device's report does.
     */
    fun onModeToggle(key: String, on: Boolean) {
        val page = uiState.value.page ?: return
        val mode = page.modes.firstOrNull { it.key == key } ?: return
        val entity = page.device.entity(key) ?: return
        commands.onWrite(entity, if (on) mode.onValue else mode.offValue)
    }

    fun onPetFilter(petId: Long?) = local.update { it.copy(petFilter = petId) }

    fun onHotspot(part: DevicePart?) = local.update { it.copy(openPart = part) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

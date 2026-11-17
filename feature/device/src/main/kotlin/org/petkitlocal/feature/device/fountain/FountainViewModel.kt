package org.petkitlocal.feature.device.fountain

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
import org.petkitlocal.core.domain.model.EntityValue
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
    /** The mode option last tapped, until the fountain reports it, the write fails or it times out. */
    val requestedMode: String? = null,
    /** What "Seen 2 min ago" counts from: the page's clock, moved on every minute while shown. */
    val now: Instant = Instant.EPOCH,
) : PageLoadState {
    val offline: Boolean get() = liveUnreachable || refreshError is DomainError.Network

    /**
     * The option tapped that the fountain has not confirmed: shown as pending, never as selected
     * (ADR 0002). Null once its report says so.
     */
    val pendingMode: String? get() = requestedMode?.takeIf { it != page?.modeBar?.selected }

    /** The hotspot of [openPart], as the cache has it now. */
    val openHotspot: Hotspot? get() = page?.hotspots?.firstOrNull { it.part == openPart }
}

/**
 * The fountain page. It observes the cache only (ADR 0002), keeps the live socket open while
 * shown, and refreshes when it opens. Actions, their confirmation and the mode chips go through
 * the device screens' shared [DeviceCommandController]: nothing changes on screen but its feedback
 * until the device's next report. A mode tapped on the bottom bar shows as pending, the selection
 * staying on what the fountain reported, until its report carries the new mode.
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
    private val clock: Clock,
) : ViewModel() {
    val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY]) { "no $DEVICE_ID_KEY argument" }

    private val commands = DeviceCommandController(viewModelScope, deviceId, runAction, setEntityValue)
    private val day = MutableStateFlow(today())
    private val local = MutableStateFlow(FountainUiState(zone = clock.zone, now = clock.instant()))
    private var modeConfirmation: Job? = null

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
        local.update { it.copy(refreshing = true, now = clock.instant()) }
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
     * An option of the bottom bar's mode: written to the fountain's mode select, and pending
     * until the fountain reports it ([FountainUiState.pendingMode]). The selection itself moves
     * only with the device's report. A tap while the last write is still on its way is ignored;
     * the confirmed option, with nothing pending, writes nothing.
     */
    fun onMode(option: String) {
        val state = uiState.value
        val bar = state.page?.modeBar
        val entity = bar?.let { state.page.device.entity(it.key) }
        val ignored = (option == bar?.selected && state.pendingMode == null) || bar?.key in state.commands.sending
        if (entity == null || option !in bar.options || ignored) return
        local.update { it.copy(requestedMode = option) }
        modeConfirmation?.cancel()
        modeConfirmation = viewModelScope.launch {
            val sent = commands.write(entity, EntityValue.Text(option), reportFailure = true)
            if (sent is Outcome.Success) {
                withTimeoutOrNull(MODE_CONFIRMATION_TIMEOUT_MS) {
                    uiState.first { it.page?.modeBar?.selected == option }
                }
            }
            local.update { if (it.requestedMode == option) it.copy(requestedMode = null) else it }
        }
    }

    /** A minute has passed: "Seen 2 min ago" counts it. */
    fun onTick() = local.update { it.copy(now = clock.instant()) }

    fun onPetFilter(petId: Long?) = local.update { it.copy(petFilter = petId) }

    fun onHotspot(part: DevicePart?) = local.update { it.copy(openPart = part) }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * How long a sent mode stays pending without the fountain's report: a Bluetooth fountain
         * reports when its relay next asks, an HTTP device within its next check-in.
         */
        const val MODE_CONFIRMATION_TIMEOUT_MS = 120_000L
    }
}

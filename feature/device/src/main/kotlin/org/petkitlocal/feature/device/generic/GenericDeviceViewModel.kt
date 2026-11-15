package org.petkitlocal.feature.device.generic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.DeviceSections
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RefreshDevicesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.DeviceCommandController

/**
 * What the generic device screen shows. [device] is the cache (null while it is read, and for a
 * device that is gone once [loaded]); the rest says how fresh it is and what the commands did.
 */
data class GenericDeviceUiState(
    val loaded: Boolean = false,
    val device: Device? = null,
    val status: DeviceStatus? = null,
    val sections: DeviceSections? = null,
    /** For a Bluetooth accessory: the device relaying it, once the cache holds it. */
    val relay: Device? = null,
    /** The server cannot be reached: what is shown is the last known data. */
    val offline: Boolean = false,
    val refreshing: Boolean = false,
    val commands: CommandState = CommandState(),
    /** The phone's time zone, which dates are shown in. */
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * Any device's screen (SPEC.md §4, "generic: status, entities, actions"), from the cache only
 * (ADR 0002). Collecting the live-updates status keeps `/ws` open while the screen is visible,
 * and says when the server is out of reach.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@HiltViewModel
class GenericDeviceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDevice: ObserveDeviceUseCase,
    observeLiveUpdates: ObserveLiveUpdatesUseCase,
    private val refreshDevices: RefreshDevicesUseCase,
    runAction: RunDeviceActionUseCase,
    setEntityValue: SetEntityValueUseCase,
    clock: Clock,
) : ViewModel() {
    private val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY])
    private val commands = DeviceCommandController(viewModelScope, deviceId, runAction, setEntityValue)
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<DomainError?>(null)
    private val zone = clock.zone

    /** The device, and the one relaying it when it is a Bluetooth accessory. */
    private val deviceAndRelay: Flow<Pair<Device?, Device?>> = observeDevice(deviceId).flatMapLatest { device ->
        val parentId = device?.parentId?.takeIf { device.isAccessory } ?: return@flatMapLatest flowOf(device to null)
        observeDevice(parentId).map { device to it }
    }

    val uiState: StateFlow<GenericDeviceUiState> = combine(
        deviceAndRelay,
        observeLiveUpdates(),
        refreshing,
        refreshError,
        commands.state,
    ) { (device, relay), live, refreshing, refreshError, commands ->
        GenericDeviceUiState(
            loaded = true,
            device = device,
            status = device?.let(HomeRules::status),
            sections = device?.let(DeviceSections::of),
            relay = relay,
            offline = live.isUnreachable() || refreshError is DomainError.Network,
            refreshing = refreshing,
            commands = commands,
            zone = zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GenericDeviceUiState(zone = zone))

    /** Pull to refresh. The live socket keeps the cache current; this is for when it cannot. */
    fun onRefresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            refreshError.value = (refreshDevices() as? Outcome.Failure)?.error
            refreshing.value = false
        }
    }

    fun onAction(action: DeviceAction) = commands.onAction(action)

    fun onConfirmAction() = commands.onConfirm()

    fun onDismissConfirmation() = commands.onDismissConfirmation()

    fun onDismissFeedback() = commands.onDismissFeedback()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The socket is waiting to reconnect because the server could not be reached. */
internal fun LiveUpdatesStatus.isUnreachable(): Boolean =
    this is LiveUpdatesStatus.Waiting && error is DomainError.Network

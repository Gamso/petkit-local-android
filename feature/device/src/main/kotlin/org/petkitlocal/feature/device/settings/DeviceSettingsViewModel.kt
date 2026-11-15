package org.petkitlocal.feature.device.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.DeviceSections
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.device.EntityWrites
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.device.SettingGroup
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.DeviceCommandController
import org.petkitlocal.feature.device.generic.isUnreachable

/** The editor open on one setting, with what went wrong with the last value tried, if anything. */
data class SettingEditorState(
    /** The setting as the cache holds it now: a live update shows in the open editor too. */
    val entity: Entity,
    /** Refused before sending (out of range, too long...). */
    val problem: EntityWriteProblem? = null,
    /** Refused by the server, or not delivered. */
    val error: DomainError? = null,
    /** The request is in flight: the only time Save is disabled. */
    val saving: Boolean = false,
)

data class DeviceSettingsUiState(
    val loaded: Boolean = false,
    val device: Device? = null,
    val groups: List<SettingGroup> = emptyList(),
    val editor: SettingEditorState? = null,
    val offline: Boolean = false,
    val commands: CommandState = CommandState(),
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * A device's settings (SPEC.md §5.3, "every settable entity, grouped, with the type-appropriate
 * control"). A switch is written when flipped; any other setting through its editor, which stays
 * open until the server took the value and says in place why it did not. The values shown are
 * the cache's (ADR 0002): what the server answered, then what the device reports.
 */
@HiltViewModel
class DeviceSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDevice: ObserveDeviceUseCase,
    observeLiveUpdates: ObserveLiveUpdatesUseCase,
    runAction: RunDeviceActionUseCase,
    setEntityValue: SetEntityValueUseCase,
    clock: Clock,
) : ViewModel() {
    private val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY])
    private val commands = DeviceCommandController(viewModelScope, deviceId, runAction, setEntityValue)
    private val zone = clock.zone

    private data class Editing(
        val key: String,
        val problem: EntityWriteProblem? = null,
        val error: DomainError? = null,
        val saving: Boolean = false,
    )

    /** The device once read, wrapped so that "read, and gone" (null inside) differs from "not read yet". */
    private data class Snapshot(val device: Device?)

    private val snapshot: StateFlow<Snapshot?> = observeDevice(deviceId)
        .map(::Snapshot)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val editing = MutableStateFlow<Editing?>(null)

    val uiState: StateFlow<DeviceSettingsUiState> = combine(
        snapshot,
        observeLiveUpdates(),
        editing,
        commands.state,
    ) { snapshot, live, editing, commands ->
        val device = snapshot?.device
        DeviceSettingsUiState(
            loaded = snapshot != null,
            device = device,
            groups = device?.let { DeviceSections.of(it).settings }.orEmpty(),
            editor = editing?.let { open ->
                device?.entity(open.key)?.let { SettingEditorState(it, open.problem, open.error, open.saving) }
            },
            offline = live.isUnreachable(),
            commands = commands,
            zone = zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DeviceSettingsUiState(zone = zone))

    /** A switch flipped: written at once; the switch moves when the server's answer is cached. */
    fun onToggle(key: String, on: Boolean) {
        val entity = current(key) ?: return
        commands.onWrite(entity, EntityValue.Bool(on))
    }

    fun onEdit(key: String) {
        if (current(key) != null) editing.value = Editing(key)
    }

    fun onDismissEditor() {
        editing.value = null
    }

    /** The editor's value: checked here first, then sent; the editor closes once it is taken. */
    fun onSave(key: String, value: EntityValue) {
        val entity = current(key) ?: return
        if (editing.value?.saving == true) return
        EntityWrites.problem(entity, value)?.let { problem ->
            editing.update { it?.copy(problem = problem, error = null) }
            return
        }
        editing.update { it?.copy(problem = null, error = null, saving = true) }
        viewModelScope.launch {
            val result = commands.write(entity, value, reportFailure = false)
            editing.update { open ->
                when {
                    open?.key != key -> open
                    result is Outcome.Failure -> open.copy(saving = false, error = result.error)
                    else -> null
                }
            }
        }
    }

    fun onDismissFeedback() = commands.onDismissFeedback()

    private fun current(key: String): Entity? = snapshot.value?.device?.entity(key)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

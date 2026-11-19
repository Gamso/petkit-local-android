package org.petkitlocal.feature.device.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.device.DeviceIdentity
import org.petkitlocal.core.domain.device.DeviceSettingsPage
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.device.ObserveDeviceSettingsUseCase
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.device.SettingGroup
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
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
    /** Who the device is: the card at the top. */
    val identity: DeviceIdentity? = null,
    /** Its parts and their resets, as its page draws them, right after the identity card. */
    val consumables: List<Hotspot> = emptyList(),
    /** The Pura Air whose N60 is among a litter box's [consumables]. */
    val puraAir: Device? = null,
    val groups: List<SettingGroup> = emptyList(),
    val editor: SettingEditorState? = null,
    val offline: Boolean = false,
    val commands: CommandState = CommandState(),
    val zone: ZoneId = ZoneId.systemDefault(),
    /** The serial number was just copied: its button says so for a moment. */
    val serialCopied: Boolean = false,
)

/**
 * A device's settings (SPEC.md §5.3, "every settable entity, grouped, with the type-appropriate
 * control"), under the device's identity card and its consumables (issue #59). A switch is
 * written when flipped; any other setting through its editor, which stays open until the server
 * took the value and says in place why it did not. A consumable's reset goes through the device
 * screens' shared [DeviceCommandController], confirmed first when it cannot be undone. The values
 * shown are the cache's (ADR 0002): what the server answered, then what the device reports.
 */
@HiltViewModel
class DeviceSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSettings: ObserveDeviceSettingsUseCase,
    observeLiveUpdates: ObserveLiveUpdatesUseCase,
    runAction: RunDeviceActionUseCase,
    setEntityValue: SetEntityValueUseCase,
    clock: Clock,
) : ViewModel() {
    private val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY])
    private val commands = DeviceCommandController(viewModelScope, deviceId, runAction, setEntityValue)
    private val editor = SettingEditorController(viewModelScope, commands)
    private val zone = clock.zone

    /** The device once read, wrapped so that "read, and gone" (null inside) differs from "not read yet". */
    private data class Snapshot(val page: DeviceSettingsPage?) {
        val device: Device? get() = page?.device
    }

    private val snapshot: StateFlow<Snapshot?> = observeSettings(deviceId)
        .map(::Snapshot)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val serialCopied = MutableStateFlow(false)
    private var copiedReset: Job? = null

    val uiState: StateFlow<DeviceSettingsUiState> = combine(
        snapshot,
        observeLiveUpdates(),
        editor.open,
        commands.state,
        serialCopied,
    ) { snapshot, live, editing, commands, copied ->
        val page = snapshot?.page
        val device = page?.device
        DeviceSettingsUiState(
            loaded = snapshot != null,
            device = device,
            identity = page?.identity,
            consumables = page?.consumables.orEmpty(),
            puraAir = page?.puraAir,
            groups = page?.groups.orEmpty(),
            editor = editor.state(editing, device),
            offline = live.isUnreachable(),
            commands = commands,
            zone = zone,
            serialCopied = copied,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DeviceSettingsUiState(zone = zone))

    /** A switch flipped: written at once; the switch moves when the server's answer is cached. */
    fun onToggle(key: String, on: Boolean) {
        val entity = current(key) ?: return
        commands.onWrite(entity, EntityValue.Bool(on))
    }

    fun onEdit(key: String) {
        current(key)?.let(editor::onEdit)
    }

    fun onDismissEditor() = editor.onDismiss()

    /** The editor's value: checked first, then sent; the editor closes once it is taken. */
    fun onSave(key: String, value: EntityValue) {
        current(key)?.let { editor.onSave(it, value) }
    }

    /** A consumable's reset: sent at once, or first confirmed when the server says it cannot be undone. */
    fun onAction(action: DeviceAction) = commands.onAction(action)

    fun onConfirmAction() = commands.onConfirm()

    fun onDismissConfirmation() = commands.onDismissConfirmation()

    fun onDismissFeedback() = commands.onDismissFeedback()

    /**
     * The screen put the serial number on the clipboard: its button says "Copied" for
     * [COPIED_SHOWN_MS], then offers to copy again.
     */
    fun onSerialCopied() {
        copiedReset?.cancel()
        serialCopied.value = true
        copiedReset = viewModelScope.launch {
            delay(COPIED_SHOWN_MS)
            serialCopied.value = false
        }
    }

    private fun current(key: String): Entity? = snapshot.value?.device?.entity(key)

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Long enough to be read, short enough not to linger over a second copy. */
        const val COPIED_SHOWN_MS = 2_000L
    }
}

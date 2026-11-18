package org.petkitlocal.feature.device.quickactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.device.DeviceActionOrder
import org.petkitlocal.core.domain.device.EditQuickActionsUseCase
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.device.ObserveQuickActionsUseCase
import org.petkitlocal.core.domain.device.QuickActionEdit
import org.petkitlocal.core.domain.device.QuickActionEditResult
import org.petkitlocal.core.domain.device.ResetQuickActionsUseCase
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.feature.device.DEVICE_ID_KEY

/** The editor of a device's quick actions, open. */
data class QuickActionsEditorState(
    /** The device's name, under the title. */
    val deviceName: String,
    /** In view on the collapsed sheet, in order. */
    val row: List<DeviceAction>,
    /** Every other action, in the app's order: under More. */
    val others: List<DeviceAction>,
    /** The user chose the row; otherwise the app did, and there is nothing to reset. */
    val custom: Boolean,
    /** What went wrong with the last edit, said where it went wrong. */
    val notice: QuickActionEditResult? = null,
)

data class QuickActionsUiState(
    /** False until the device's stored choice has been read. */
    val loaded: Boolean = false,
    /** The device's chosen keys, null while the app chooses. */
    val pinned: List<String>? = null,
    /** Null while the editor is closed, and for a device that is gone. */
    val editor: QuickActionsEditorState? = null,
)

/**
 * The quick actions of the device a destination shows (issue #40): read by its action sheet and
 * its settings, edited in the editor both open. An edit is written at once on the phone (no
 * device is involved, so there is nothing to wait for), and the editor shows the row as the
 * sheet will: a pinned action the server no longer lists has already given way there.
 */
@HiltViewModel
class QuickActionsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeDevice: ObserveDeviceUseCase,
    observeQuickActions: ObserveQuickActionsUseCase,
    private val editQuickActions: EditQuickActionsUseCase,
    private val resetQuickActions: ResetQuickActionsUseCase,
) : ViewModel() {
    private val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY])

    /** Kept across process death, as an open dialog is. */
    private val editing: StateFlow<Boolean> = savedStateHandle.getStateFlow(EDITING_KEY, false)
    private val notice = MutableStateFlow<QuickActionEditResult?>(null)

    private val device: StateFlow<Device?> = observeDevice(deviceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<QuickActionsUiState> = combine(
        device,
        observeQuickActions(deviceId),
        editing,
        notice,
    ) { device, pinned, editing, notice ->
        QuickActionsUiState(
            loaded = true,
            pinned = pinned,
            editor = device?.takeIf { editing }?.let { editorOf(it, pinned, notice) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), QuickActionsUiState())

    fun onOpen() {
        notice.value = null
        savedStateHandle[EDITING_KEY] = true
    }

    fun onClose() {
        savedStateHandle[EDITING_KEY] = false
    }

    fun onAdd(key: String) = edit(QuickActionEdit.Add(key))

    fun onRemove(key: String) = edit(QuickActionEdit.Remove(key))

    fun onMoveUp(key: String) = edit(QuickActionEdit.Move(key, -1))

    fun onMoveDown(key: String) = edit(QuickActionEdit.Move(key, +1))

    fun onReset() {
        viewModelScope.launch {
            notice.value = if (resetQuickActions(deviceId)) null else QuickActionEditResult.NotSaved
        }
    }

    private fun edit(edit: QuickActionEdit) {
        val actions = device.value?.actions ?: return
        viewModelScope.launch {
            notice.value = editQuickActions(deviceId, actions, edit).takeIf { it != QuickActionEditResult.Saved }
        }
    }

    private fun editorOf(
        device: Device,
        pinned: List<String>?,
        notice: QuickActionEditResult?,
    ): QuickActionsEditorState {
        val layout = DeviceActionOrder.layout(device.actions, pinned)
        return QuickActionsEditorState(
            deviceName = device.modelName,
            row = layout.main,
            others = layout.more,
            custom = pinned != null,
            notice = notice,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EDITING_KEY = "quickActionsEditing"
    }
}

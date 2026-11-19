package org.petkitlocal.feature.device.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.EntityWriteProblem
import org.petkitlocal.core.domain.device.EntityWrites
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.feature.device.common.DeviceCommandController

/**
 * The editor of one setting, in [SettingEditorDialog], for any screen that opens one: the settings
 * screen, and the fountain page's battery mode. A value [EntityWrites] already refuses is said in
 * place without a request; a value sent goes through the screen's [commands] (its banner says how
 * it went out), and the editor stays open until the server took it, saying in place why not.
 */
internal class SettingEditorController(
    private val scope: CoroutineScope,
    private val commands: DeviceCommandController,
) {
    /** The editor open on setting [key], with what became of the last value tried. */
    data class Open(
        val key: String,
        val problem: EntityWriteProblem? = null,
        val error: DomainError? = null,
        val saving: Boolean = false,
    )

    private val mutableOpen = MutableStateFlow<Open?>(null)

    /** The open editor, by key: [state] reads it against the cache. */
    val open: StateFlow<Open?> = mutableOpen.asStateFlow()

    /** [open] on [device] as the cache holds it now; null when none is open or its setting is gone. */
    fun state(open: Open?, device: Device?): SettingEditorState? = open?.let { editing ->
        device?.entity(editing.key)?.let { SettingEditorState(it, editing.problem, editing.error, editing.saving) }
    }

    fun onEdit(entity: Entity) {
        mutableOpen.value = Open(entity.key)
    }

    fun onDismiss() {
        mutableOpen.value = null
    }

    /** The editor's value: checked here first, then sent; the editor closes once it is taken. */
    fun onSave(entity: Entity, value: EntityValue) {
        if (mutableOpen.value?.saving == true) return
        EntityWrites.problem(entity, value)?.let { problem ->
            mutableOpen.update { it?.copy(problem = problem, error = null) }
            return
        }
        mutableOpen.update { it?.copy(problem = null, error = null, saving = true) }
        scope.launch {
            val result = commands.write(entity, value, reportFailure = false)
            mutableOpen.update { open ->
                when {
                    open?.key != entity.key -> open
                    result is Outcome.Failure -> open.copy(saving = false, error = result.error)
                    else -> null
                }
            }
        }
    }
}

package org.petkitlocal.core.domain.device

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.repository.QuickActionsRepository

/** One change the user makes to the actions in view on a device's collapsed sheet. */
sealed interface QuickActionEdit {
    val key: String

    /** Puts [key] in view, after the others. */
    data class Add(override val key: String) : QuickActionEdit

    /** Takes [key] out of view: it stays behind More. */
    data class Remove(override val key: String) : QuickActionEdit

    /** Moves [key] by [offset] slots: -1 is towards the start of the row, +1 towards More. */
    data class Move(override val key: String, val offset: Int) : QuickActionEdit
}

/**
 * The rules of the user's row, on action keys: at most [DeviceActionOrder.VISIBLE] of them,
 * each once, in the user's order. An edit applies to the row as the sheet shows it, so the first
 * one turns the app's choice into the user's, and a slot that had fallen back to the app's
 * ranking is kept as it shows.
 */
object QuickActionRow {
    /** [row] after [edit]; null when [edit] adds to a full row (the user removes one first). */
    fun apply(row: List<String>, edit: QuickActionEdit, visible: Int = DeviceActionOrder.VISIBLE): List<String>? =
        when (edit) {
            is QuickActionEdit.Add -> when {
                edit.key in row -> row
                row.size >= visible -> null
                else -> row + edit.key
            }

            is QuickActionEdit.Remove -> row - edit.key

            is QuickActionEdit.Move -> {
                val from = row.indexOf(edit.key)
                val to = from + edit.offset
                if (from < 0 || to !in row.indices) {
                    row
                } else {
                    row.toMutableList().apply { add(to, removeAt(from)) }
                }
            }
        }
}

/** The device's own choice of actions in view, null while it is the app's. */
class ObserveQuickActionsUseCase @Inject constructor(private val repository: QuickActionsRepository) {
    operator fun invoke(deviceId: Long): Flow<List<String>?> = repository.observe(deviceId)
}

/** What became of an edit of the row. */
enum class QuickActionEditResult {
    Saved,

    /** The row already holds [DeviceActionOrder.VISIBLE] actions: nothing was added. */
    RowFull,

    /** The phone could not write it. */
    NotSaved,
}

/**
 * Applies one edit to the row a device's sheet shows today ([DeviceActionOrder.layout] of its
 * [actions] and the stored choice), and stores the result as the user's choice.
 */
class EditQuickActionsUseCase @Inject constructor(private val repository: QuickActionsRepository) {
    suspend operator fun invoke(
        deviceId: Long,
        actions: List<DeviceAction>,
        edit: QuickActionEdit,
    ): QuickActionEditResult {
        var full = false
        val written = repository.update(deviceId) { stored ->
            val row = DeviceActionOrder.layout(actions, stored).main.map { it.key }
            QuickActionRow.apply(row, edit) ?: stored.also { full = true }
        }
        return when {
            !written -> QuickActionEditResult.NotSaved
            full -> QuickActionEditResult.RowFull
            else -> QuickActionEditResult.Saved
        }
    }
}

/** "Reset to default": the app chooses this device's actions in view again. */
class ResetQuickActionsUseCase @Inject constructor(private val repository: QuickActionsRepository) {
    suspend operator fun invoke(deviceId: Long): Boolean = repository.update(deviceId) { null }
}

package org.petkitlocal.feature.device.common

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * What a device's action sheet knows of the user's own row (issue #40). The device destinations
 * provide it around every family's screen (`QuickActionsHost`), so any page built on
 * [DeviceActionScaffold] shows the user's row and offers "Edit quick actions" with nothing to
 * wire. Outside them (a screen alone in a test or a preview) it is the app's row, not editable.
 *
 * @property pinned the device's chosen action keys in order; null while the app chooses.
 * @property ready false until the stored choice has been read: the sheet waits for it rather
 *   than showing the app's row and then swapping it.
 * @property onEdit opens the editor; null where it cannot be opened.
 */
@Immutable
data class QuickActions(val pinned: List<String>? = null, val ready: Boolean = true, val onEdit: (() -> Unit)? = null)

val LocalQuickActions = compositionLocalOf { QuickActions() }

/** Test tags of the quick actions entry points and editor. */
object QuickActionsTestTags {
    const val EDIT_ENTRY = "quick-actions:edit"
    const val EDITOR = "quick-actions:editor"
    const val DONE = "quick-actions:done"
    const val RESET = "quick-actions:reset"
    const val SETTINGS_ROW = "quick-actions:settings"

    fun inView(key: String): String = "quick-actions:in-view:$key"

    fun other(key: String): String = "quick-actions:other:$key"
}

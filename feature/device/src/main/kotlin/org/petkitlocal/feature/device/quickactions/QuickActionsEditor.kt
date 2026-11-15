package org.petkitlocal.feature.device.quickactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.DeviceActionOrder
import org.petkitlocal.core.domain.device.QuickActionEditResult
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DeviceSectionTitle
import org.petkitlocal.feature.device.common.LocalQuickActions
import org.petkitlocal.feature.device.common.QuickActions
import org.petkitlocal.feature.device.common.QuickActionsTestTags
import org.petkitlocal.feature.device.common.actionIcon

/** What the editor's user can do; the host wires each to the ViewModel. */
data class QuickActionsCallbacks(
    val onClose: () -> Unit = {},
    val onAdd: (key: String) -> Unit = {},
    val onRemove: (key: String) -> Unit = {},
    val onMoveUp: (key: String) -> Unit = {},
    val onMoveDown: (key: String) -> Unit = {},
    val onReset: () -> Unit = {},
)

/**
 * Wraps a device destination: gives every action sheet and the settings under it the device's
 * quick actions ([LocalQuickActions]), and shows the editor when one of them opens it.
 */
@Composable
internal fun QuickActionsHost(viewModel: QuickActionsViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val quick = remember(uiState.pinned, uiState.loaded, viewModel) {
        QuickActions(pinned = uiState.pinned, ready = uiState.loaded, onEdit = viewModel::onOpen)
    }
    CompositionLocalProvider(LocalQuickActions provides quick, content = content)
    uiState.editor?.let { editor ->
        QuickActionsEditorDialog(
            editor,
            QuickActionsCallbacks(
                onClose = viewModel::onClose,
                onAdd = viewModel::onAdd,
                onRemove = viewModel::onRemove,
                onMoveUp = viewModel::onMoveUp,
                onMoveDown = viewModel::onMoveDown,
                onReset = viewModel::onReset,
            ),
        )
    }
}

/** The editor over the whole screen: Back, or Done, closes it; every change is already saved. */
@Composable
internal fun QuickActionsEditorDialog(state: QuickActionsEditorState, callbacks: QuickActionsCallbacks) {
    Dialog(onDismissRequest = callbacks.onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        QuickActionsEditor(state, callbacks)
    }
}

/**
 * The editor, outside any window (what screenshot tests render). The row first, each action
 * with buttons to move it and take it out, which TalkBack also offers as actions on the row
 * itself; then every other action, a tap away from the row. A full row is said where the row
 * is, and Add stays a control that answers (never greyed out).
 */
@Composable
internal fun QuickActionsEditor(state: QuickActionsEditorState, callbacks: QuickActionsCallbacks) {
    val labels = rememberServerLabels()
    val colors = PkTheme.colors
    Surface(color = colors.background, contentColor = colors.onSurface, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(PkTheme.spacing.gutter)
                .testTag(QuickActionsTestTags.EDITOR),
        ) {
            EditorHeader(state, callbacks.onClose)
            Text(
                stringResource(R.string.quick_actions_explanation),
                style = PkTheme.typography.bodyMedium,
                color = colors.onSurfaceMuted,
            )
            if (state.notice == QuickActionEditResult.NotSaved) Notice(stringResource(R.string.quick_actions_not_saved))
            DeviceSectionTitle(
                stringResource(R.string.quick_actions_in_view, state.row.size, DeviceActionOrder.VISIBLE),
            )
            PkCard(Modifier.fillMaxWidth()) {
                if (!state.custom) Caption(stringResource(R.string.quick_actions_by_app))
                if (state.row.isEmpty()) Caption(stringResource(R.string.quick_actions_none))
                state.row.forEachIndexed { index, action ->
                    InViewRow(action, index, state.row.size, labels, callbacks)
                }
                if (state.notice == QuickActionEditResult.RowFull) {
                    Notice(stringResource(R.string.quick_actions_row_full))
                }
            }
            DeviceSectionTitle(stringResource(R.string.quick_actions_behind_more))
            PkCard(Modifier.fillMaxWidth()) {
                if (state.others.isEmpty()) Caption(stringResource(R.string.quick_actions_all_in_view))
                state.others.forEachIndexed { index, action ->
                    if (index > 0) HorizontalDivider(color = colors.outline)
                    OtherRow(action, labels, onAdd = { callbacks.onAdd(action.key) })
                }
            }
            EditorButtons(state.custom, callbacks)
        }
    }
}

@Composable
private fun EditorHeader(state: QuickActionsEditorState, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        PkBackButton(onClick = onClose)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.quick_actions_title),
                style = PkTheme.typography.titleLarge,
                color = PkTheme.colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(state.deviceName, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
        }
    }
}

/** Reset (only when there is something to reset) and Done; each change is already saved. */
@Composable
private fun EditorButtons(custom: Boolean, callbacks: QuickActionsCallbacks) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (custom) {
            // All the width Done leaves: a long French label wraps between words, not inside them.
            PkButton(
                text = stringResource(R.string.quick_actions_reset),
                onClick = callbacks.onReset,
                style = PkButtonStyle.Text,
                icon = PkIcons.Refresh,
                modifier = Modifier
                    .weight(1f)
                    .testTag(QuickActionsTestTags.RESET),
            )
        }
        PkButton(
            text = stringResource(R.string.quick_actions_done),
            onClick = callbacks.onClose,
            modifier = Modifier.testTag(QuickActionsTestTags.DONE),
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
}

/** What went wrong with the last change, read out as it appears. */
@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = PkTheme.typography.bodyMedium,
        color = PkTheme.colors.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * One action in view: its buttons beside its name when the name fits there on one line, under
 * it otherwise, so a long name at a large font size breaks between words and a short one leaves
 * no empty band. Its position is read with it, and moving it does not depend on dragging: the
 * arrows are buttons, and the row carries Move up, Move down and Remove as TalkBack actions.
 */
@Composable
private fun InViewRow(
    action: DeviceAction,
    index: Int,
    count: Int,
    labels: ServerLabels,
    callbacks: QuickActionsCallbacks,
) {
    val name = labels.action(action.key, action.name)
    val position = stringResource(R.string.quick_actions_position, index + 1, count)
    val canMoveUp = index > 0
    val canMoveDown = index < count - 1
    val moveUp = stringResource(R.string.quick_actions_move_up)
    val moveDown = stringResource(R.string.quick_actions_move_down)
    val remove = stringResource(R.string.quick_actions_remove)
    val confirmed = stringResource(R.string.device_actions_confirmed)
    val nameStyle = PkTheme.typography.bodyLarge
    val noteStyle = PkTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val beside = with(LocalDensity.current) {
        (ICON_BOX_SIZE + PkTheme.spacing.s + BUTTON_SIZE * BUTTONS).roundToPx()
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = position
                customActions = listOfNotNull(
                    CustomAccessibilityAction(moveUp) {
                        callbacks.onMoveUp(action.key)
                        true
                    }.takeIf { canMoveUp },
                    CustomAccessibilityAction(moveDown) {
                        callbacks.onMoveDown(action.key)
                        true
                    }.takeIf { canMoveDown },
                    CustomAccessibilityAction(remove) {
                        callbacks.onRemove(action.key)
                        true
                    },
                )
            }
            .testTag(QuickActionsTestTags.inView(action.key)),
    ) {
        val room = constraints.maxWidth - beside
        val oneLine = remember(name, confirmed, action.destructive, room, nameStyle, noteStyle, measurer) {
            fun width(text: String, style: TextStyle) =
                measurer.measure(AnnotatedString(text), style, softWrap = false, maxLines = 1).size.width
            width(name, nameStyle) <= room && (!action.destructive || width(confirmed, noteStyle) <= room)
        }
        if (oneLine) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                ActionName(action, name, Modifier.weight(1f))
                InViewButtons(action.key, name, canMoveUp, canMoveDown, callbacks)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(top = PkTheme.spacing.xs)) {
                ActionName(action, name, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    InViewButtons(action.key, name, canMoveUp, canMoveDown, callbacks)
                }
            }
        }
    }
}

@Composable
private fun InViewButtons(
    key: String,
    name: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    callbacks: QuickActionsCallbacks,
) {
    // A missing arrow keeps its place, so the buttons line up from one action to the next.
    if (canMoveUp) {
        PkIconButton(
            PkIcons.ArrowUpward,
            stringResource(R.string.quick_actions_move_up_named, name),
            onClick = { callbacks.onMoveUp(key) },
        )
    } else {
        Spacer(Modifier.size(BUTTON_SIZE))
    }
    if (canMoveDown) {
        PkIconButton(
            PkIcons.ArrowDownward,
            stringResource(R.string.quick_actions_move_down_named, name),
            onClick = { callbacks.onMoveDown(key) },
        )
    } else {
        Spacer(Modifier.size(BUTTON_SIZE))
    }
    PkIconButton(
        PkIcons.Close,
        stringResource(R.string.quick_actions_remove_named, name),
        onClick = { callbacks.onRemove(key) },
    )
}

/** An action under More: a tap puts it in view. */
@Composable
private fun OtherRow(action: DeviceAction, labels: ServerLabels, onAdd: () -> Unit) {
    val name = labels.action(action.key, action.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clip(PkTheme.shapes.tile)
            .clickable(onClickLabel = stringResource(R.string.quick_actions_add), role = Role.Button, onClick = onAdd)
            .padding(vertical = PkTheme.spacing.xxs)
            .testTag(QuickActionsTestTags.other(action.key)),
    ) {
        ActionName(action, name, Modifier.weight(1f))
        Icon(
            PkIcons.Add,
            contentDescription = null,
            tint = PkTheme.colors.accent,
            modifier = Modifier.padding(horizontal = PkTheme.spacing.s),
        )
    }
}

/** The action's symbol and name as the sheet shows them; a destructive one says it is confirmed. */
@Composable
private fun ActionName(action: DeviceAction, name: String, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ICON_BOX_SIZE)
                .clip(PkTheme.shapes.tile)
                .background(if (action.destructive) colors.warningContainer else colors.surfaceMuted),
        ) {
            Icon(
                actionIcon(action.key),
                contentDescription = null,
                tint = if (action.destructive) colors.onWarningContainer else colors.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(PkTheme.spacing.s))
        Column(Modifier.weight(1f)) {
            Text(name, style = PkTheme.typography.bodyLarge)
            if (action.destructive) {
                Text(
                    stringResource(R.string.device_actions_confirmed),
                    style = PkTheme.typography.bodyMedium,
                    color = colors.onSurfaceMuted,
                )
            }
        }
    }
}

private val ICON_BOX_SIZE = 40.dp
private val BUTTON_SIZE = 48.dp
private const val BUTTONS = 3

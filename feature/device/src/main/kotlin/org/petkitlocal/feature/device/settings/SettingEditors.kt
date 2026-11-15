package org.petkitlocal.feature.device.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalTime
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.EntityWrites
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.asDouble
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.errorText
import org.petkitlocal.feature.device.common.numberText
import org.petkitlocal.feature.device.common.optionText
import org.petkitlocal.feature.device.common.problemText

/** The editor of one setting, in a dialog. */
@Composable
internal fun SettingEditorDialog(state: SettingEditorState, onSave: (EntityValue) -> Unit, onDismiss: () -> Unit) {
    // The time picker needs more width than a dialog gives by default.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SettingEditorContent(
            state,
            onSave,
            onDismiss,
            Modifier
                .padding(PkTheme.spacing.gutter)
                .fillMaxWidth(),
        )
    }
}

/**
 * The dialog's card, outside any window: what screenshot tests render. The control follows the
 * component (SPEC.md §5.3): a slider and a stepper for a number, options for a select, a clock
 * for a time, a field for a text. Save is never disabled for a value that is wrong, only while
 * one is being sent: what is wrong is said under the value.
 */
@Composable
internal fun SettingEditorContent(
    state: SettingEditorState,
    onSave: (EntityValue) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entity = state.entity
    Surface(
        shape = PkTheme.shapes.dialog,
        color = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        modifier = modifier.testTag(DeviceTestTags.EDITOR),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(PkTheme.spacing.xl),
        ) {
            Text(
                rememberServerLabels().entity(entity.key, entity.name),
                style = PkTheme.typography.titleLarge,
                color = PkTheme.colors.onSurface,
            )
            when (entity.component) {
                EntityComponent.Number -> NumberEditor(state, onSave, onDismiss)
                EntityComponent.Select -> SelectEditor(state, onSave, onDismiss)
                EntityComponent.Time -> TimeEditor(state, onSave, onDismiss)
                else -> TextEditor(state, onSave, onDismiss)
            }
        }
    }
}

/** What is wrong with the last value tried, said in place, and read out as it appears. */
@Composable
private fun EditorMessage(state: SettingEditorState) {
    val text = state.problem?.let { problemText(it, state.entity) } ?: state.error?.let { errorText(it) } ?: return
    Text(
        text,
        style = PkTheme.typography.bodyMedium,
        color = PkTheme.colors.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Cancel and Save, at the end of an editor. */
@Composable
private fun EditorButtons(saving: Boolean, onSave: () -> Unit, onDismiss: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs, Alignment.End),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PkTheme.spacing.xs),
    ) {
        PkButton(
            stringResource(org.petkitlocal.core.designsystem.R.string.pk_cancel),
            onDismiss,
            style = PkButtonStyle.Text,
        )
        PkButton(
            stringResource(if (saving) R.string.device_editor_saving else R.string.device_editor_save),
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.testTag(DeviceTestTags.EDITOR_SAVE),
        )
    }
}

/** A number: a slider over its range when it has one, a stepper by its step, the value in words. */
@Composable
private fun NumberEditor(state: SettingEditorState, onSave: (EntityValue) -> Unit, onDismiss: () -> Unit) {
    val entity = state.entity
    val min = entity.min
    val max = entity.max
    var draft by rememberSaveable(entity.key) {
        mutableDoubleStateOf(entity.value.asDouble ?: min ?: 0.0)
    }
    val step = EntityWrites.step(entity)
    Text(
        numberText(draft, entity),
        style = PkTheme.typography.headlineLarge,
        color = PkTheme.colors.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        PkIconButton(PkIcons.Remove, stringResource(R.string.device_editor_decrease), {
            draft = EntityWrites.snap(entity, draft - step)
        })
        if (min != null && max != null && max > min) {
            Slider(
                value = draft.coerceIn(min, max).toFloat(),
                onValueChange = { draft = EntityWrites.snap(entity, it.toDouble()) },
                valueRange = min.toFloat()..max.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = PkTheme.colors.accent,
                    activeTrackColor = PkTheme.colors.accent,
                    inactiveTrackColor = PkTheme.colors.surfaceMuted,
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = PkTheme.spacing.xs),
            )
        } else {
            Row(Modifier.weight(1f)) {}
        }
        PkIconButton(PkIcons.Add, stringResource(R.string.device_editor_increase), {
            draft = EntityWrites.snap(entity, draft + step)
        })
    }
    if (min != null && max != null) {
        Text(
            stringResource(R.string.device_editor_range, numberText(min, entity), numberText(max, entity)),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    EditorMessage(state)
    EditorButtons(state.saving, onSave = { onSave(EntityValue.Number(draft)) }, onDismiss)
}

/** A select: its options; picking one sends it. */
@Composable
private fun SelectEditor(state: SettingEditorState, onSave: (EntityValue) -> Unit, onDismiss: () -> Unit) {
    val entity = state.entity
    val labels = rememberServerLabels()
    val selected = EntityWrites.selectedOption(entity)
    Column(Modifier.selectableGroup()) {
        entity.options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
                    .selectable(
                        selected = option == selected,
                        enabled = !state.saving,
                        role = Role.RadioButton,
                        onClick = { onSave(EntityValue.Text(option)) },
                    )
                    .testTag(DeviceTestTags.option(option)),
            ) {
                RadioButton(
                    selected = option == selected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = PkTheme.colors.accent,
                        unselectedColor = PkTheme.colors.onSurfaceMuted,
                    ),
                )
                Text(
                    optionText(option, labels),
                    style = PkTheme.typography.bodyLarge,
                    color = PkTheme.colors.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = PkTheme.spacing.s),
                )
            }
        }
    }
    if (state.saving) {
        CircularProgressIndicator(color = PkTheme.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    }
    EditorMessage(state)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        PkButton(
            stringResource(org.petkitlocal.core.designsystem.R.string.pk_cancel),
            onDismiss,
            style = PkButtonStyle.Text,
        )
    }
}

/** A time of day, on the phone's own clock (12 or 24 hours); sent as `HH:MM`. */
@OptIn(ExperimentalMaterial3Api::class) // TimePicker
@Composable
private fun TimeEditor(state: SettingEditorState, onSave: (EntityValue) -> Unit, onDismiss: () -> Unit) {
    val initial = EntityWrites.timeOfDay(state.entity.value) ?: LocalTime.MIDNIGHT
    val picker = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    val colors = PkTheme.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TimePicker(
            state = picker,
            colors = TimePickerDefaults.colors(
                clockDialColor = colors.surfaceMuted,
                selectorColor = colors.accent,
                clockDialSelectedContentColor = colors.onAccent,
                clockDialUnselectedContentColor = colors.onSurface,
                periodSelectorSelectedContainerColor = colors.accentContainer,
                periodSelectorSelectedContentColor = colors.onAccentContainer,
                periodSelectorUnselectedContentColor = colors.onSurfaceMuted,
                periodSelectorBorderColor = colors.outline,
                timeSelectorSelectedContainerColor = colors.accentContainer,
                timeSelectorSelectedContentColor = colors.onAccentContainer,
                timeSelectorUnselectedContainerColor = colors.surfaceMuted,
                timeSelectorUnselectedContentColor = colors.onSurface,
            ),
        )
    }
    EditorMessage(state)
    EditorButtons(
        state.saving,
        onSave = { onSave(EntityValue.Text(EntityWrites.timeText(LocalTime.of(picker.hour, picker.minute)))) },
        onDismiss,
    )
}

/** A text, up to 255 characters; a longer one is not cut, it is refused with the reason shown. */
@Composable
private fun TextEditor(state: SettingEditorState, onSave: (EntityValue) -> Unit, onDismiss: () -> Unit) {
    val entity = state.entity
    var draft by rememberSaveable(entity.key) {
        mutableStateOf((entity.value as? EntityValue.Text)?.value.orEmpty())
    }
    val save = { onSave(EntityValue.Text(draft)) }
    val wrong = state.problem != null || state.error != null
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        isError = wrong,
        singleLine = true,
        supportingText = {
            Text(
                stringResource(R.string.device_editor_counter, draft.length, EntityWrites.TEXT_MAX_LENGTH),
                color = if (draft.length >
                    EntityWrites.TEXT_MAX_LENGTH
                ) {
                    PkTheme.colors.danger
                } else {
                    PkTheme.colors.onSurfaceMuted
                },
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        shape = PkTheme.shapes.field,
        textStyle = PkTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
    EditorMessage(state)
    EditorButtons(state.saving, onSave = save, onDismiss)
}

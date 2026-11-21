package org.petkitlocal.feature.pets.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkConfirmDialog
import org.petkitlocal.core.designsystem.component.PkEmptyView
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.feature.pets.PetTextField
import org.petkitlocal.feature.pets.PetsNavigation
import org.petkitlocal.feature.pets.R
import org.petkitlocal.feature.pets.writeErrorMessage

/** What the editor lets its user do; the route wires each to the ViewModel. */
data class PetEditCallbacks(
    val onBack: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onWeight: (String) -> Unit = {},
    val onToggleDevice: (Long) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
)

/** Test tags of the editor's parts. */
object PetEditTestTags {
    const val SCREEN = "pet_edit"
    const val NAME = "pet_edit_name"
    const val WEIGHT = "pet_edit_weight"
    const val SAVE = "pet_edit_save"
    const val DELETE = "pet_edit_delete"
    const val ERROR = "pet_edit_error"

    fun device(id: Long) = "pet_edit_device_$id"
}

@Composable
internal fun PetEditRoute(navigation: PetsNavigation, viewModel: PetEditViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PetEditEffect.Saved -> navigation.onPetSaved(effect.petId, effect.created)
                PetEditEffect.Deleted -> navigation.onPetDeleted()
            }
        }
    }
    PetEditScreen(
        uiState,
        PetEditCallbacks(
            onBack = navigation.onBack,
            onName = viewModel::onName,
            onWeight = viewModel::onWeight,
            onToggleDevice = viewModel::onToggleDevice,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            onDismissDelete = viewModel::onDismissDelete,
        ),
    )
}

/**
 * The pet editor (issue #98): name, weight, and the devices that recognise it by its face, which
 * are the only ones offered. Save and Delete stay enabled; a field the server cannot take is said
 * under it when saving, and the server's own refusal under the buttons.
 */
@Composable
internal fun PetEditScreen(uiState: PetEditUiState, callbacks: PetEditCallbacks, modifier: Modifier = Modifier) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val gutter = PkTheme.spacing.gutter
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                top = insets.calculateTopPadding() + gutter,
                bottom =
                    insets.calculateBottomPadding() + PkTheme.spacing.xl,
            )
            .padding(horizontal = gutter)
            .testTag(PetEditTestTags.SCREEN),
    ) {
        val title = when {
            uiState.isNew -> stringResource(R.string.pet_editor_new_title)
            else -> stringResource(R.string.pet_editor_edit_title, uiState.pet?.name ?: uiState.name)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PkBackButton(onClick = callbacks.onBack)
            Spacer(Modifier.width(PkTheme.spacing.s))
            Text(
                title,
                style = PkTheme.typography.titleLarge,
                color = PkTheme.colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        when {
            !uiState.loaded -> PkLoadingView(cards = 2)
            uiState.gone -> PkEmptyView(stringResource(R.string.pet_editor_gone))
            else -> EditorForm(uiState, callbacks)
        }
    }
    if (uiState.confirmingDelete) {
        PkConfirmDialog(
            title = stringResource(R.string.pet_editor_delete_title, uiState.pet?.name.orEmpty()),
            message = stringResource(R.string.pet_editor_delete_message),
            confirmLabel = stringResource(R.string.pet_editor_delete_confirm),
            onConfirm = callbacks.onConfirmDelete,
            onDismiss = callbacks.onDismissDelete,
        )
    }
}

@Composable
private fun EditorForm(uiState: PetEditUiState, callbacks: PetEditCallbacks) {
    PkCard(Modifier.fillMaxWidth()) {
        PetTextField(
            value = uiState.name,
            onValueChange = callbacks.onName,
            label = stringResource(R.string.pet_editor_name),
            supporting = if (uiState.nameMissing) stringResource(R.string.pet_editor_name_missing) else null,
            isError = uiState.nameMissing,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.testTag(PetEditTestTags.NAME),
        )
        Spacer(Modifier.padding(top = PkTheme.spacing.xs))
        PetTextField(
            value = uiState.weight,
            onValueChange = callbacks.onWeight,
            label = stringResource(R.string.pet_editor_weight),
            supporting = stringResource(
                when (uiState.weightProblem) {
                    WeightProblem.Invalid -> R.string.pet_editor_weight_invalid
                    WeightProblem.OutOfRange -> R.string.pet_editor_weight_range
                    null -> R.string.pet_editor_weight_hint
                },
            ),
            isError = uiState.weightProblem != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier.testTag(PetEditTestTags.WEIGHT),
        )
    }
    DevicesCard(uiState, callbacks.onToggleDevice)
    EditorButtons(uiState, callbacks)
}

/** The server's refusal, if any, then Save and, for an existing pet, Delete. */
@Composable
private fun EditorButtons(uiState: PetEditUiState, callbacks: PetEditCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
        val error = uiState.error
        if (error != null) {
            Text(
                writeErrorMessage(error),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.danger,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(PetEditTestTags.ERROR),
            )
        }
        PkButton(
            text = stringResource(
                when {
                    uiState.saving -> R.string.pet_editor_saving
                    uiState.isNew -> R.string.pet_editor_create
                    else -> R.string.pet_editor_save
                },
            ),
            onClick = callbacks.onSave,
            enabled = !uiState.busy,
            icon = PkIcons.Check,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PetEditTestTags.SAVE),
        )
        if (!uiState.isNew) {
            PkButton(
                text = stringResource(
                    if (uiState.deleting) R.string.pet_editor_deleting else R.string.pet_editor_delete,
                ),
                onClick = callbacks.onDelete,
                style = PkButtonStyle.Danger,
                enabled = !uiState.busy,
                icon = PkIcons.Delete,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PetEditTestTags.DELETE),
            )
        }
    }
}

/** The devices that recognise pets by their face, each a checkbox row; or why there is none. */
@Composable
private fun DevicesCard(uiState: PetEditUiState, onToggle: (Long) -> Unit) {
    val colors = PkTheme.colors
    PkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.pet_editor_devices),
            style = PkTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(
                if (uiState.devices.isEmpty()) R.string.pet_editor_devices_none else R.string.pet_editor_devices_hint,
            ),
            style = PkTheme.typography.bodyMedium,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = PkTheme.spacing.xxs, bottom = PkTheme.spacing.xs),
        )
        uiState.devices.forEach { device -> DeviceRow(device, device.id in uiState.selected, onToggle) }
    }
}

@Composable
private fun DeviceRow(device: Device, checked: Boolean, onToggle: (Long) -> Unit) {
    val colors = PkTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(PkTheme.shapes.tile)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(device.id) })
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .testTag(PetEditTestTags.device(device.id)),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                checkmarkColor = colors.onAccent,
                uncheckedColor = colors.onSurfaceMuted,
            ),
        )
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            if (device.online) {
                device.modelName
            } else {
                stringResource(
                    R.string.pet_editor_device_offline,
                    device.modelName,
                )
            },
            style = PkTheme.typography.bodyLarge,
            color = colors.onSurface,
        )
    }
}

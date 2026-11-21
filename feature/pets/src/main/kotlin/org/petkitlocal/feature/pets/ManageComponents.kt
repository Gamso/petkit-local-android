package org.petkitlocal.feature.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.pets.PetsSource

/** A one-line field, as the sign-in form's: its problem, or its hint, said under it. */
@Composable
internal fun PetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supporting?.let {
            {
                Text(
                    it,
                    color = if (isError) PkTheme.colors.danger else PkTheme.colors.onSurfaceMuted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
        isError = isError,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = PkTheme.shapes.field,
        textStyle = PkTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth(),
    )
}

/** A write's failure (SPEC.md §6: every command reports the server's error), in the app's words. */
@Composable
internal fun writeErrorMessage(error: DomainError): String = when (error) {
    is DomainError.Network, is DomainError.Certificate -> stringResource(R.string.pet_error_unreachable)

    is DomainError.SessionEnded -> stringResource(R.string.pets_session_ended)

    is DomainError.NotFound -> when (error.code) {
        "pet_not_found" -> stringResource(R.string.pet_editor_gone)

        // A route the server does not have: one older than the app's pet management.
        "not_found" -> stringResource(R.string.pet_error_too_old)

        else -> stringResource(R.string.pet_error_server)
    }

    is DomainError.Validation -> when (error.code) {
        "photo_too_small" -> stringResource(R.string.pet_error_too_small)
        else -> stringResource(R.string.pet_error_refused, error.message.ifEmpty { error.code })
    }

    is DomainError.Server -> when (error.code) {
        "photo_too_large" -> stringResource(R.string.pet_error_too_large)

        "not_jpeg" -> stringResource(R.string.pet_error_not_jpeg)

        "photo_not_stored" -> stringResource(R.string.pet_error_not_stored)

        else -> error.message?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.pet_error_refused, it) }
            ?: stringResource(R.string.pet_error_server)
    }

    else -> stringResource(R.string.pet_error_server)
}

/**
 * "Pets for recognition" (petkit-local#130), shown while the server relays PetKit's cloud: whose
 * pets the cameras recognise, one choice per option the server lists, the one in use checked.
 * A choice is shown as taken only once the server has answered.
 */
@Composable
internal fun PetsSourceCard(uiState: PetsUiState, onSelect: (PetsSource) -> Unit, modifier: Modifier = Modifier) {
    val setting = uiState.petsSource ?: return
    val colors = PkTheme.colors
    PkCard(modifier.fillMaxWidth().testTag(PetsTestTags.SOURCE)) {
        Text(
            stringResource(R.string.pets_source_title),
            style = PkTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.pets_source_explanation),
            style = PkTheme.typography.bodyMedium,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = PkTheme.spacing.xxs, bottom = PkTheme.spacing.xs),
        )
        Column(Modifier.selectableGroup()) {
            setting.options.forEach { option ->
                SourceRow(
                    option,
                    selected = option == setting.source,
                    pending = option == uiState.settingSource,
                    onSelect,
                )
            }
        }
        if (setting.source == PetsSource.PetKit) {
            Row(Modifier.padding(top = PkTheme.spacing.xs), verticalAlignment = Alignment.Top) {
                Icon(PkIcons.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.pets_source_petkit_warning),
                    style = PkTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = PkTheme.spacing.xs),
                )
            }
        }
        uiState.sourceError?.let { error ->
            Text(
                stringResource(R.string.pets_source_failed, writeErrorMessage(error)),
                style = PkTheme.typography.bodyMedium,
                color = colors.danger,
                modifier = Modifier
                    .padding(top = PkTheme.spacing.xs)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun SourceRow(option: PetsSource, selected: Boolean, pending: Boolean, onSelect: (PetsSource) -> Unit) {
    val colors = PkTheme.colors
    val (label, detail) = when (option) {
        PetsSource.PetKit -> R.string.pets_source_petkit to R.string.pets_source_petkit_detail
        PetsSource.Local -> R.string.pets_source_local to R.string.pets_source_local_detail
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(PkTheme.shapes.tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(option) })
            .background(if (selected) colors.accentContainer else colors.surface)
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .padding(horizontal = PkTheme.spacing.s, vertical = PkTheme.spacing.xs)
            .testTag(PetsTestTags.source(option.name.lowercase())),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.accent, unselectedColor = colors.onSurfaceMuted),
        )
        Column(
            Modifier
                .padding(start = PkTheme.spacing.s)
                .weight(1f),
        ) {
            Text(
                stringResource(label),
                style = PkTheme.typography.bodyLarge,
                color = if (selected) colors.onAccentContainer else colors.onSurface,
            )
            Text(
                stringResource(detail),
                style = PkTheme.typography.labelMedium,
                color = if (selected) colors.onAccentContainer else colors.onSurfaceMuted,
            )
        }
        if (pending) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}

package org.petkitlocal.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.auth.SignInFailure

/** The three fields: the server's address, the password, and this phone's name (folded away). */
@Composable
internal fun SignInForm(uiState: SignInUiState, actions: SignInActions, focus: SignInFocus, submit: () -> Unit) {
    val addressError = (uiState.failure as? SignInFailure.InvalidAddress)?.problem
    val passwordError = when (uiState.failure) {
        SignInFailure.PasswordMissing -> stringResource(R.string.auth_error_password_missing)
        is SignInFailure.WrongPassword -> stringResource(R.string.auth_error_wrong_password)
        else -> null
    }
    PkCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
            PkTextField(
                value = uiState.address,
                onValueChange = actions.onAddressChange,
                label = stringResource(R.string.auth_address_label),
                placeholder = stringResource(R.string.auth_address_placeholder),
                supporting = when {
                    addressError != null -> addressProblemMessage(addressError)
                    uiState.plainHttpWarning -> stringResource(R.string.auth_plain_http_warning)
                    else -> null
                },
                isError = addressError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.focusRequester(focus.address).testTag(SignInTestTags.ADDRESS),
            )
            PkTextField(
                value = uiState.password,
                onValueChange = actions.onPasswordChange,
                label = stringResource(R.string.auth_password_label),
                supporting = passwordError,
                isError = passwordError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                visualTransformation = if (uiState.passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailing = { PasswordVisibilityButton(uiState.passwordVisible, actions.onTogglePasswordVisibility) },
                modifier = Modifier.focusRequester(focus.password).testTag(SignInTestTags.PASSWORD),
            )
            PhoneName(uiState, actions, submit)
        }
    }
}

/** The eye in the password field; TalkBack says what it will do. */
@Composable
internal fun PasswordVisibilityButton(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (visible) PkIcons.VisibilityOff else PkIcons.Visibility,
            contentDescription = stringResource(
                if (visible) R.string.auth_password_hide else R.string.auth_password_show,
            ),
            tint = PkTheme.colors.onSurfaceMuted,
        )
    }
}

/**
 * The name the panel lists this phone under. Prefilled with the model and rarely changed, so it is
 * one line until opened; opened, it is the field with what the name is for.
 */
@Composable
private fun PhoneName(uiState: SignInUiState, actions: SignInActions, submit: () -> Unit) {
    val expanded = uiState.phoneNameExpanded
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clip(PkTheme.shapes.field)
            .clickable(
                onClickLabel = stringResource(
                    if (expanded) R.string.auth_phone_name_close else R.string.auth_phone_name_edit,
                ),
                role = Role.Button,
                onClick = actions.onTogglePhoneName,
            )
            .testTag(SignInTestTags.PHONE_NAME_ROW),
    ) {
        Text(
            if (expanded) {
                stringResource(R.string.auth_phone_name_title)
            } else {
                stringResource(R.string.auth_phone_name_row, uiState.phoneName)
            },
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurfaceMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) PkIcons.ExpandLess else PkIcons.ExpandMore,
            contentDescription = null,
            tint = PkTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(20.dp),
        )
    }
    if (expanded) {
        PkTextField(
            value = uiState.phoneName,
            onValueChange = actions.onPhoneNameChange,
            label = stringResource(R.string.auth_phone_name_label),
            supporting = stringResource(R.string.auth_phone_name_help),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.testTag(SignInTestTags.PHONE_NAME),
        )
    }
}

@Suppress("LongParameterList") // A text field's own parameters, passed through.
@Composable
internal fun PkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = PkTheme.colors.onSurfaceMuted) } },
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
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        shape = PkTheme.shapes.field,
        textStyle = PkTheme.typography.bodyLarge,
        modifier = modifier.fillMaxWidth(),
    )
}

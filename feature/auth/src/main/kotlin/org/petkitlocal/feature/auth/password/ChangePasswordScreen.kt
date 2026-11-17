package org.petkitlocal.feature.auth.password

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkConfirmDialog
import org.petkitlocal.core.designsystem.component.PkPreviewSurface
import org.petkitlocal.core.designsystem.component.PkPreviews
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.auth.ChangePasswordFailure
import org.petkitlocal.core.domain.auth.ChangePasswordUseCase
import org.petkitlocal.feature.auth.ErrorCard
import org.petkitlocal.feature.auth.PasswordVisibilityButton
import org.petkitlocal.feature.auth.PkTextField
import org.petkitlocal.feature.auth.R
import org.petkitlocal.feature.auth.serverErrorMessage

/** The callbacks of [ChangePasswordScreen], one per user intent. */
internal class ChangePasswordActions(
    val onBack: () -> Unit = {},
    val onCurrentChange: (String) -> Unit = {},
    val onNewChange: (String) -> Unit = {},
    val onRepeatedChange: (String) -> Unit = {},
    val onToggleVisibility: () -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismissConfirm: () -> Unit = {},
)

@Composable
internal fun ChangePasswordRoute(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChangePasswordScreen(
        uiState,
        ChangePasswordActions(
            onBack = onBack,
            onCurrentChange = viewModel::onCurrentChange,
            onNewChange = viewModel::onNewChange,
            onRepeatedChange = viewModel::onRepeatedChange,
            onToggleVisibility = viewModel::onToggleVisibility,
            onSubmit = viewModel::onSubmit,
            onConfirm = viewModel::onConfirm,
            onDismissConfirm = viewModel::onDismissConfirm,
        ),
    )
}

/**
 * The server's one password, changed from this phone: the current one, then the new one twice.
 * Every other phone is signed out by it, which a dialog says before anything is sent.
 */
@Composable
internal fun ChangePasswordScreen(
    uiState: ChangePasswordUiState,
    actions: ChangePasswordActions,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m),
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(PkTheme.spacing.gutter)
            .testTag(ChangePasswordTestTags.SCREEN),
    ) {
        PkBackButton(onClick = actions.onBack)
        Text(
            stringResource(R.string.auth_change_password_title),
            style = PkTheme.typography.display,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        val signedOut = uiState.signedOutPhones
        if (signedOut != null) {
            Changed(signedOut, actions.onBack)
        } else {
            Text(
                stringResource(R.string.auth_change_password_intro),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
            )
            ChangePasswordForm(uiState, actions)
        }
    }
    if (uiState.confirming) {
        PkConfirmDialog(
            title = stringResource(R.string.auth_change_password_confirm_title),
            message = stringResource(R.string.auth_change_password_confirm_message),
            confirmLabel = stringResource(R.string.auth_change_password_submit),
            onConfirm = actions.onConfirm,
            onDismiss = actions.onDismissConfirm,
        )
    }
}

@Composable
private fun ChangePasswordForm(uiState: ChangePasswordUiState, actions: ChangePasswordActions) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        actions.onSubmit()
    }
    PasswordFields(uiState, actions, submit)
    generalError(uiState.failure)?.let { ErrorCard(it) }
    PkButton(
        text = submitLabel(uiState),
        onClick = submit,
        enabled = uiState.canSubmit,
        modifier = Modifier.fillMaxWidth().testTag(ChangePasswordTestTags.SUBMIT),
    )
}

@Composable
private fun submitLabel(uiState: ChangePasswordUiState): String = when {
    uiState.submitting -> stringResource(R.string.auth_change_password_submitting)

    uiState.lockedSeconds > 0 ->
        pluralStringResource(R.plurals.auth_retry_in, uiState.lockedSeconds, uiState.lockedSeconds)

    else -> stringResource(R.string.auth_change_password_submit)
}

/** The three fields, each with its own error under it. */
@Composable
private fun PasswordFields(uiState: ChangePasswordUiState, actions: ChangePasswordActions, submit: () -> Unit) {
    val failure = uiState.failure
    val transformation = if (uiState.passwordsVisible) VisualTransformation.None else PasswordVisualTransformation()
    val eye: @Composable () -> Unit = { PasswordVisibilityButton(uiState.passwordsVisible, actions.onToggleVisibility) }
    PkCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
            val currentError = currentFieldError(failure)
            PkTextField(
                value = uiState.current,
                onValueChange = actions.onCurrentChange,
                label = stringResource(R.string.auth_change_password_current),
                supporting = currentError,
                isError = currentError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                visualTransformation = transformation,
                trailing = eye,
                modifier = Modifier.testTag(ChangePasswordTestTags.CURRENT),
            )
            val newError = newFieldError(failure)
            PkTextField(
                value = uiState.new,
                onValueChange = actions.onNewChange,
                label = stringResource(R.string.auth_change_password_new),
                supporting = newError
                    ?: pluralStringResource(
                        R.plurals.auth_change_password_new_help,
                        ChangePasswordUseCase.MIN_LENGTH,
                        ChangePasswordUseCase.MIN_LENGTH,
                    ),
                isError = newError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                visualTransformation = transformation,
                trailing = eye,
                modifier = Modifier.testTag(ChangePasswordTestTags.NEW),
            )
            val repeatedError = if (failure == ChangePasswordFailure.Mismatch) {
                stringResource(R.string.auth_change_password_error_mismatch)
            } else {
                null
            }
            PkTextField(
                value = uiState.repeated,
                onValueChange = actions.onRepeatedChange,
                label = stringResource(R.string.auth_change_password_repeat),
                supporting = repeatedError,
                isError = repeatedError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                visualTransformation = transformation,
                trailing = eye,
                modifier = Modifier.testTag(ChangePasswordTestTags.REPEAT),
            )
        }
    }
}

/** The password changed: what that did to the other phones, and the way back. */
@Composable
private fun Changed(signedOutPhones: Int, onDone: () -> Unit) {
    PkCard(Modifier.fillMaxWidth().testTag(ChangePasswordTestTags.DONE_CARD)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                stringResource(R.string.auth_change_password_done),
                style = PkTheme.typography.titleMedium,
                color = PkTheme.colors.onSurface,
            )
            Text(
                if (signedOutPhones == 0) {
                    stringResource(R.string.auth_change_password_done_none)
                } else {
                    pluralStringResource(R.plurals.auth_change_password_done_others, signedOutPhones, signedOutPhones)
                },
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
            )
        }
    }
    PkButton(
        stringResource(R.string.auth_change_password_back),
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().testTag(ChangePasswordTestTags.DONE),
    )
}

@Composable
private fun currentFieldError(failure: ChangePasswordFailure?): String? = when (failure) {
    ChangePasswordFailure.CurrentMissing -> stringResource(R.string.auth_change_password_error_current_missing)
    is ChangePasswordFailure.WrongCurrent -> stringResource(R.string.auth_change_password_error_wrong_current)
    else -> null
}

@Composable
private fun newFieldError(failure: ChangePasswordFailure?): String? = when (failure) {
    ChangePasswordFailure.NewTooShort ->
        pluralStringResource(
            R.plurals.auth_change_password_error_too_short,
            ChangePasswordUseCase.MIN_LENGTH,
            ChangePasswordUseCase.MIN_LENGTH,
        )

    ChangePasswordFailure.NewTooLong ->
        pluralStringResource(
            R.plurals.auth_change_password_error_too_long,
            ChangePasswordUseCase.MAX_LENGTH,
            ChangePasswordUseCase.MAX_LENGTH,
        )

    is ChangePasswordFailure.Rejected -> stringResource(R.string.auth_error_server_message, failure.message)

    else -> null
}

/** The failures that belong to no single field. */
@Composable
private fun generalError(failure: ChangePasswordFailure?): String? = when (failure) {
    is ChangePasswordFailure.LockedOut -> stringResource(R.string.auth_error_locked_out)
    ChangePasswordFailure.Unreachable -> stringResource(R.string.auth_change_password_error_unreachable)
    ChangePasswordFailure.SessionEnded -> stringResource(R.string.auth_change_password_error_session_ended)
    is ChangePasswordFailure.ServerError -> serverErrorMessage(failure.status, failure.message)
    else -> null
}

/** Tags for UI tests and the end-to-end flows. */
object ChangePasswordTestTags {
    const val SCREEN = "change_password"
    const val CURRENT = "change_password_current"
    const val NEW = "change_password_new"
    const val REPEAT = "change_password_repeat"
    const val SUBMIT = "change_password_submit"
    const val DONE_CARD = "change_password_done_card"
    const val DONE = "change_password_done"
}

@PkPreviews
@Composable
private fun ChangePasswordPreview() = PkPreviewSurface {
    ChangePasswordScreen(
        ChangePasswordUiState(current = "demo", failure = ChangePasswordFailure.NewTooShort),
        ChangePasswordActions(),
    )
}

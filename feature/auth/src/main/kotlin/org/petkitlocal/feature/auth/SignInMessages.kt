package org.petkitlocal.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.domain.auth.AddressProblem
import org.petkitlocal.core.domain.auth.ServerInfo
import org.petkitlocal.core.domain.auth.SignInFailure

/* What the sign-in screen says, for each state and each way signing in can fail (SPEC.md §5.1). */

@Composable
internal fun submitLabel(uiState: SignInUiState): String = when {
    uiState.submitting -> stringResource(R.string.auth_submitting)

    uiState.lockedSeconds > 0 -> pluralStringResource(
        R.plurals.auth_retry_in,
        uiState.lockedSeconds,
        uiState.lockedSeconds,
    )

    else -> stringResource(R.string.auth_submit)
}

@Composable
internal fun addressProblemMessage(problem: AddressProblem): String = stringResource(
    when (problem) {
        AddressProblem.Empty -> R.string.auth_error_address_empty
        AddressProblem.Malformed -> R.string.auth_error_address_malformed
        AddressProblem.UnsupportedScheme -> R.string.auth_error_address_scheme
        AddressProblem.HttpsRequired -> R.string.auth_error_address_https_required
    },
)

/** The failures that belong to no single field. */
@Composable
internal fun generalErrorMessage(failure: SignInFailure?): String? = when (failure) {
    null,
    is SignInFailure.InvalidAddress,
    SignInFailure.PasswordMissing,
    is SignInFailure.WrongPassword,
    SignInFailure.LocalNetworkDenied,
    is SignInFailure.Certificate,
    -> null

    SignInFailure.NotPetkitLocal -> stringResource(R.string.auth_error_not_petkit_local)

    SignInFailure.PasswordNotSet -> stringResource(R.string.auth_error_password_not_set)

    is SignInFailure.IncompatibleServer ->
        stringResource(R.string.auth_error_incompatible, failure.apiVersion, ServerInfo.SUPPORTED_API_VERSION)

    is SignInFailure.LockedOut -> stringResource(R.string.auth_error_locked_out)

    is SignInFailure.Unreachable -> stringResource(R.string.auth_error_unreachable)

    is SignInFailure.ServerError -> serverErrorMessage(failure.status, failure.message)
}

@Composable
internal fun serverErrorMessage(status: Int?, message: String?): String = when {
    !message.isNullOrBlank() -> stringResource(R.string.auth_error_server_message, message)
    status != null -> stringResource(R.string.auth_error_server_status, status)
    else -> stringResource(R.string.auth_error_server)
}

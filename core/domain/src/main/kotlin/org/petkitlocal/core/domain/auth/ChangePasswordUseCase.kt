package org.petkitlocal.core.domain.auth

import javax.inject.Inject
import kotlin.time.Duration
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.UnauthorizedReason

/** Every way changing the password can fail, each shown where it belongs on the form. */
sealed interface ChangePasswordFailure {
    /** Nothing typed in the current password's field. */
    data object CurrentMissing : ChangePasswordFailure

    /** The new password is shorter than [ChangePasswordUseCase.MIN_LENGTH]; empty included. */
    data object NewTooShort : ChangePasswordFailure

    /** The new password is longer than [ChangePasswordUseCase.MAX_LENGTH]. */
    data object NewTooLong : ChangePasswordFailure

    /** The repeated new password is not the same as the new one. */
    data object Mismatch : ChangePasswordFailure

    /** The current password is wrong; [retryAfter] is the server's lockout before the next attempt. */
    data class WrongCurrent(val retryAfter: Duration?) : ChangePasswordFailure

    /** HTTP 429: too many wrong passwords from this address. */
    data class LockedOut(val retryAfter: Duration?) : ChangePasswordFailure

    /** The server refused the new password, saying why (400 `password_rejected`). */
    data class Rejected(val message: String) : ChangePasswordFailure

    /** No answer: offline, DNS, TLS, timeout. */
    data object Unreachable : ChangePasswordFailure

    /** The session ended meanwhile; the app returns to sign-in on its own. */
    data object SessionEnded : ChangePasswordFailure

    /** The server failed, or answered something else. */
    data class ServerError(val status: Int?, val message: String?) : ChangePasswordFailure
}

sealed interface ChangePasswordResult {
    /** Changed; [signedOutPhones] other phones were signed out. This one stays signed in. */
    data class Success(val signedOutPhones: Int) : ChangePasswordResult

    data class Failure(val failure: ChangePasswordFailure) : ChangePasswordResult
}

/**
 * Changes the server's password (SPEC.md §5.8): it needs the current one, and signs out every
 * other phone. The new one is typed twice, since a typo would lock every phone out. Nothing is
 * stored: both passwords are dropped once sent.
 */
class ChangePasswordUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(current: String, new: String, repeated: String): ChangePasswordResult {
        checkForm(current, new, repeated)?.let { return ChangePasswordResult.Failure(it) }
        return when (val result = repository.changePassword(current, new)) {
            is Outcome.Success -> ChangePasswordResult.Success(result.value)
            is Outcome.Failure -> ChangePasswordResult.Failure(failureOf(result.error))
        }
    }

    private fun failureOf(error: DomainError): ChangePasswordFailure = when (error) {
        is DomainError.Unauthorized -> if (error.reason == UnauthorizedReason.InvalidPassword) {
            ChangePasswordFailure.WrongCurrent(error.retryAfter)
        } else {
            ChangePasswordFailure.ServerError(status = UNAUTHORIZED, message = null)
        }

        is DomainError.RateLimited -> ChangePasswordFailure.LockedOut(error.retryAfter)

        is DomainError.Validation -> ChangePasswordFailure.Rejected(error.message)

        is DomainError.Network, is DomainError.Certificate -> ChangePasswordFailure.Unreachable

        is DomainError.SessionEnded -> ChangePasswordFailure.SessionEnded

        is DomainError.Server -> ChangePasswordFailure.ServerError(error.status, error.message)

        else -> ChangePasswordFailure.ServerError(status = null, message = null)
    }

    companion object {
        /** `ChangePasswordRequest.new_password`: 8 to 1024 characters (api/openapi.yaml). */
        const val MIN_LENGTH = 8
        const val MAX_LENGTH = 1024
        private const val UNAUTHORIZED = 401

        /**
         * What is wrong with the form before anything is sent, top field first; null when it can
         * be sent. The screen calls it on submit and puts the message under that field.
         */
        fun checkForm(current: String, new: String, repeated: String): ChangePasswordFailure? = when {
            current.isEmpty() -> ChangePasswordFailure.CurrentMissing
            new.length < MIN_LENGTH -> ChangePasswordFailure.NewTooShort
            new.length > MAX_LENGTH -> ChangePasswordFailure.NewTooLong
            repeated != new -> ChangePasswordFailure.Mismatch
            else -> null
        }
    }
}

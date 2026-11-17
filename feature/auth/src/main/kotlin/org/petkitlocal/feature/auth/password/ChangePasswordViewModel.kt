package org.petkitlocal.feature.auth.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.auth.ChangePasswordFailure
import org.petkitlocal.core.domain.auth.ChangePasswordResult
import org.petkitlocal.core.domain.auth.ChangePasswordUseCase

/** What the change-password screen shows (SPEC.md §5.8). */
data class ChangePasswordUiState(
    val current: String = "",
    val new: String = "",
    val repeated: String = "",
    val passwordsVisible: Boolean = false,
    /** The form passed its checks: the dialog says every other phone will be signed out. */
    val confirming: Boolean = false,
    val submitting: Boolean = false,
    /** The last attempt's failure, shown where it belongs; null once the user edits that field. */
    val failure: ChangePasswordFailure? = null,
    /** Seconds before the server checks a password again (its `Retry-After`), counting down. */
    val lockedSeconds: Int = 0,
    /** Changed: how many other phones were signed out. Null until then. */
    val signedOutPhones: Int? = null,
) {
    /**
     * The button stays enabled for an empty or wrong field (pressing it says what is wrong, under
     * the field); it waits only while the request is in flight and while the server says to wait.
     */
    val canSubmit: Boolean
        get() = !submitting && lockedSeconds == 0
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(private val changePassword: ChangePasswordUseCase) : ViewModel() {
    private val state = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = state.asStateFlow()
    private var countdown: Job? = null

    fun onCurrentChange(value: String) = state.update {
        it.copy(current = value, failure = it.failure.unless(CURRENT_FIELD))
    }

    fun onNewChange(value: String) = state.update { it.copy(new = value, failure = it.failure.unless(NEW_FIELDS)) }

    fun onRepeatedChange(value: String) = state.update {
        it.copy(repeated = value, failure = it.failure.unless(NEW_FIELDS))
    }

    fun onToggleVisibility() = state.update { it.copy(passwordsVisible = !it.passwordsVisible) }

    /** Checks the form; when it passes, asks for confirmation before anything is sent. */
    fun onSubmit() {
        val current = state.value
        if (!current.canSubmit) return
        val problem = ChangePasswordUseCase.checkForm(current.current, current.new, current.repeated)
        state.update { it.copy(failure = problem, confirming = problem == null) }
    }

    fun onDismissConfirm() = state.update { it.copy(confirming = false) }

    fun onConfirm() {
        val form = state.value
        if (!form.confirming || form.submitting) return
        state.update { it.copy(confirming = false, submitting = true, failure = null) }
        viewModelScope.launch {
            when (val result = changePassword(form.current, form.new, form.repeated)) {
                // Nothing typed is kept once the password changed.
                is ChangePasswordResult.Success ->
                    state.value =
                        ChangePasswordUiState(signedOutPhones = result.signedOutPhones)

                is ChangePasswordResult.Failure -> onFailure(result.failure)
            }
        }
    }

    private fun onFailure(failure: ChangePasswordFailure) {
        // A wrong current password is typed again; the new one stays for the retry.
        val wrong = failure is ChangePasswordFailure.WrongCurrent
        state.update {
            it.copy(submitting = false, failure = failure, current = if (wrong) "" else it.current)
        }
        val wait = when (failure) {
            is ChangePasswordFailure.WrongCurrent -> failure.retryAfter
            is ChangePasswordFailure.LockedOut -> failure.retryAfter
            else -> null
        }
        if (wait != null && wait.isPositive()) startCountdown(wait)
    }

    private fun startCountdown(wait: Duration) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var left = ceil(wait / 1.seconds).toInt()
            while (left > 0) {
                state.update { it.copy(lockedSeconds = left) }
                delay(1.seconds)
                left--
            }
            // The lockout is over: its message goes; a wrong password stays said until edited.
            state.update {
                it.copy(
                    lockedSeconds = 0,
                    failure = it.failure.takeUnless { f ->
                        f is ChangePasswordFailure.LockedOut
                    },
                )
            }
        }
    }

    private fun ChangePasswordFailure?.unless(fields: Set<Class<out ChangePasswordFailure>>): ChangePasswordFailure? =
        this?.takeUnless { failure -> fields.any { it.isInstance(failure) } }

    private companion object {
        /** The failures said under the current password, which editing it clears. */
        val CURRENT_FIELD = setOf(
            ChangePasswordFailure.CurrentMissing::class.java,
            ChangePasswordFailure.WrongCurrent::class.java,
        )

        /** The failures said under the new password or its repetition. */
        val NEW_FIELDS = setOf(
            ChangePasswordFailure.NewTooShort::class.java,
            ChangePasswordFailure.NewTooLong::class.java,
            ChangePasswordFailure.Mismatch::class.java,
            ChangePasswordFailure.Rejected::class.java,
        )
    }
}

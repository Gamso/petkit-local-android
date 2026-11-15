package org.petkitlocal.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AddressCheck
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.GetSignInDefaultsUseCase
import org.petkitlocal.core.domain.auth.LocalNetworkAccess
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.ServerAddresses
import org.petkitlocal.core.domain.auth.SignInFailure
import org.petkitlocal.core.domain.auth.SignInResult
import org.petkitlocal.core.domain.auth.SignInUseCase

/** What the sign-in screen shows (SPEC.md §5.1). */
data class SignInUiState(
    val address: String = "",
    val password: String = "",
    val phoneName: String = "",
    val passwordVisible: Boolean = false,
    val submitting: Boolean = false,
    /** The last attempt's failure, shown in place; null once the user edits the form. */
    val failure: SignInFailure? = null,
    /** Seconds before the server takes another attempt (its `Retry-After`), counting down. */
    val lockedSeconds: Int = 0,
    /** The typed address is plain HTTP on the LAN: allowed, with a warning. */
    val plainHttpWarning: Boolean = false,
    /** Why the last session ended, when the server ended it. */
    val sessionEnded: SessionEndReason? = null,
    /** Android must be asked for local network access first: why, and a button that asks. */
    val localNetworkRationale: Boolean = false,
    /** The phone's name is shown as one line until the user opens it to change it. */
    val phoneNameExpanded: Boolean = false,
) {
    /**
     * The button stays enabled for an empty or wrong field (pressing it says what is missing, under
     * the field); it waits only while a request is in flight and while the server locks sign-in.
     */
    val canSubmit: Boolean
        get() = !submitting && lockedSeconds == 0
}

/** The fields the screen can move the focus to. */
enum class SignInField { Address, Password }

/** One-shot requests to the screen. */
sealed interface SignInEffect {
    /** Show Android's `ACCESS_LOCAL_NETWORK` prompt; answer with [SignInViewModel.onLocalNetworkResult]. */
    data object RequestLocalNetworkPermission : SignInEffect

    /** Put the cursor in [field], whose error is now shown under it. */
    data class Focus(val field: SignInField) : SignInEffect
}

// One function per user intent (ARCHITECTURE.md §3), and the form has many.
@Suppress("TooManyFunctions")
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val defaults: GetSignInDefaultsUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val localNetwork: LocalNetworkAccess,
) : ViewModel() {
    private val form = MutableStateFlow(SignInUiState())
    private var countdown: Job? = null
    private val effectChannel = Channel<SignInEffect>(Channel.BUFFERED)

    /** Collected once by the screen. */
    val effects: Flow<SignInEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<SignInUiState> = combine(form, observeAuthState()) { form, auth ->
        form.copy(sessionEnded = (auth as? AuthState.SignedOut)?.reason)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SignInUiState())

    init {
        viewModelScope.launch {
            val prefill = defaults()
            form.update {
                it.copy(
                    address = it.address.ifEmpty { prefill.serverAddress },
                    phoneName = it.phoneName.ifEmpty { prefill.phoneName },
                ).withWarning()
            }
        }
    }

    fun onAddressChange(address: String) = form.update {
        it.copy(address = address, failure = null, localNetworkRationale = false).withWarning()
    }

    fun onPasswordChange(password: String) = form.update { it.copy(password = password, failure = null) }

    fun onPhoneNameChange(name: String) = form.update { it.copy(phoneName = name) }

    fun onTogglePasswordVisibility() = form.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onTogglePhoneName() = form.update { it.copy(phoneNameExpanded = !it.phoneNameExpanded) }

    fun onSubmit() {
        val current = form.value
        if (!current.canSubmit) return
        val incomplete = SignInUseCase.checkForm(current.address, current.password)
        if (incomplete != null) {
            onFailure(incomplete)
            return
        }
        if (current.isOnLan() && localNetwork.permissionNeeded()) {
            // Ask with a reason first: Android's own prompt does not say why the app needs it.
            form.update { it.copy(localNetworkRationale = true, failure = null) }
            return
        }
        submit(current)
    }

    fun onAllowLocalNetworkClick() {
        effectChannel.trySend(SignInEffect.RequestLocalNetworkPermission)
    }

    fun onLocalNetworkResult(granted: Boolean) {
        form.update { it.copy(localNetworkRationale = false) }
        if (granted) onSubmit() else form.update { it.copy(failure = SignInFailure.LocalNetworkDenied) }
    }

    private fun submit(current: SignInUiState) {
        form.update { it.copy(submitting = true, failure = null) }
        viewModelScope.launch {
            val result = signIn(current.address, current.password, current.phoneName)
            // On success the app's navigation follows the session to Home. The password is
            // dropped then, and when it was wrong; any other failure keeps it for the retry.
            val failure = (result as? SignInResult.Failure)?.failure
            val keepPassword = failure != null && failure !is SignInFailure.WrongPassword
            form.update {
                it.copy(submitting = false, password = if (keepPassword) it.password else "", passwordVisible = false)
            }
            if (failure != null) onFailure(failure)
        }
    }

    private fun onFailure(failure: SignInFailure) {
        form.update { it.copy(failure = failure) }
        focusFieldOf(failure)?.let { effectChannel.trySend(SignInEffect.Focus(it)) }
        val wait = when (failure) {
            is SignInFailure.WrongPassword -> failure.retryAfter
            is SignInFailure.LockedOut -> failure.retryAfter
            else -> null
        }
        if (wait != null && wait.isPositive()) startCountdown(wait)
    }

    private fun startCountdown(wait: Duration) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var left = ceil(wait / 1.seconds).toInt()
            while (left > 0) {
                form.update { it.copy(lockedSeconds = left) }
                delay(1.seconds)
                left--
            }
            // The lockout is over: its message goes; a wrong password stays said until edited.
            form.update {
                it.copy(lockedSeconds = 0, failure = it.failure.takeUnless { f -> f is SignInFailure.LockedOut })
            }
        }
    }

    private fun focusFieldOf(failure: SignInFailure): SignInField? = when (failure) {
        is SignInFailure.InvalidAddress -> SignInField.Address
        SignInFailure.PasswordMissing, is SignInFailure.WrongPassword -> SignInField.Password
        else -> null
    }

    private fun SignInUiState.isOnLan(): Boolean {
        val check = ServerAddresses.normalise(address)
        return check is AddressCheck.Valid && ServerAddresses.isPrivateLanHost(check.address.host)
    }

    private fun SignInUiState.withWarning(): SignInUiState {
        val check = ServerAddresses.normalise(address)
        return copy(plainHttpWarning = check is AddressCheck.Valid && check.address.plainHttpOnLan)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

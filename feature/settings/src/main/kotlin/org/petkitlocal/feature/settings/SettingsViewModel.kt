package org.petkitlocal.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.SignOutUseCase

/**
 * What the settings screen shows. M1 brings signing out; the server's details, notifications,
 * theme and the password change come with M5 (docs/ROADMAP.md). The language is the phone's.
 */
data class SettingsUiState(
    /** The server this phone is signed in to. */
    val server: String? = null,
    val confirmingSignOut: Boolean = false,
    val signingOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val signOut: SignOutUseCase,
    observeAuthState: ObserveAuthStateUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(local, observeAuthState()) { local, auth ->
            local.copy(server = (auth as? AuthState.SignedIn)?.serverBaseUrl)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun onSignOutClick() = local.update { it.copy(confirmingSignOut = true) }

    fun onSignOutDismiss() = local.update { it.copy(confirmingSignOut = false) }

    /** The app's navigation follows the session back to sign-in once it is cleared. */
    fun onSignOutConfirm() {
        if (local.value.signingOut) return
        local.update { it.copy(confirmingSignOut = false, signingOut = true) }
        viewModelScope.launch {
            signOut()
            local.update { it.copy(signingOut = false) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

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
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.GetServerInfoUseCase
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.SignOutUseCase
import org.petkitlocal.core.domain.settings.AppVersionProvider
import org.petkitlocal.core.domain.settings.GetNotificationSettingsUseCase
import org.petkitlocal.core.domain.settings.NotificationSettings
import org.petkitlocal.core.domain.settings.SetNotificationKindUseCase

/** Something read from the server: not yet, read, or not readable now. */
sealed interface Loaded<out T> {
    data object Loading : Loaded<Nothing>

    data class Ready<T>(val value: T) : Loaded<T>

    data object Failed : Loaded<Nothing>
}

/** What `GET /info` said: the server's version and this phone's name there. */
data class ServerDetails(val version: String?, val phoneName: String?)

/**
 * What the settings screen shows (SPEC.md §5.8). There is no language or theme here: the app
 * follows the phone's.
 */
data class SettingsUiState(
    /** The server this phone is signed in to. */
    val server: String? = null,
    val details: Loaded<ServerDetails> = Loaded.Loading,
    val notifications: Loaded<NotificationSettings> = Loaded.Loading,
    /** The kind whose switch was just flipped, while the server has not answered. */
    val savingKind: String? = null,
    /** The last switch flip was refused or could not reach the server; nothing changed. */
    val kindNotSaved: Boolean = false,
    /** This build's version, for About. */
    val appVersion: String = "",
    val confirmingSignOut: Boolean = false,
    val signingOut: Boolean = false,
)

@Suppress("LongParameterList") // One use case per thing the screen shows or does.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val signOut: SignOutUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val serverInfo: GetServerInfoUseCase,
    private val notificationSettings: GetNotificationSettingsUseCase,
    private val setNotificationKind: SetNotificationKindUseCase,
    appVersion: AppVersionProvider,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState(appVersion = appVersion.versionName()))

    val uiState: StateFlow<SettingsUiState> =
        combine(local, observeAuthState()) { local, auth ->
            local.copy(server = (auth as? AuthState.SignedIn)?.serverBaseUrl)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        load()
    }

    /** Reads again what could not be read: the server's details and the notification kinds. */
    fun onRetry() = load()

    /**
     * Flips one notification kind. The switch shows what the server answered, not the tap
     * (ADR 0002): it moves once the server holds the new choice. One write at a time.
     */
    fun onKindToggle(kind: String, on: Boolean) {
        val state = local.value
        val current = (state.notifications as? Loaded.Ready)?.value ?: return
        if (state.savingKind != null) return
        local.update { it.copy(savingKind = kind, kindNotSaved = false) }
        viewModelScope.launch {
            val result = setNotificationKind(current, kind, on)
            local.update {
                when (result) {
                    is Outcome.Success -> it.copy(savingKind = null, notifications = Loaded.Ready(result.value))
                    is Outcome.Failure -> it.copy(savingKind = null, kindNotSaved = true)
                }
            }
        }
    }

    fun onSignOutClick() = local.update { it.copy(confirmingSignOut = true) }

    fun onSignOutDismiss() = local.update { it.copy(confirmingSignOut = false) }

    /**
     * The app's navigation follows the session back to sign-in once it is cleared. What is kept
     * per server (the quick actions) survives it; only a sign-in to another server clears that.
     */
    fun onSignOutConfirm() {
        if (local.value.signingOut) return
        local.update { it.copy(confirmingSignOut = false, signingOut = true) }
        viewModelScope.launch {
            signOut()
            local.update { it.copy(signingOut = false) }
        }
    }

    private fun load() {
        val state = local.value
        if (state.details !is Loaded.Ready) {
            local.update { it.copy(details = Loaded.Loading) }
            viewModelScope.launch {
                val details = when (val info = serverInfo()) {
                    is Outcome.Success -> Loaded.Ready(
                        ServerDetails(info.value.serverVersion, info.value.session?.name),
                    )

                    is Outcome.Failure -> Loaded.Failed
                }
                local.update { it.copy(details = details) }
            }
        }
        if (state.notifications !is Loaded.Ready) {
            local.update { it.copy(notifications = Loaded.Loading) }
            viewModelScope.launch {
                val notifications = when (val settings = notificationSettings()) {
                    is Outcome.Success -> Loaded.Ready(settings.value)
                    is Outcome.Failure -> Loaded.Failed
                }
                local.update { it.copy(notifications = notifications) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

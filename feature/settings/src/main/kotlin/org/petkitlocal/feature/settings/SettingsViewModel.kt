package org.petkitlocal.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.GetServerInfoUseCase
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.SignOutUseCase
import org.petkitlocal.core.domain.notifications.MarkNotificationPromptUseCase
import org.petkitlocal.core.domain.notifications.NotificationPrompt
import org.petkitlocal.core.domain.notifications.ObservePhoneNotificationsUseCase
import org.petkitlocal.core.domain.notifications.PhoneNotifications
import org.petkitlocal.core.domain.notifications.SetNotificationsEnabledUseCase
import org.petkitlocal.core.domain.settings.AppVersionProvider
import org.petkitlocal.core.domain.settings.GetAppLicenceUseCase
import org.petkitlocal.core.domain.settings.GetNotificationSettingsUseCase
import org.petkitlocal.core.domain.settings.NotificationSettings
import org.petkitlocal.core.domain.settings.OpenSourceLicence
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
    /** Notifications on this phone, and what Android lets them do; null while it is read. */
    val phone: PhoneNotifications? = null,
    /** The one-time explanation before Android's battery-optimisation request. */
    val explainingBattery: Boolean = false,
    /** The kind whose switch was just flipped, while the server has not answered. */
    val savingKind: String? = null,
    /** The last switch flip was refused or could not reach the server; nothing changed. */
    val kindNotSaved: Boolean = false,
    /** This build's version, for About. */
    val appVersion: String = "",
    /** The app's own licence, read from the APK; null for the few milliseconds that takes. */
    val appLicence: OpenSourceLicence? = null,
    /** The app's licence text is open in a dialog. */
    val showingAppLicence: Boolean = false,
    val confirmingSignOut: Boolean = false,
    val signingOut: Boolean = false,
)

/** What the screen asks Android for, once each time. */
sealed interface SettingsEffect {
    /**
     * Android 13+'s notification permission. [askedBefore]: Android shows no prompt once it was
     * refused for good, so the screen then opens the app's notification settings instead.
     */
    data class RequestNotificationPermission(val askedBefore: Boolean) : SettingsEffect

    /** Android's own dialog exempting the app from battery optimisation. */
    data object RequestBatteryExemption : SettingsEffect
}

@Suppress("LongParameterList", "TooManyFunctions") // One use case per thing the screen shows or does.
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val signOut: SignOutUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val serverInfo: GetServerInfoUseCase,
    private val notificationSettings: GetNotificationSettingsUseCase,
    private val setNotificationKind: SetNotificationKindUseCase,
    observePhoneNotifications: ObservePhoneNotificationsUseCase,
    private val setNotificationsEnabled: SetNotificationsEnabledUseCase,
    private val markPrompt: MarkNotificationPromptUseCase,
    appVersion: AppVersionProvider,
    appLicence: GetAppLicenceUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState(appVersion = appVersion.versionName()))

    /** Emits when the screen comes back to the front: Android's settings may have changed. */
    private val recheck = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val phone: Flow<PhoneNotifications> = observePhoneNotifications(recheck)

    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(local, observeAuthState(), phone) { local, auth, phone ->
            local.copy(server = (auth as? AuthState.SignedIn)?.serverBaseUrl, phone = phone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        load()
        viewModelScope.launch {
            val licence = appLicence()
            local.update { it.copy(appLicence = licence) }
        }
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

    /** Back in front: the user may have changed the permission or the battery setting meanwhile. */
    fun onResume() {
        recheck.tryEmit(Unit)
    }

    /**
     * Turns notifications on or off for this phone; the service follows. Turned on, what they need
     * from Android is asked for at once, with its reason: the permission, then, once, the battery.
     */
    fun onNotificationsToggle(on: Boolean) {
        viewModelScope.launch {
            setNotificationsEnabled(on)
            if (!on) return@launch
            val phone = phone.first()
            when {
                phone.permissionNeeded -> effectChannel.trySend(
                    SettingsEffect.RequestNotificationPermission(askedBefore = phone.asked.permission),
                )

                phone.batteryOptimised && !phone.asked.battery -> local.update { it.copy(explainingBattery = true) }
            }
        }
    }

    /** "Allow notifications", next to the switch while Android does not let them show. */
    fun onAllowNotificationsClick() {
        viewModelScope.launch {
            val asked = phone.first().asked.permission
            effectChannel.trySend(SettingsEffect.RequestNotificationPermission(askedBefore = asked))
        }
    }

    /** Android answered the permission request; the battery comes next, if never asked. */
    fun onNotificationPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            markPrompt(NotificationPrompt.Permission)
            recheck.tryEmit(Unit)
            val phone = phone.first()
            if (granted && phone.batteryOptimised && !phone.asked.battery) {
                local.update { it.copy(explainingBattery = true) }
            }
        }
    }

    /** "Allow in the background", next to the switch while battery optimisation applies. */
    fun onAllowBackgroundClick() = local.update { it.copy(explainingBattery = true) }

    fun onBatteryExplanationConfirm() {
        local.update { it.copy(explainingBattery = false) }
        viewModelScope.launch { markPrompt(NotificationPrompt.Battery) }
        effectChannel.trySend(SettingsEffect.RequestBatteryExemption)
    }

    /** Not now: it is not asked again by itself; the button next to the switch stays. */
    fun onBatteryExplanationDismiss() {
        local.update { it.copy(explainingBattery = false) }
        viewModelScope.launch { markPrompt(NotificationPrompt.Battery) }
    }

    fun onAppLicenceClick() = local.update { it.copy(showingAppLicence = it.appLicence != null) }

    fun onAppLicenceDismiss() = local.update { it.copy(showingAppLicence = false) }

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

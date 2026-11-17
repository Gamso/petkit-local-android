package org.petkitlocal.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.home.HomeOverview
import org.petkitlocal.core.domain.home.ObserveHomeUseCase
import org.petkitlocal.core.domain.home.RefreshHomeUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.notifications.MarkNotificationPromptUseCase
import org.petkitlocal.core.domain.notifications.NotificationPrompt
import org.petkitlocal.core.domain.notifications.ObserveNotificationBannerUseCase
import org.petkitlocal.core.domain.notifications.ObservePhoneNotificationsUseCase

/**
 * What Home shows. [overview] is the cache, null only until it has been read once; the refresh
 * fields say how the last fetch from the server went, never what the devices are doing.
 */
data class HomeUiState(
    val overview: HomeOverview? = null,
    val refreshing: Boolean = false,
    /** Why the last refresh failed; null after a successful one. */
    val refreshError: DomainError? = null,
    /** Notifications are on, but Android 13+ does not let them show: the banner asking for it. */
    val askNotifications: Boolean = false,
) {
    /** The server could not be reached: what is on screen is the last known data. */
    val offline: Boolean get() = refreshError is DomainError.Network
}

/**
 * Home observes the cache only (ADR 0002), so it shows the last known data at once and whether
 * or not the server answers; it refreshes when it opens and when the user pulls.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHome: ObserveHomeUseCase,
    private val refreshHome: RefreshHomeUseCase,
    private val today: TodayUseCase,
    observeNotificationBanner: ObserveNotificationBannerUseCase,
    private val observePhoneNotifications: ObservePhoneNotificationsUseCase,
    private val markPrompt: MarkNotificationPromptUseCase,
) : ViewModel() {
    /** The local day being counted; it moves on at each refresh, so a new day is picked up. */
    private val day = MutableStateFlow(today())
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<DomainError?>(null)

    /** Emits when Home comes back to the front: the permission may have been granted meanwhile. */
    private val recheck = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val permissionRequestChannel = Channel<Boolean>(Channel.BUFFERED)

    /** Asks for the notification permission; the value says whether it was asked before. */
    val permissionRequests: Flow<Boolean> = permissionRequestChannel.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        day.flatMapLatest { observeHome(it) },
        refreshing,
        refreshError,
        observeNotificationBanner(recheck),
    ) { overview, refreshing, error, askNotifications ->
        HomeUiState(
            overview = overview,
            refreshing = refreshing,
            refreshError = error,
            askNotifications = askNotifications,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        onRefresh()
    }

    /** Pull to refresh, or retry. A refresh already running is not started twice. */
    fun onRefresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            day.value = today()
            val result = refreshHome(day.value)
            refreshError.value = (result as? Outcome.Failure)?.error
            refreshing.value = false
        }
    }

    /** Back in front: the permission may have been granted in Android's settings meanwhile. */
    fun onResume() {
        recheck.tryEmit(Unit)
    }

    /** The banner's "Allow": Android's prompt, or its settings once the permission was refused for good. */
    fun onAllowNotificationsClick() {
        viewModelScope.launch {
            val askedBefore = observePhoneNotifications(recheck).first().asked.permission
            permissionRequestChannel.trySend(askedBefore)
        }
    }

    /** Android answered; granted, the banner goes. */
    fun onNotificationPermissionResult(@Suppress("UNUSED_PARAMETER") granted: Boolean) {
        viewModelScope.launch {
            markPrompt(NotificationPrompt.Permission)
            recheck.tryEmit(Unit)
        }
    }

    /** "Not now": the banner goes for the rest of the session; Settings keeps its prompt. */
    fun onNotificationsNotNow() {
        viewModelScope.launch { markPrompt(NotificationPrompt.HomeBanner) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package org.petkitlocal.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.ValidateSessionUseCase
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus

/** The activity's own state: the session the navigation follows, and the live updates. */
@HiltViewModel
class AppViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    private val validateSession: ValidateSessionUseCase,
    observeLiveUpdates: ObserveLiveUpdatesUseCase,
) : ViewModel() {
    val authState: StateFlow<AuthState> = observeAuthState()

    /**
     * The `/ws` socket's state. Collecting it keeps the socket open: the app does so while it is
     * signed in and in the foreground, so the socket closes a few seconds after the app goes to
     * the background (SPEC.md §6). Deliberately not a StateFlow here, which would hold the socket
     * open for as long as this ViewModel lives.
     */
    val liveUpdates: Flow<LiveUpdatesStatus> = observeLiveUpdates()

    /**
     * Back in front: the session may have been revoked from the panel meanwhile. It is found out
     * now rather than at the next tap.
     */
    fun onForeground() {
        viewModelScope.launch { validateSession() }
    }
}

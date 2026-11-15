package org.petkitlocal.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.auth.AuthState
import org.petkitlocal.core.domain.auth.ObserveAuthStateUseCase
import org.petkitlocal.core.domain.auth.ValidateSessionUseCase

/** The activity's own state: the session the navigation follows. */
@HiltViewModel
class AppViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    private val validateSession: ValidateSessionUseCase,
) : ViewModel() {
    val authState: StateFlow<AuthState> = observeAuthState()

    /**
     * Back in front: the session may have been revoked from the panel meanwhile. It is found out
     * now rather than at the next tap.
     */
    fun onForeground() {
        viewModelScope.launch { validateSession() }
    }
}

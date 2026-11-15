package org.petkitlocal.feature.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.pets.ObservePetsUseCase
import org.petkitlocal.core.domain.pets.RefreshPetsUseCase

/**
 * What the pets list shows. [pets] is the cache, null only until it has been read once; the
 * refresh fields say how the last fetch from the server went.
 */
data class PetsUiState(
    val pets: List<PetOverview>? = null,
    val refreshing: Boolean = false,
    /** Why the last refresh failed; null after a successful one. */
    val refreshError: DomainError? = null,
) {
    /** The server could not be reached: what is on screen is the last known data. */
    val offline: Boolean get() = refreshError is DomainError.Network
}

/**
 * The pets list (SPEC.md §5.5) observes the cache only (ADR 0002), as Home does: each pet with
 * its weight and today's visits; it refreshes when it opens and when the user pulls.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@HiltViewModel
class PetsViewModel @Inject constructor(
    observePets: ObservePetsUseCase,
    private val refreshPets: RefreshPetsUseCase,
    private val today: TodayUseCase,
) : ViewModel() {
    /** The local day being counted; it moves on at each refresh, so a new day is picked up. */
    private val day = MutableStateFlow(today())
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<DomainError?>(null)

    val uiState: StateFlow<PetsUiState> = combine(
        day.flatMapLatest { observePets(it) },
        refreshing,
        refreshError,
    ) { overview, refreshing, error ->
        PetsUiState(pets = overview.pets, refreshing = refreshing, refreshError = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PetsUiState())

    init {
        onRefresh()
    }

    /** Pull to refresh, or retry. A refresh already running is not started twice. */
    fun onRefresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            day.value = today()
            val result = refreshPets(day.value)
            refreshError.value = (result as? Outcome.Failure)?.error
            refreshing.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

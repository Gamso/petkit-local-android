package org.petkitlocal.feature.pets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.getOrNull
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.pets.GetPetsSourceUseCase
import org.petkitlocal.core.domain.pets.ObservePetsUseCase
import org.petkitlocal.core.domain.pets.PetsSource
import org.petkitlocal.core.domain.pets.PetsSourceSetting
import org.petkitlocal.core.domain.pets.RefreshPetsUseCase
import org.petkitlocal.core.domain.pets.SetPetsSourceUseCase

/**
 * What the pets list shows. [pets] is the cache, null only until it has been read once; the
 * refresh fields say how the last fetch from the server went.
 */
data class PetsUiState(
    val pets: List<PetOverview>? = null,
    val refreshing: Boolean = false,
    /** Why the last refresh failed; null after a successful one. */
    val refreshError: DomainError? = null,
    /** Whose pets the devices recognise; null until read, or when it could not be. */
    val petsSource: PetsSourceSetting? = null,
    /** The source being set. */
    val settingSource: PetsSource? = null,
    /** Why setting it failed. */
    val sourceError: DomainError? = null,
) {
    /** The setting matters, and is shown, only while the server relays PetKit's cloud. */
    val showsPetsSource: Boolean get() = petsSource?.proxyMode == true

    /** The server could not be reached: what is on screen is the last known data. */
    val offline: Boolean get() = refreshError is DomainError.Network
}

/**
 * The pets list (SPEC.md §5.5) observes the cache only (ADR 0002), as Home does: each pet with
 * its weight and today's visits; it refreshes when it opens and when the user pulls. It also reads
 * whose pets the devices recognise, a server setting shown only in proxy mode (issue #98).
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@HiltViewModel
class PetsViewModel @Inject constructor(
    observePets: ObservePetsUseCase,
    private val refreshPets: RefreshPetsUseCase,
    private val today: TodayUseCase,
    private val getPetsSource: GetPetsSourceUseCase,
    private val setPetsSource: SetPetsSourceUseCase,
) : ViewModel() {
    /** The local day being counted; it moves on at each refresh, so a new day is picked up. */
    private val day = MutableStateFlow(today())
    private val refreshing = MutableStateFlow(false)
    private val refreshError = MutableStateFlow<DomainError?>(null)
    private val source = MutableStateFlow(SourceState())

    /** The pets source as read, and its write. */
    private data class SourceState(
        val setting: PetsSourceSetting? = null,
        val pending: PetsSource? = null,
        val error: DomainError? = null,
    )

    val uiState: StateFlow<PetsUiState> = combine(
        day.flatMapLatest { observePets(it) },
        refreshing,
        refreshError,
        source,
    ) { overview, refreshing, error, source ->
        PetsUiState(
            pets = overview.pets,
            refreshing = refreshing,
            refreshError = error,
            petsSource = source.setting,
            settingSource = source.pending,
            sourceError = source.error,
        )
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
            val sourceRead = async { getPetsSource() }
            val result = refreshPets(day.value)
            refreshError.value = (result as? Outcome.Failure)?.error
            // A server too old to have the setting, or one that cannot be reached: nothing to show.
            sourceRead.await().getOrNull()?.let { setting -> source.update { it.copy(setting = setting) } }
            refreshing.value = false
        }
    }

    /** Whose pets the devices recognise: sent, and shown once the server has taken it (ADR 0002). */
    fun onPetsSource(choice: PetsSource) {
        val current = source.value
        if (current.pending != null || current.setting?.source == choice) return
        source.update { it.copy(pending = choice, error = null) }
        viewModelScope.launch {
            val result = setPetsSource(choice)
            source.update { state ->
                when (result) {
                    is Outcome.Success -> state.copy(setting = result.value, pending = null)
                    is Outcome.Failure -> state.copy(pending = null, error = result.error)
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

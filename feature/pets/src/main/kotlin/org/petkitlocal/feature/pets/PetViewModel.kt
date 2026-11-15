package org.petkitlocal.feature.pets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.pets.ObservePetDayVisitsUseCase
import org.petkitlocal.core.domain.pets.ObservePetUseCase
import org.petkitlocal.core.domain.pets.PetPage
import org.petkitlocal.core.domain.pets.RefreshPetUseCase
import org.petkitlocal.core.domain.pets.RefreshPetWeightsUseCase
import org.petkitlocal.core.domain.pets.WeightRange
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus

/**
 * What a pet's page shows. [page] is the cache's (ADR 0002) for [petId]; the rest is how the
 * reads from the server went, the pet's and its weights' separately, and the screen's choices.
 */
data class PetUiState(
    val petId: Long,
    val today: LocalDate,
    /** The phone's time zone, which times and days are shown in. */
    val zone: ZoneId,
    val range: WeightRange = WeightRange.Month,
    /** Every pet, in the household's order: the switcher. */
    val pets: List<Pet> = emptyList(),
    val page: PetPage? = null,
    /** The cache has been read for [petId]. */
    val cacheLoaded: Boolean = false,
    /** The pet is being read from the server. */
    val loading: Boolean = true,
    /** That read was asked for by pulling the page down. */
    val pulled: Boolean = false,
    /** Why the last read of the pet failed; null after a successful one. */
    val error: DomainError? = null,
    /** The weighed visits of [range] are being read. */
    val weightsLoading: Boolean = true,
    val weightsError: DomainError? = null,
    /** The live socket cannot reach the server: what is shown is the last known data. */
    val liveUnreachable: Boolean = false,
) {
    /** The user pulled to refresh, and the read is on its way. */
    val refreshing: Boolean get() = pulled && loading

    /** Nothing cached for the pet yet, and something is on its way: the skeleton. */
    val skeleton: Boolean get() = page == null && (!cacheLoaded || loading)

    /** The server says the pet is gone, or never was: nothing to show but that. */
    val gone: Boolean get() = page == null && cacheLoaded && !loading && (error == null || error.isPetGone)

    /** Nothing cached, and the read failed: the error view with its retry. */
    val failedEmpty: Boolean get() = page == null && cacheLoaded && !loading && error != null && !error.isPetGone

    val offline: Boolean get() = liveUnreachable || error is DomainError.Network
}

private val DomainError.isPetGone: Boolean get() = this is DomainError.NotFound

/**
 * A pet's page (SPEC.md §5.5). It shows the cache at once and reads the pet when it opens, when
 * the user pulls, when another pet is picked, and when a visit of the pet lands in the day's
 * cards (a live update): the server's statistics are the ones its Home Assistant sensors have.
 * The weighed visits of the chosen range are read separately, so a long range never holds up
 * the rest of the page.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@Suppress("LongParameterList") // One use case per read, and the clock.
@HiltViewModel
class PetViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observePet: ObservePetUseCase,
    observeDayVisits: ObservePetDayVisitsUseCase,
    observeLiveUpdates: ObserveLiveUpdatesUseCase,
    private val refreshPet: RefreshPetUseCase,
    private val refreshWeights: RefreshPetWeightsUseCase,
    today: TodayUseCase,
    clock: Clock,
) : ViewModel() {
    private val local = MutableStateFlow(
        PetUiState(petId = checkNotNull(savedStateHandle.get<Long>(PET_ID_KEY)), today = today(), zone = clock.zone),
    )
    private val selection = MutableStateFlow(local.value.petId to local.value.range)
    private var petJob: Job? = null
    private var weightsJob: Job? = null

    val uiState: StateFlow<PetUiState> = combine(
        selection.flatMapLatest { (petId, range) -> observePet(petId, range).map { petId to it } },
        observeLiveUpdates(),
        local,
    ) { (cachedId, content), live, state ->
        val current = cachedId == state.petId
        state.copy(
            pets = content.pets,
            page = content.page.takeIf { current },
            cacheLoaded = current,
            liveUnreachable = live is LiveUpdatesStatus.Waiting && live.error is DomainError.Network,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        loadPet()
        loadWeights()
        viewModelScope.launch {
            // The first value is what is cached already; each change after it is a new visit.
            selection.map { it.first }
                .flatMapLatest { petId -> observeDayVisits(petId, local.value.today).drop(1) }
                .collect { loadPet() }
        }
    }

    /** The switcher: another pet, in place. */
    fun onSelectPet(petId: Long) {
        if (petId == local.value.petId) return
        savedStateHandle[PET_ID_KEY] = petId
        local.update { it.copy(petId = petId, error = null, weightsError = null) }
        selection.value = petId to local.value.range
        loadPet()
        loadWeights()
    }

    fun onRange(range: WeightRange) {
        if (range == local.value.range) return
        local.update { it.copy(range = range, weightsError = null) }
        selection.value = local.value.petId to range
        loadWeights()
    }

    /** Pull to refresh: the pet and its weights again; what is cached stays on screen meanwhile. */
    fun onRefresh() {
        loadPet(pulled = true)
        loadWeights()
    }

    fun onRetry() = loadPet()

    fun onRetryWeights() = loadWeights()

    private fun loadPet(pulled: Boolean = false) {
        petJob?.cancel()
        val petId = local.value.petId
        local.update { it.copy(loading = true, pulled = pulled) }
        petJob = viewModelScope.launch {
            val result = refreshPet(petId)
            local.update { state ->
                if (state.petId != petId) return@update state
                state.copy(loading = false, pulled = false, error = (result as? Outcome.Failure)?.error)
            }
        }
    }

    private fun loadWeights() {
        weightsJob?.cancel()
        val (petId, range) = local.value.petId to local.value.range
        local.update { it.copy(weightsLoading = true) }
        weightsJob = viewModelScope.launch {
            val result = refreshWeights(petId, range)
            local.update { state ->
                if (state.petId != petId || state.range != range) return@update state
                state.copy(weightsLoading = false, weightsError = (result as? Outcome.Failure)?.error)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

package org.petkitlocal.core.domain.pets

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * The pets list for [day][invoke], from the cache only: the pets with today's visits, counted
 * from the day's cards as Home counts them, so live updates move both alike.
 */
class ObservePetsUseCase @Inject constructor(
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
) {
    operator fun invoke(day: LocalDate): Flow<PetsOverview> =
        combine(pets.observePets(), timeline.observeDay(day)) { pets, items -> PetRules.overview(pets, items) }
}

/** Refreshes the pets list: the pets and the cards of [day][invoke], at the same time. */
class RefreshPetsUseCase @Inject constructor(
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
) {
    suspend operator fun invoke(day: LocalDate): Outcome<Unit> = coroutineScope {
        val results = listOf(async { pets.refresh() }, async { timeline.refreshDay(day) }).map { it.await() }
        results.firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
    }
}

/**
 * One pet's page from the cache only (ADR 0002): it emits the last known data at once, online or
 * not, and again whenever a refresh writes the cache. "Today" is read once, when it is invoked.
 */
class ObservePetUseCase @Inject constructor(
    private val pets: PetRepository,
    private val devices: DeviceRepository,
    private val clock: Clock,
) {
    operator fun invoke(petId: Long, range: WeightRange): Flow<PetContent> {
        val today = LocalDate.now(clock)
        val since = PetRules.since(range, today, clock.zone)
        return combine(
            pets.observePets(),
            pets.observeActivity(petId),
            pets.observeWeights(petId, since),
            devices.observeDevices(),
        ) { pets, activity, weights, devices ->
            PetContent(pets, PetRules.page(petId, pets, activity, weights, devices, range, today, clock.zone))
        }
    }
}

/**
 * Reads one pet again: its statistics and recent visits, and the list (names and colours of the
 * switcher), at the same time. The pet's own result comes first: it is what the page is about.
 */
class RefreshPetUseCase @Inject constructor(private val pets: PetRepository) {
    suspend operator fun invoke(petId: Long): Outcome<Unit> = coroutineScope {
        val results = listOf(async { pets.refreshPet(petId) }, async { pets.refresh() }).map { it.await() }
        results.firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
    }
}

/** Reads a pet's weighed visits over [range][invoke], ending today, into the cache. */
class RefreshPetWeightsUseCase @Inject constructor(private val pets: PetRepository, private val clock: Clock) {
    suspend operator fun invoke(petId: Long, range: WeightRange): Outcome<Unit> =
        pets.refreshWeights(petId, PetRules.since(range, LocalDate.now(clock), clock.zone))
}

/**
 * The ids of a pet's visits among the cached cards of [day][invoke]. Live updates write a new
 * visit there; the pet's page reads its statistics again when this changes.
 */
class ObservePetDayVisitsUseCase @Inject constructor(private val timeline: TimelineRepository) {
    operator fun invoke(petId: Long, day: LocalDate): Flow<Set<Long>> =
        timeline.observeDay(day).map { PetRules.visitIds(petId, it) }.distinctUntilChanged()
}

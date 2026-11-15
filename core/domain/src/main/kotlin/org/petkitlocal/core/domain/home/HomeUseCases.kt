package org.petkitlocal.core.domain.home

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/** Today, in the phone's time zone: the local day Home counts. */
class TodayUseCase @Inject constructor(private val clock: Clock) {
    operator fun invoke(): LocalDate = LocalDate.now(clock)
}

/**
 * Home's content for [day][invoke], from the cache only: it emits the last known data at once,
 * online or not, and again whenever a refresh or a live update writes the cache.
 */
class ObserveHomeUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
    private val clock: Clock,
) {
    operator fun invoke(day: LocalDate): Flow<HomeOverview> = combine(
        devices.observeDevices(),
        pets.observePets(),
        timeline.observeDay(day),
    ) { devices, pets, items ->
        HomeRules.overview(devices, pets, items, clock.zone)
    }
}

/**
 * Refreshes what Home shows: the devices with their details, the pets, and the cards of [day],
 * at the same time. Each part replaces its own cache; the result is the devices' failure first,
 * then the others', so a server that cannot be reached reads as one [Outcome.Failure].
 */
class RefreshHomeUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
) {
    suspend operator fun invoke(day: LocalDate): Outcome<Unit> = coroutineScope {
        val results = listOf(
            async { devices.refresh() },
            async { pets.refresh() },
            async { timeline.refreshDay(day) },
        ).map { it.await() }
        results.firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
    }
}

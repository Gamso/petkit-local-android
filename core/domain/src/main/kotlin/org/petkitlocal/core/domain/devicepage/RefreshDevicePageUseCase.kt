package org.petkitlocal.core.domain.devicepage

import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * Refreshes what a device page shows: the devices with their details, the pets, and the cards of
 * [day][invoke] and of the day before (the page compares them). Each part replaces its own cache;
 * the devices' failure comes first, so an unreachable server reads as one [Outcome.Failure].
 */
class RefreshDevicePageUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
) {
    suspend operator fun invoke(day: LocalDate): Outcome<Unit> = coroutineScope {
        listOf(
            async { devices.refresh() },
            async { pets.refresh() },
            async { timeline.refreshDay(day) },
            async { timeline.refreshDay(day.minusDays(1)) },
        ).map { it.await() }.firstOrNull { it is Outcome.Failure } ?: Outcome.Success(Unit)
    }
}

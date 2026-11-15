package org.petkitlocal.core.domain.litter

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * The litter box page of device [deviceId][invoke] on [day][invoke], from the cache only: null
 * while the device is not (or no longer) known. It emits again whenever a refresh or a live update
 * writes the cache.
 */
class ObserveLitterBoxUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
    private val clock: Clock,
) {
    operator fun invoke(deviceId: Long, day: LocalDate): Flow<LitterBoxPage?> = combine(
        devices.observeDevice(deviceId),
        pets.observePets(),
        timeline.observeDay(day),
        timeline.observeDay(day.minusDays(1)),
    ) { device, pets, today, yesterday ->
        device?.let { LitterRules.page(it, pets, today, yesterday, clock.zone) }
    }
}

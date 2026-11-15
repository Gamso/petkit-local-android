package org.petkitlocal.core.domain.litter

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * The litter box page of device [deviceId][invoke] on [day][invoke], from the cache only: null
 * while the device is not (or no longer) known. It emits again whenever a refresh or a live update
 * writes the cache, the box's or its Pura Air's (the first purifier it lists, [Device.puraAirId]).
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ObserveLitterBoxUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
    private val clock: Clock,
) {
    operator fun invoke(deviceId: Long, day: LocalDate): Flow<LitterBoxPage?> = combine(
        withPuraAir(deviceId),
        pets.observePets(),
        timeline.observeDay(day),
        timeline.observeDay(day.minusDays(1)),
    ) { (device, puraAir), pets, today, yesterday ->
        device?.let { LitterRules.page(it, pets, today, yesterday, clock.zone, puraAir) }
    }

    /** The box, and the Pura Air in its K3 slot once the cache holds it. */
    private fun withPuraAir(deviceId: Long): Flow<Pair<Device?, Device?>> =
        devices.observeDevice(deviceId).flatMapLatest { device ->
            val puraAirId = device?.puraAirId ?: return@flatMapLatest flowOf(device to null)
            devices.observeDevice(puraAirId).map { puraAir ->
                device to puraAir?.takeIf { it.family == DeviceFamily.Purifier }
            }
        }
}

package org.petkitlocal.core.domain.fountain

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
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * The fountain page of device [deviceId][invoke] on [day][invoke], from the cache only: null while
 * the device is not (or no longer) known. It emits again whenever a refresh or a live update
 * writes the cache, the fountain's or, for a Bluetooth one, its relay's.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ObserveFountainUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
    private val clock: Clock,
) {
    operator fun invoke(deviceId: Long, day: LocalDate): Flow<FountainPage?> = combine(
        withRelay(deviceId),
        pets.observePets(),
        timeline.observeDay(day),
        timeline.observeDay(day.minusDays(1)),
    ) { (device, relay), pets, today, yesterday ->
        device?.let { FountainRules.page(it, pets, today, yesterday, clock.zone, relay) }
    }

    /** The fountain, and the device relaying it when it is a Bluetooth accessory. */
    private fun withRelay(deviceId: Long): Flow<Pair<Device?, Device?>> =
        devices.observeDevice(deviceId).flatMapLatest { device ->
            val parentId = device?.parentId?.takeIf { device.isAccessory }
            if (parentId == null) flowOf(device to null) else devices.observeDevice(parentId).map { device to it }
        }
}

package org.petkitlocal.core.domain.feeder

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.FeedingRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.ServedPortionsRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * The feeder page of device [deviceId][invoke] on [day][invoke], from the caches only: null while
 * the device is not (or no longer) known. A meal's state is worked out at each emission, against
 * the clock's time then; its portions are those read so far.
 */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class ObserveFeederUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val feeding: FeedingRepository,
    private val pets: PetRepository,
    private val timeline: TimelineRepository,
    private val served: ServedPortionsRepository,
    private val clock: Clock,
) {
    operator fun invoke(deviceId: Long, day: LocalDate): Flow<FeederPage?> = combine(
        devices.observeDevice(deviceId),
        feeding.observePlan(deviceId),
        feeding.observeDeferredFeeds(deviceId),
        dayWithPortions(deviceId, day),
        pets.observePets(),
    ) { device, plan, deferred, (items, portions), pets ->
        device?.let { FeederRules.page(it, plan, deferred, items, clock.instant(), clock.zone, pets, portions) }
    }

    /** The day's cards, with the portions of this feeder's meals among them. */
    private fun dayWithPortions(deviceId: Long, day: LocalDate) = timeline.observeDay(day).flatMapLatest { items ->
        val meals = FeederDayRules.mealIds(items.filter { it.deviceId == deviceId })
        served.observe(meals).map { portions -> items to portions }
    }
}

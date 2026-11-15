package org.petkitlocal.core.domain.feeder

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.FeedingRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/**
 * The feeder page of device [deviceId][invoke] on [day][invoke], from the caches only: null while
 * the device is not (or no longer) known. A meal's state is worked out at each emission, against
 * the clock's time then.
 */
class ObserveFeederUseCase @Inject constructor(
    private val devices: DeviceRepository,
    private val feeding: FeedingRepository,
    private val timeline: TimelineRepository,
    private val clock: Clock,
) {
    operator fun invoke(deviceId: Long, day: LocalDate): Flow<FeederPage?> = combine(
        devices.observeDevice(deviceId),
        feeding.observePlan(deviceId),
        feeding.observeDeferredFeeds(deviceId),
        timeline.observeDay(day),
    ) { device, plan, deferred, items ->
        device?.let { FeederRules.page(it, plan, deferred, items, clock.instant(), clock.zone) }
    }
}

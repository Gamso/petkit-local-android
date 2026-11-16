package org.petkitlocal.core.domain.history

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/** What History shows for one query, from the cache only (ADR 0002). */
data class HistoryContent(
    /** The cards, newest first. */
    val entries: List<HistoryEntry>,
    /** Every device, in the household's order: the device filter's choices. */
    val devices: List<Device>,
    /** Every pet, in the household's order, which picks their colour: the pet filter's choices. */
    val pets: List<Pet>,
)

/** One card with what the screen needs to name it: its device, and its pet's colour. */
data class HistoryEntry(val item: TimelineItem, val device: Device?, val petColorIndex: Int?)

/**
 * The cached cards of a [HistoryQuery], with the devices and pets they name. It emits the last
 * known data at once, and again whenever a page, a refresh or a live update writes the cache.
 */
class ObserveHistoryUseCase @Inject constructor(
    private val timeline: TimelineRepository,
    private val devices: DeviceRepository,
    private val pets: PetRepository,
) {
    operator fun invoke(query: HistoryQuery): Flow<HistoryContent> = combine(
        timeline.observeHistory(query),
        devices.observeDevices(),
        pets.observePets(),
    ) { items, devices, pets ->
        val byId = devices.associateBy { it.id }
        HistoryContent(
            entries = items.map { item ->
                HistoryEntry(
                    item = item,
                    device = item.deviceId?.let(byId::get),
                    petColorIndex = item.pet?.let { ref -> pets.indexOfFirst { it.id == ref.id }.takeIf { it >= 0 } },
                )
            },
            devices = devices,
            pets = pets,
        )
    }
}

/** One page of a [HistoryQuery] into the cache: the first without a [cursor]. */
class FetchHistoryPageUseCase @Inject constructor(private val timeline: TimelineRepository) {
    suspend operator fun invoke(query: HistoryQuery, cursor: HistoryCursor?): Outcome<HistoryPage> =
        timeline.fetchHistoryPage(query, cursor)
}

/** The decoded fields and media of one card's event, for the detail sheet. */
class GetTimelineEventUseCase @Inject constructor(private val timeline: TimelineRepository) {
    suspend operator fun invoke(id: Long): Outcome<EventDetail> = timeline.getEvent(id)
}

/**
 * The days History offers. The strip shows a week of local days ending on a day no later than
 * today; days are calendar dates (the server cuts them at local midnight), so a week is seven
 * dates whatever the clocks did, never 7 × 24 hours.
 */
object HistoryDays {
    const val WEEK = 7L

    /** The seven days of the strip ending on [end], oldest first. */
    fun week(end: LocalDate): List<LocalDate> = (WEEK - 1 downTo 0).map { end.minusDays(it) }

    /**
     * The strip [weeks] weeks away from the one ending on [end], and the day selected in it: the
     * same weekday as [selected], or [today] when that would be in the future.
     */
    fun shift(selected: LocalDate, end: LocalDate, weeks: Long, today: LocalDate): Pair<LocalDate, LocalDate> {
        val newEnd = minOf(end.plusWeeks(weeks), today)
        val moved = minOf(selected.plusWeeks(weeks), today)
        val newSelected = if (moved in week(newEnd)) moved else newEnd
        return newEnd to newSelected
    }

    /**
     * The end of the strip that shows [day]: today's strip, or the one a whole number of weeks
     * before it, as the strip's arrows would reach it.
     */
    fun endShowing(day: LocalDate, today: LocalDate): LocalDate =
        today.minusWeeks(ChronoUnit.DAYS.between(day, today).coerceAtLeast(0) / WEEK)
}

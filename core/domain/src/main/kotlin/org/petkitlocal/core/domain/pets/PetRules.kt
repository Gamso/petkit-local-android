package org.petkitlocal.core.domain.pets

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.WeightSample

/** How a pet's page is put together from the cache (SPEC.md §5.5). Pure: the tests drive it. */
object PetRules {
    /**
     * The page of [petId] among [pets], or null when the cache does not have it. Days are local
     * calendar days in [zone], so a range is whole days whatever the clocks did.
     */
    @Suppress("LongParameterList") // What the cache holds of the pet, and the day it is.
    fun page(
        petId: Long,
        pets: List<Pet>,
        activity: PetActivity?,
        weights: List<WeightSample>,
        devices: List<Device>,
        range: WeightRange,
        today: LocalDate,
        zone: ZoneId,
    ): PetPage? {
        val index = pets.indexOfFirst { it.id == petId }.takeIf { it >= 0 } ?: return null
        val byId = devices.associateBy { it.id }
        return PetPage(
            pet = pets[index],
            colorIndex = index,
            activity = activity,
            visitsToday = visitsToday(activity, today, zone),
            lastVisitDevice = activity?.stats?.lastDeviceId?.let(byId::get),
            recentVisits = activity?.recentVisits.orEmpty().map { PetVisit(it, it.deviceId?.let(byId::get)) },
            trend = weightTrend(weights, range, today, zone),
        )
    }

    /** The server's count of today's visits, if it was read today; another day's count is no count. */
    fun visitsToday(activity: PetActivity?, today: LocalDate, zone: ZoneId): Int? =
        activity?.takeIf { it.fetchedAt.atZone(zone).toLocalDate() == today }?.stats?.visitsToday

    /** When [range] ending on [today] starts: its first local day's midnight. */
    fun since(range: WeightRange, today: LocalDate, zone: ZoneId): Instant =
        range.firstDay(today).atStartOfDay(zone).toInstant()

    /**
     * [samples] as one point per bucket of [range] ending on [today]: the median of the bucket's
     * weighed visits. A bucket without one has no point; samples outside the range are left out.
     */
    fun weightTrend(samples: List<WeightSample>, range: WeightRange, today: LocalDate, zone: ZoneId): WeightTrend {
        val first = range.firstDay(today)
        val buckets = samples
            .groupBy { ChronoUnit.DAYS.between(first, it.at.atZone(zone).toLocalDate()) }
            .filterKeys { it in 0 until range.days }
            .entries
            .groupBy({ (it.key / range.bucketDays).toInt() }, { it.value })
            .mapValues { (_, days) -> days.flatten().map { it.weightG } }
        val points = buckets.toSortedMap().map { (index, weights) ->
            val start = first.plusDays(index.toLong() * range.bucketDays)
            WeightPoint(
                index = index,
                firstDay = start,
                lastDay = start.plusDays(range.bucketDays - 1L),
                medianG = median(weights),
                visits = weights.size,
            )
        }
        return WeightTrend(range, first, today, points)
    }

    /** The middle value, or the mean of the two middle ones. [values] must not be empty. */
    fun median(values: List<Int>): Double {
        require(values.isNotEmpty()) { "no value" }
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle].toDouble()
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        }
    }

    /** The ids of [petId]'s visits among [dayItems]: what changes when it visits a box. */
    fun visitIds(petId: Long, dayItems: List<TimelineItem>): Set<Long> = dayItems
        .filter { TimelineCategory.Toileting in it.categories && it.pet?.id == petId }
        .mapTo(HashSet()) { it.id }

    /** The pets list: Home's pet chips, each with its colour and today's visits. */
    fun overview(pets: List<Pet>, dayItems: List<TimelineItem>): PetsOverview = PetsOverview(
        pets.mapIndexed { index, pet -> PetOverview(pet, colorIndex = index, HomeRules.visitsOf(pet.id, dayItems)) },
    )
}

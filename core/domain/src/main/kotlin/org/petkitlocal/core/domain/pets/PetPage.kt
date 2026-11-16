package org.petkitlocal.core.domain.pets

import java.time.LocalDate
import org.petkitlocal.core.domain.home.PetOverview
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.TimelineItem

/** The pets list: each pet with its colour and today's visits, as Home's pet chips have them. */
data class PetsOverview(val pets: List<PetOverview>)

/** What a pet's page shows, from the cache only (ADR 0002). */
data class PetContent(
    /** Every pet, in the household's order, which picks their colour: the switcher's choices. */
    val pets: List<Pet>,
    /** The pet asked for; null when the cache does not (or no longer) have it. */
    val page: PetPage?,
)

/** One pet's page (SPEC.md §5.5). */
data class PetPage(
    val pet: Pet,
    val colorIndex: Int,
    /** Its statistics and recent visits as last read; null until read once. */
    val activity: PetActivity?,
    /** Today's visits, when [activity] was read today; null otherwise, since it would be another day's. */
    val visitsToday: Int?,
    /** The device of its last visit, when the cache knows it. */
    val lastVisitDevice: Device?,
    /** Its recent visits, newest first, each with its device. */
    val recentVisits: List<PetVisit>,
    val trend: WeightTrend,
)

/** One recent visit and the device it was on, when the cache knows it. */
data class PetVisit(val item: TimelineItem, val device: Device?)

/** The ranges of a pet's weight chart, each cut into buckets of [bucketDays] local days ending today. */
enum class WeightRange(val days: Int, val bucketDays: Int) {
    /** One point a day. */
    Month(days = 30, bucketDays = 1),

    /** One point a week: 26 weeks. */
    HalfYear(days = 182, bucketDays = 7),
    ;

    val buckets: Int get() = days / bucketDays

    /** The first local day of the range that ends on [today]. */
    fun firstDay(today: LocalDate): LocalDate = today.minusDays(days - 1L)
}

/**
 * The median weight of a pet's weighed visits in bucket [index] of a range (0 is the oldest),
 * which runs from [firstDay] to [lastDay], and how many visits it was taken from.
 */
data class WeightPoint(
    val index: Int,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val medianG: Double,
    val visits: Int,
)

/**
 * A pet's weight over [range], from [firstDay] to [lastDay] (today): one point per bucket that
 * has a weighed visit, oldest first, and none where there is none; nothing is filled in.
 */
data class WeightTrend(
    val range: WeightRange,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val points: List<WeightPoint>,
) {
    val visits: Int get() = points.sumOf { it.visits }

    /**
     * The first day of the first bucket with a weighed visit, when that is not the range's first
     * bucket: the chart then says from when it has data. Before it, the pet did not visit, was
     * not weighed, or the server no longer keeps those visits; the chart cannot tell which.
     */
    val dataSince: LocalDate?
        get() = points.firstOrNull()?.takeIf { it.index > 0 }?.firstDay
}

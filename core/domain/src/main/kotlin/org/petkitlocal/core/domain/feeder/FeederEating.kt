package org.petkitlocal.core.domain.feeder

import java.time.ZoneId
import kotlin.math.roundToInt
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineItem

/**
 * What a camera feeder saw its pets eat (#68): today's eating episodes against yesterday's, and
 * today's as the stems of a 24-hour chart. A feeder without a camera has none: it cannot see who
 * eats.
 */
data class FeederEating(
    val today: EatingStats,
    val yesterday: EatingStats,
    /**
     * Whether this feeder's eating episodes say how long they lasted: false when it has some today
     * or yesterday and none of them has a duration (petkit-local sends `duration_sec: null` then).
     */
    val durationsKnown: Boolean,
    /** Today's eating episodes, oldest first: the stems of the chart. */
    val episodes: List<EatingEpisode>,
    /** Pets in the household's order, which picks their colour. */
    val pets: List<Pet>,
) {
    /** The pets the chart can be filtered on: those seen eating today, in the household's order. */
    val chartPets: List<Pet>
        get() = episodes.mapNotNull { it.petId }.toSet().let { ids -> pets.filter { it.id in ids } }

    /** A pet's place in the household's order, which picks its colour; null for an unknown pet. */
    fun colorIndexOf(petId: Long?): Int? = pets.indexOfFirst { it.id == petId }.takeIf { it >= 0 }
}

/** A day's eating episodes at one feeder. [averageDurationSec] is null when none had a duration. */
data class EatingStats(val meals: Int, val averageDurationSec: Int?)

/** One stem of the eating chart. */
data class EatingEpisode(
    val itemId: Long,
    /** Local minute of the day, 0 to 1439. */
    val minuteOfDay: Int,
    val durationSec: Int?,
    val petId: Long?,
)

/** How the feeder page reads what its camera saw the pets eat. */
object FeederEatingRules {
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

    /**
     * The eating of a camera feeder from [today] and [yesterday] (its cards of each day). Every
     * eating episode counts, a recognised pet or not, and one whose end never arrived too: it has
     * no duration, so it counts towards the meals but not the average.
     */
    fun eating(today: List<TimelineItem>, yesterday: List<TimelineItem>, zone: ZoneId, pets: List<Pet>): FeederEating {
        val mine = today.filter(FeederDayRules::isEating)
        val before = yesterday.filter(FeederDayRules::isEating)
        val both = mine + before
        return FeederEating(
            today = stats(mine),
            yesterday = stats(before),
            durationsKnown = both.isEmpty() || both.any { it.durationSec != null },
            episodes = episodes(mine, zone),
            pets = pets,
        )
    }

    /** A day's eating episodes among [items], and their average duration. */
    fun stats(items: List<TimelineItem>): EatingStats {
        val durations = items.mapNotNull { it.durationSec }
        return EatingStats(items.size, durations.takeIf { it.isNotEmpty() }?.average()?.roundToInt())
    }

    private fun episodes(items: List<TimelineItem>, zone: ZoneId): List<EatingEpisode> = items
        .mapNotNull { item ->
            val local = (item.shownAt ?: return@mapNotNull null).atZone(zone).toLocalTime()
            EatingEpisode(
                itemId = item.id,
                minuteOfDay = (local.hour * MINUTES_PER_HOUR + local.minute).coerceIn(0, MINUTES_PER_DAY - 1),
                durationSec = item.durationSec,
                petId = item.pet?.id,
            )
        }
        .sortedBy { it.minuteOfDay }
}

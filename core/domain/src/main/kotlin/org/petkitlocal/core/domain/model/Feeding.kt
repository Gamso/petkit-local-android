package org.petkitlocal.core.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A feeder's recurring meals (`GET /devices/{id}/schedule`). Each day of the week is served by
 * the group that lists it; a day no group lists has no meal.
 */
data class FeedingPlan(
    val deviceId: Long,
    /** False while paused: the meals stay stored and none is served. */
    val active: Boolean,
    /** Meals and feeds give a count per hopper ([Portions.Dual]) rather than one count. */
    val dualHopper: Boolean,
    val groups: List<FeedingGroup>,
) {
    /** The meals of [day], earliest first. */
    fun mealsOn(day: DayOfWeek): List<Meal> = groups
        .filter { day in it.days }
        .flatMap { it.meals }
        .sortedBy { it.time }
}

/** The meals served on each of [days]. */
data class FeedingGroup(val days: Set<DayOfWeek>, val meals: List<Meal>)

/**
 * One recurring meal at [time], the feeder's local time. [id] is the server's: kept when the meal
 * is sent back, null for a meal added in the app.
 */
data class Meal(val id: String?, val time: LocalTime, val portions: Portions)

/** How much a meal or a feed gives, in portions: one count, or one per hopper on a Dual-Hopper. */
sealed interface Portions {
    val total: Int

    data class Single(val amount: Int) : Portions {
        override val total: Int get() = amount
    }

    data class Dual(val hopper1: Int, val hopper2: Int) : Portions {
        override val total: Int get() = hopper1 + hopper2
    }

    companion object {
        /** What the API accepts for one hopper (`Meal.amount`, 0 to 255). */
        const val MAX = 255
    }
}

/** A pending one-off feed ("later"). [fireAt] is null only if the server lost its time. */
data class DeferredFeed(val id: String, val fireAt: Instant?, val portions: Portions)

/** A one-off feed to schedule at [date] and [time], in the feeder's own time zone. */
data class DeferredFeedRequest(val date: LocalDate, val time: LocalTime, val portions: Portions)

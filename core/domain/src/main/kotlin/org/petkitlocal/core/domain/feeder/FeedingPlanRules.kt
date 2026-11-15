package org.petkitlocal.core.domain.feeder

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.petkitlocal.core.domain.model.DeferredFeedRequest
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.Portions

/** Something to fix in a weekly plan before it is sent; [group] is its index in the plan. */
sealed interface PlanProblem {
    val group: Int

    /** A group serves no day. */
    data class NoDays(override val group: Int) : PlanProblem

    /** [day] is in this group and an earlier one. */
    data class DayTwice(override val group: Int, val day: DayOfWeek) : PlanProblem

    /** Two meals of the group at the same [time]. */
    data class SameTime(override val group: Int, val time: LocalTime) : PlanProblem

    /** The meal at [meal] gives nothing. */
    data class NothingServed(override val group: Int, val meal: Int) : PlanProblem

    /** More than [FeedingPlanRules.MAX_MEALS] meals. */
    data class TooManyMeals(override val group: Int) : PlanProblem
}

/** Something to fix in a one-off feed before it is sent. */
enum class FeedProblem {
    InThePast,
    NothingServed,
}

/**
 * The rules a plan and a one-off feed must follow (api/openapi.yaml: at most 7 groups of 24
 * meals, amounts 0 to 255). The app checks them before sending, to say what is wrong where it is;
 * the server checks again and its refusal is shown as it words it.
 */
object FeedingPlanRules {
    const val MAX_MEALS = 24

    /** A new meal comes this long after the group's last one. */
    private const val NEXT_MEAL_HOURS = 4L
    private val FIRST_MEAL: LocalTime = LocalTime.of(8, 0)

    fun problems(groups: List<FeedingGroup>): List<PlanProblem> {
        val seen = mutableSetOf<DayOfWeek>()
        return groups.flatMapIndexed { index, group ->
            buildList {
                if (group.days.isEmpty()) add(PlanProblem.NoDays(index))
                group.days.sorted().filter { it in seen }.forEach { add(PlanProblem.DayTwice(index, it)) }
                seen += group.days
                if (group.meals.size > MAX_MEALS) add(PlanProblem.TooManyMeals(index))
                group.meals.groupBy { it.time }.filterValues { it.size > 1 }.keys.sorted()
                    .forEach { add(PlanProblem.SameTime(index, it)) }
                group.meals.forEachIndexed { position, meal ->
                    if (meal.portions.total == 0) add(PlanProblem.NothingServed(index, position))
                }
            }
        }
    }

    /** A meal to add to [group]: one portion (on the first hopper), some hours after the last meal. */
    fun newMeal(group: FeedingGroup, dual: Boolean): Meal {
        val last = group.meals.maxOfOrNull { it.time }
        val time = when {
            last == null -> FIRST_MEAL
            last < LocalTime.MAX.minusHours(NEXT_MEAL_HOURS) -> last.plusHours(NEXT_MEAL_HOURS)
            else -> last.plusMinutes(1).takeIf { it > last } ?: last
        }
        return Meal(null, time, if (dual) Portions.Dual(1, 0) else Portions.Single(1))
    }

    /** A group for the days no other group serves, with one meal. */
    fun newGroup(groups: List<FeedingGroup>, dual: Boolean): FeedingGroup {
        val free = DayOfWeek.entries.toSet() - groups.flatMap { it.days }.toSet()
        val empty = FeedingGroup(free, emptyList())
        return empty.copy(meals = listOf(newMeal(empty, dual)))
    }

    fun feedProblems(feed: DeferredFeedRequest, now: Instant, zone: ZoneId): Set<FeedProblem> = buildSet {
        if (!feed.date.atTime(feed.time).atZone(zone).toInstant().isAfter(now)) add(FeedProblem.InThePast)
        if (feed.portions.total == 0) add(FeedProblem.NothingServed)
    }
}

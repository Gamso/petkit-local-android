package org.petkitlocal.feature.device.feeder

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.feeder.FeedProblem
import org.petkitlocal.core.domain.feeder.FeederDayFilter
import org.petkitlocal.core.domain.feeder.FeederPage
import org.petkitlocal.core.domain.feeder.FeedingPlanRules
import org.petkitlocal.core.domain.feeder.PlanProblem
import org.petkitlocal.core.domain.model.DeferredFeedRequest
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.litter.PageLoadState

/**
 * What the feeder page shows: [page] is the caches (null until read, or when the device is gone,
 * which [loaded] tells apart); [commands] is what its buttons and writes did (the plan and the
 * one-off feeds included); the rest is the page's own state, the forms being edited among it.
 */
data class FeederUiState(
    val page: FeederPage? = null,
    override val loaded: Boolean = false,
    override val refreshing: Boolean = false,
    override val refreshError: DomainError? = null,
    /** The live socket cannot reach the server: what is shown is the last known data. */
    val liveUnreachable: Boolean = false,
    val commands: CommandState = CommandState(),
    /** The phone's time zone and local day, which times and "today" are shown in. */
    val zone: ZoneId = ZoneId.systemDefault(),
    val today: LocalDate = LocalDate.now(zone),
    val openPart: DevicePart? = null,
    /** Feed-now portions per hopper chosen on the page; null follows the feeder's own settings. */
    val chosenPortions: List<Int>? = null,
    /** Feed now was pressed with every hopper at zero. */
    val nothingChosen: Boolean = false,
    val planDraft: PlanDraft? = null,
    val feedDraft: FeedDraft? = null,
    /** The chip picked over the day's list. */
    val dayFilter: FeederDayFilter = FeederDayFilter.All,
) : PageLoadState {
    val offline: Boolean get() = liveUnreachable || refreshError is DomainError.Network

    /** The portions feed now gives per hopper (empty on a single hopper). */
    val portions: List<Int>
        get() = chosenPortions ?: page?.manualFeed?.hoppers?.map { it.portions }.orEmpty()

    val openHotspot: Hotspot? get() = page?.hotspots?.firstOrNull { it.part == openPart }

    /** The chip the day's list follows: All when the one picked is not offered. */
    val shownDayFilter: FeederDayFilter
        get() = dayFilter.takeIf { it in page?.day?.filters.orEmpty() } ?: FeederDayFilter.All

    /** Whether the command or write [key] is in flight. */
    fun sending(key: String): Boolean = key in commands.sending
}

/**
 * The weekly plan being edited. Its [problems] are shown once a save was tried ([checked]); a
 * [refusal] is the server's own answer to the last save.
 */
data class PlanDraft(
    val groups: List<FeedingGroup>,
    val dual: Boolean,
    val checked: Boolean = false,
    val refusal: DomainError? = null,
) {
    val problems: List<PlanProblem> get() = FeedingPlanRules.problems(groups)

    val shownProblems: List<PlanProblem> get() = if (checked) problems else emptyList()
}

/** A one-off feed being added; like [PlanDraft], its problems show once it was submitted. */
data class FeedDraft(
    val date: LocalDate,
    val time: LocalTime,
    val portions: Portions,
    val checked: Boolean = false,
    val problems: Set<FeedProblem> = emptySet(),
    val refusal: DomainError? = null,
) {
    val request: DeferredFeedRequest get() = DeferredFeedRequest(date, time, portions)

    val shownProblems: Set<FeedProblem> get() = if (checked) problems else emptySet()
}

/** One change to a [PlanDraft]; [group] and [meal] are indexes. */
sealed interface PlanEdit {
    data class ToggleDay(val group: Int, val day: DayOfWeek) : PlanEdit

    data class AddMeal(val group: Int) : PlanEdit

    data class RemoveMeal(val group: Int, val meal: Int) : PlanEdit

    data class MealTime(val group: Int, val meal: Int, val time: LocalTime) : PlanEdit

    /** [hopper] is 1 or 2 on a Dual-Hopper, ignored on a single one. */
    data class MealPortions(val group: Int, val meal: Int, val hopper: Int, val portions: Int) : PlanEdit

    data object AddGroup : PlanEdit

    data class RemoveGroup(val group: Int) : PlanEdit
}

/** Applies [edit] to [draft]; an index that no longer exists changes nothing. */
internal fun PlanDraft.apply(edit: PlanEdit): PlanDraft {
    fun group(index: Int, change: (FeedingGroup) -> FeedingGroup) =
        copy(groups = groups.mapIndexed { i, group -> if (i == index) change(group) else group }, refusal = null)

    fun meal(group: Int, index: Int, change: (Meal) -> Meal) =
        group(group) { g -> g.copy(meals = g.meals.mapIndexed { i, meal -> if (i == index) change(meal) else meal }) }

    return when (edit) {
        is PlanEdit.ToggleDay -> group(edit.group) { g ->
            g.copy(days = if (edit.day in g.days) g.days - edit.day else g.days + edit.day)
        }

        is PlanEdit.AddMeal -> group(edit.group) { g -> g.copy(meals = g.meals + FeedingPlanRules.newMeal(g, dual)) }

        is PlanEdit.RemoveMeal -> group(edit.group) { g ->
            g.copy(meals = g.meals.filterIndexed { i, _ -> i != edit.meal })
        }

        is PlanEdit.MealTime -> meal(edit.group, edit.meal) { it.copy(time = edit.time) }

        is PlanEdit.MealPortions -> meal(edit.group, edit.meal) {
            it.copy(portions = it.portions.with(edit.hopper, edit.portions))
        }

        PlanEdit.AddGroup -> copy(groups = groups + FeedingPlanRules.newGroup(groups, dual), refusal = null)

        is PlanEdit.RemoveGroup -> copy(groups = groups.filterIndexed { i, _ -> i != edit.group }, refusal = null)
    }
}

/** [this] with [hopper]'s count (1 or 2; any for a single hopper) set to [count], within 0..255. */
internal fun Portions.with(hopper: Int, count: Int): Portions {
    val bounded = count.coerceIn(0, Portions.MAX)
    return when (this) {
        is Portions.Single -> Portions.Single(bounded)
        is Portions.Dual -> if (hopper == 2) copy(hopper2 = bounded) else copy(hopper1 = bounded)
    }
}

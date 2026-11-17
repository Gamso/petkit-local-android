package org.petkitlocal.core.domain.feeder

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import org.petkitlocal.core.domain.activity.ActivityRules
import org.petkitlocal.core.domain.devicepage.HotspotRules
import org.petkitlocal.core.domain.home.HomeRules
import org.petkitlocal.core.domain.home.Quantity
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.FeedingPlan
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.asDouble

/**
 * The rules of the feeder page. Meal times are the feeder's local time; the app takes the
 * phone's time zone for it, which is the household's.
 */
object FeederRules {
    /** A feeding card this long before a meal's time or [SERVED_AFTER] after it serves that meal. */
    val SERVED_BEFORE: Duration = Duration.ofMinutes(5)
    val SERVED_AFTER: Duration = Duration.ofMinutes(30)

    /** A meal still unserved this long after its time is skipped. */
    val SKIPPED_AFTER: Duration = Duration.ofMinutes(10)

    private const val DAYS_AHEAD = 7L

    /** @param pets the household, in its order, which names and colours a pet a camera feeder saw. */
    @Suppress("LongParameterList") // The page's sources, the time and the zone.
    fun page(
        device: Device,
        plan: FeedingPlan?,
        deferred: List<DeferredFeed>?,
        todayItems: List<TimelineItem>,
        now: Instant,
        zone: ZoneId,
        pets: List<Pet> = emptyList(),
    ): FeederPage {
        val cards = todayItems.filter { it.deviceId == device.id }
        val feeds = cards.filter { TimelineCategory.Feeding in it.categories }
        val manual = manualFeed(device)
        return FeederPage(
            device = device,
            status = HomeRules.status(device),
            hotspots = HotspotRules.feeder(device),
            dualHopper = plan?.dualHopper ?: manual.hoppers.isNotEmpty(),
            manualFeed = manual,
            plan = plan,
            today = plan?.let { todayMeals(it, feeds, now, zone) }.orEmpty(),
            nextMeal = nextMeal(plan, deferred.orEmpty(), now, zone),
            deferredFeeds = deferred,
            mealsToday = feeds.size,
            dispensed = device.entity("total_dispensed")?.let { e -> e.value.asDouble?.let { Quantity(it, e.unit) } },
            activity = ActivityRules.entries(cards, pets),
        )
    }

    /** Today's meals, each matched with at most one of [feeds] (this feeder's feeding cards). */
    fun todayMeals(plan: FeedingPlan, feeds: List<TimelineItem>, now: Instant, zone: ZoneId): List<PlannedMeal> {
        val today = now.atZone(zone).toLocalDate()
        val unmatched = feeds.mapNotNull { it.shownAt }.sorted().toMutableList()
        return plan.mealsOn(today.dayOfWeek).map { meal ->
            val at = meal.at(today, zone)
            val card = unmatched.firstOrNull { it >= at - SERVED_BEFORE && it <= at + SERVED_AFTER }
            val state = when {
                card != null -> MealState.Served.also { unmatched.remove(card) }
                !plan.active -> MealState.Paused
                now > at + SKIPPED_AFTER -> MealState.Skipped
                else -> MealState.Upcoming
            }
            PlannedMeal(meal, state)
        }
    }

    /** The soonest of the plan's meals to come (unless paused) and the pending one-off feeds. */
    fun nextMeal(plan: FeedingPlan?, deferred: List<DeferredFeed>, now: Instant, zone: ZoneId): NextMeal? {
        val today = now.atZone(zone).toLocalDate()
        val planned = plan?.takeIf { it.active }?.let { active ->
            (0..DAYS_AHEAD).asSequence()
                .map { today.plusDays(it) }
                .flatMap { day -> active.mealsOn(day.dayOfWeek).map { meal -> meal to meal.at(day, zone) } }
                .firstOrNull { (_, at) -> at > now }
                ?.let { (meal, at) -> NextMeal(at, meal.portions, oneOff = false) }
        }
        val oneOff = deferred
            .filter { it.fireAt != null && it.fireAt > now }
            .minByOrNull { it.fireAt!! }
            ?.let { NextMeal(it.fireAt!!, it.portions, oneOff = true) }
        return listOfNotNull(planned, oneOff).minByOrNull { it.at }
    }

    fun manualFeed(device: Device): ManualFeed = ManualFeed(
        hoppers = (1..2).mapNotNull { hopper ->
            val entity = device.entity("hopper${hopper}_portions")?.takeIf { it.settable } ?: return@mapNotNull null
            HopperPortions(
                hopper = hopper,
                entityKey = entity.key,
                entityName = entity.name,
                portions = entity.value.asDouble?.roundToInt() ?: 1,
                min = entity.min?.roundToInt() ?: 0,
                max = entity.max?.roundToInt() ?: DEFAULT_MAX_PORTIONS,
            )
        },
        feedAll = device.actions.firstOrNull { it.key == "feed" },
        feedHopper = (1..2)
            .mapNotNull { n -> device.actions.firstOrNull { it.key == "feed_hopper_$n" }?.let { n to it } }
            .toMap(),
    )

    /**
     * Feeding now with [chosen] portions per hopper (ignored on a single hopper): the hopper
     * settings that differ are written first, then the action that dispenses exactly the hoppers
     * given some (`feed_hopper_n` for one alone, `feed` for both).
     */
    fun feedNow(manual: ManualFeed, chosen: List<Int>): FeedNow {
        val counts = manual.hoppers.mapIndexed { index, hopper ->
            hopper to (chosen.getOrNull(index) ?: hopper.portions)
        }
        val giving = counts.filter { (_, count) -> count > 0 }
        val action = when {
            manual.hoppers.isEmpty() -> manual.feedAll
            giving.isEmpty() -> return FeedNow.NothingChosen
            else -> giving.singleOrNull()?.let { (hopper, _) -> manual.feedHopper[hopper.hopper] } ?: manual.feedAll
        } ?: return FeedNow.NoFeedAction
        // Writing a hopper that is not dispensed would only change the next feed's default.
        val dispensed = if (action == manual.feedAll) counts else giving
        return FeedNow.Steps(dispensed.filter { (hopper, count) -> count != hopper.portions }, action)
    }

    private fun Meal.at(day: LocalDate, zone: ZoneId): Instant = day.atTime(time).atZone(zone).toInstant()

    /** The bound petkit-local gives the hopper portion settings. */
    private const val DEFAULT_MAX_PORTIONS = 10
}

package org.petkitlocal.core.domain.feeder

import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.devicepage.PageFixtures.action
import org.petkitlocal.core.domain.devicepage.PageFixtures.at
import org.petkitlocal.core.domain.devicepage.PageFixtures.device
import org.petkitlocal.core.domain.devicepage.PageFixtures.item
import org.petkitlocal.core.domain.devicepage.PageFixtures.paris
import org.petkitlocal.core.domain.devicepage.PageFixtures.sensor
import org.petkitlocal.core.domain.devicepage.PageFixtures.today
import org.petkitlocal.core.domain.home.Quantity
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.DeferredFeedRequest
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.FeedingPlan
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.model.TimelineCategory

class FeederRulesTest {
    // 2026-11-15 is a Sunday.
    private val everyDay = FeedingGroup(
        DayOfWeek.entries.toSet(),
        listOf(meal(7, 0, 1), meal(12, 0, 1), meal(19, 0, 2)),
    )
    private val plan = FeedingPlan(2001, active = true, dualHopper = false, groups = listOf(everyDay))
    private val d4 = device(
        2001,
        DeviceFamily.Feeder,
        sensor("total_dispensed", 20.0, unit = "g"),
        actions = listOf(action("feed")),
    )

    @Test
    fun `today's meals are served, skipped or to come, matched with this feeder's cards`() {
        val cards = listOf(
            item(1, 2001, at(7, 0).plusSeconds(7), TimelineCategory.Feeding),
            // A manual feed at 10:30 matches no meal.
            item(2, 2001, at(10, 30), TimelineCategory.Feeding),
            item(3, 9999, at(12, 0), TimelineCategory.Feeding),
        )

        val page = FeederRules.page(d4, plan, emptyList(), cards, at(12, 20), paris)

        assertEquals(listOf(MealState.Served, MealState.Skipped, MealState.Upcoming), page.today.map { it.state })
        assertEquals(2, page.mealsToday)
        assertEquals(listOf(1L, 2L), page.day.rows.map { it.id })
        assertEquals(Quantity(20.0, "g"), page.dispensed)
        assertEquals(NextMeal(at(19, 0), Portions.Single(2), oneOff = false), page.nextMeal)
    }

    @Test
    fun `a meal just past its time is still to come, and a paused plan serves nothing`() {
        val paused = plan.copy(active = false)

        assertEquals(MealState.Upcoming, FeederRules.todayMeals(plan, emptyList(), at(12, 5), paris)[1].state)
        assertEquals(
            listOf(MealState.Paused, MealState.Paused, MealState.Paused),
            FeederRules.todayMeals(paused, emptyList(), at(12, 20), paris).map { it.state },
        )
        assertNull(FeederRules.nextMeal(paused, emptyList(), at(12, 20), paris))
    }

    @Test
    fun `the next meal may be tomorrow, another day's group, or a sooner one-off feed`() {
        val weekdays = FeedingGroup(setOf(DayOfWeek.MONDAY), listOf(meal(8, 30, 3)))
        val mondayOnly = plan.copy(groups = listOf(weekdays))
        val tomorrow = today.plusDays(1)

        assertEquals(
            NextMeal(at(8, 30, tomorrow), Portions.Single(3), oneOff = false),
            FeederRules.nextMeal(mondayOnly, emptyList(), at(20, 0), paris),
        )
        val feed = DeferredFeed("d", at(21, 0), Portions.Single(1))
        val gone = DeferredFeed("old", at(9, 0), Portions.Single(1))
        assertEquals(
            NextMeal(at(21, 0), Portions.Single(1), oneOff = true),
            FeederRules.nextMeal(mondayOnly, listOf(gone, feed), at(20, 0), paris),
        )
    }

    @Test
    fun `feed now on a dual hopper writes the changed counts and presses the right button`() {
        val d4sh = device(
            2004,
            DeviceFamily.Feeder,
            portions("hopper1_portions", 1.0),
            portions("hopper2_portions", 1.0),
            actions = listOf(action("feed"), action("feed_hopper_1"), action("feed_hopper_2")),
        )
        val manual = FeederRules.manualFeed(d4sh)
        assertEquals(listOf(1, 2), manual.hoppers.map { it.hopper })
        assertEquals(10, manual.hoppers[0].max)

        val both = FeederRules.feedNow(manual, listOf(2, 1)) as FeedNow.Steps
        assertEquals("feed", both.action.key)
        assertEquals(listOf("hopper1_portions" to 2), both.settings.map { (h, n) -> h.entityKey to n })

        val second = FeederRules.feedNow(manual, listOf(0, 3)) as FeedNow.Steps
        assertEquals("feed_hopper_2", second.action.key)
        assertEquals(listOf("hopper2_portions" to 3), second.settings.map { (h, n) -> h.entityKey to n })

        assertEquals(FeedNow.NothingChosen, FeederRules.feedNow(manual, listOf(0, 0)))
        assertEquals(true, FeederRules.page(d4sh, null, null, emptyList(), at(9, 0), paris).dualHopper)
    }

    @Test
    fun `feed now on a single hopper is its feed button, if it has one`() {
        val single = FeederRules.manualFeed(d4)
        assertEquals(FeedNow.Steps(emptyList(), action("feed")), FeederRules.feedNow(single, emptyList()))
        assertEquals(
            FeedNow.NoFeedAction,
            FeederRules.feedNow(FeederRules.manualFeed(device(1, DeviceFamily.Feeder)), emptyList()),
        )
    }

    @Test
    fun `a plan with a day twice, no day, twin meals or an empty meal says where`() {
        val groups = listOf(
            FeedingGroup(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), listOf(meal(8, 0, 1), meal(8, 0, 2))),
            FeedingGroup(setOf(DayOfWeek.TUESDAY), listOf(meal(9, 0, 0))),
            FeedingGroup(emptySet(), listOf(meal(9, 0, 1))),
        )

        assertEquals(
            listOf(
                PlanProblem.SameTime(0, LocalTime.of(8, 0)),
                PlanProblem.DayTwice(1, DayOfWeek.TUESDAY),
                PlanProblem.NothingServed(1, 0),
                PlanProblem.NoDays(2),
            ),
            FeedingPlanRules.problems(groups),
        )
        assertEquals(emptyList<PlanProblem>(), FeedingPlanRules.problems(listOf(everyDay)))
    }

    @Test
    fun `a new meal comes after the last one, a new group takes the free days`() {
        assertEquals(LocalTime.of(23, 0), FeedingPlanRules.newMeal(everyDay, dual = false).time)
        assertEquals(
            Portions.Dual(1, 0),
            FeedingPlanRules.newMeal(FeedingGroup(emptySet(), emptyList()), dual = true).portions,
        )
        val weekdays = FeedingGroup(DayOfWeek.entries.take(5).toSet(), emptyList())
        assertEquals(
            setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            FeedingPlanRules.newGroup(listOf(weekdays), false).days,
        )
    }

    @Test
    fun `a one-off feed must be in the future and give something`() {
        val past = DeferredFeedRequest(today, LocalTime.of(9, 0), Portions.Single(0))
        val later = DeferredFeedRequest(today.plusDays(1), LocalTime.of(9, 0), Portions.Dual(0, 1))

        assertEquals(
            setOf(FeedProblem.InThePast, FeedProblem.NothingServed),
            FeedingPlanRules.feedProblems(past, at(10, 0), paris),
        )
        assertEquals(emptySet<FeedProblem>(), FeedingPlanRules.feedProblems(later, at(10, 0), paris))
    }

    private fun meal(hour: Int, minute: Int, portions: Int) =
        Meal("n_${hour * 3600 + minute * 60}", LocalTime.of(hour, minute), Portions.Single(portions))

    private fun portions(key: String, value: Double) = Entity(
        key,
        key,
        EntityComponent.Number,
        EntityValue.Number(value),
        settable = true,
        min = 0.0,
        max = 10.0,
        step = 1.0,
    )
}

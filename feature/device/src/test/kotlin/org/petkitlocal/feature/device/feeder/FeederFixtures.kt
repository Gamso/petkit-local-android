package org.petkitlocal.feature.device.feeder

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.feeder.FeederPage
import org.petkitlocal.core.domain.feeder.FeederRules
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.FeedingPlan
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.Household.at
import org.petkitlocal.core.testing.Household.binary
import org.petkitlocal.core.testing.Household.item
import org.petkitlocal.core.testing.Household.sensor
import org.petkitlocal.core.testing.Household.withClips

/**
 * Feeders as the mock serves them (mock-server/README.md): a Fresh Element Solo (D4) and a
 * YumShare Dual-Hopper (D4SH), with their plans, a one-off feed and this morning's meals. The
 * household's clock says 10:00 on a Sunday.
 */
object FeederFixtures {
    private fun feederActions(vararg extra: String) = listOf(
        DeviceAction("feed", "Feed", false),
        DeviceAction("cancel_manual_feed", "Cancel Manual Feed", false),
        DeviceAction("food_replenished", "Food Replenished", false),
    ) + extra.map { DeviceAction(it, it, false) } + DeviceAction("reset_desiccant", "Reset Desiccant", true)

    val solo: Device = Household.device(
        2001,
        "d4",
        "Fresh Element Solo",
        DeviceFamily.Feeder,
        sensor("error", EntityValue.None),
        sensor("desiccant_days", EntityValue.Number(3.0), unit = "days"),
        sensor("total_dispensed", EntityValue.Number(20.0), unit = "g"),
        binary("food_low", false, "problem"),
        binary("feeding", false, "running"),
        Entity(
            "feeding_schedule_active",
            "Feeding Schedule Active",
            EntityComponent.Switch,
            EntityValue.Bool(true),
            settable = true,
        ),
        Entity("feeding_schedule", "Feeding Schedule", EntityComponent.Text, EntityValue.Text("v1"), settable = true),
        actions = feederActions(),
    )

    val dual: Device = Household.device(
        2004,
        "d4sh",
        "YumShare Dual-Hopper",
        DeviceFamily.Feeder,
        sensor("device_status", EntityValue.Number(-1.0), display = "idle"),
        sensor("error", EntityValue.None),
        sensor("desiccant_days", EntityValue.Number(18.0), unit = "days"),
        sensor("total_dispensed", EntityValue.Number(30.0), unit = "g"),
        sensor("hopper1_level", EntityValue.Number(2.0), display = "Has food"),
        sensor("hopper2_level", EntityValue.Number(0.0), display = "Empty"),
        portions("hopper1_portions", 1),
        portions("hopper2_portions", 1),
        transport = DeviceTransport.Mqtt,
        isCamera = true,
        actions = feederActions("play_sound", "feed_hopper_1", "feed_hopper_2"),
    )

    val soloPlan = FeedingPlan(
        2001,
        active = true,
        dualHopper = false,
        groups = listOf(
            FeedingGroup(
                DayOfWeek.entries.toSet(),
                listOf(
                    meal(7, 0, Portions.Single(1)),
                    meal(12, 0, Portions.Single(1)),
                    meal(19, 0, Portions.Single(2)),
                ),
            ),
        ),
    )

    val dualPlan = FeedingPlan(
        2004,
        active = false,
        dualHopper = true,
        groups = listOf(
            FeedingGroup(
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                listOf(meal(7, 30, Portions.Dual(1, 1))),
            ),
            FeedingGroup(
                setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                listOf(meal(8, 0, Portions.Dual(1, 1)), meal(18, 0, Portions.Dual(0, 2))),
            ),
        ),
    )

    val tomorrowFeed = DeferredFeed("d_1", at(12, 30).plusSeconds(DAY), Portions.Single(1))

    /**
     * This morning on both feeders: on the Solo the 07:00 meal, then one that put nothing out; on
     * the Dual-Hopper the 08:00 meal (with its clip), Mochi at the camera, and a feed from hopper 2
     * the camera did not record.
     */
    val todayItems: List<TimelineItem> = listOf(
        meal(34, 2001, at(9, 15), "Feeding failed (nothing dispensed)"),
        meal(33, 2004, at(9, 40), "Feeding completed"),
        item(32, 2004, at(9, 20), TimelineCategory.Pet, pet = 1).copy(label = "Pet detected")
            .withClips(MediaKind.Highlight),
        meal(31, 2004, at(8, 0), "Feeding completed").withClips(MediaKind.Highlight),
        meal(30, 2001, at(7, 0).plusSeconds(7), "Feeding completed"),
    )

    /** What each of [todayItems]' meals put out, as their events say. */
    val servedPortions: Map<Long, Portions> = mapOf(
        30L to Portions.Single(1),
        31L to Portions.Dual(1, 1),
        33L to Portions.Dual(0, 2),
        34L to Portions.Single(0),
    )

    fun page(
        device: Device = solo,
        plan: FeedingPlan? = soloPlan,
        deferred: List<DeferredFeed>? = listOf(tomorrowFeed),
    ): FeederPage = FeederRules.page(
        device,
        plan,
        deferred,
        todayItems,
        Household.clock.instant(),
        Household.zone,
        Household.pets,
        servedPortions,
    )

    private fun state(page: FeederPage) =
        FeederUiState(page = page, loaded = true, zone = Household.zone, today = Household.today)

    val single = state(page())

    /** Paused, a hopper empty, offline banner, nothing chosen to feed. */
    val dualPaused = state(page(dual, dualPlan, emptyList())).copy(
        refreshError = DomainError.Network("timeout"),
        chosenPortions = listOf(0, 0),
        nothingChosen = true,
    )

    val planUnread = state(page(solo, null, null))

    val editor =
        PlanDraft(dualPlan.groups + FeedingGroup(setOf(DayOfWeek.MONDAY), emptyList()), dual = true, checked = true)

    val feedForm = FeedDraft(
        date = Household.today,
        time = LocalTime.of(9, 0),
        portions = Portions.Dual(0, 0),
        checked = true,
        problems = setOf(
            org.petkitlocal.core.domain.feeder.FeedProblem.InThePast,
            org.petkitlocal.core.domain.feeder.FeedProblem.NothingServed,
        ),
    )

    val today: LocalDate = Household.today

    private const val DAY = 86_400L

    private fun meal(id: Long, device: Long, at: Instant, label: String) =
        item(id, device, at, TimelineCategory.Feeding).copy(kind = "feeding", eventKind = "feeding", label = label)

    private fun meal(hour: Int, minute: Int, portions: Portions) =
        Meal("n_${hour * 3600 + minute * 60}", LocalTime.of(hour, minute), portions)

    private fun portions(key: String, value: Int) = Entity(
        key,
        key,
        EntityComponent.Number,
        EntityValue.Number(value.toDouble()),
        settable = true,
        min = 0.0,
        max = 10.0,
        step = 1.0,
    )
}

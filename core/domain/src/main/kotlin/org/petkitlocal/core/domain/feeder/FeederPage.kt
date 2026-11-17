package org.petkitlocal.core.domain.feeder

import java.time.Instant
import org.petkitlocal.core.domain.activity.ActivityEntry
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.home.Quantity
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.FeedingPlan
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.Portions

/** Everything the feeder page shows (SPEC.md §5.6), derived by [FeederRules]. */
data class FeederPage(
    val device: Device,
    val status: DeviceStatus,
    val hotspots: List<Hotspot>,
    /** Counts portions per hopper. */
    val dualHopper: Boolean,
    val manualFeed: ManualFeed,
    /** Null until read once. */
    val plan: FeedingPlan?,
    /** Today's recurring meals, earliest first, each with how it went. */
    val today: List<PlannedMeal>,
    /** The next meal or one-off feed to come, if any. */
    val nextMeal: NextMeal?,
    /** Pending one-off feeds, soonest first; null until read once. */
    val deferredFeeds: List<DeferredFeed>?,
    /** Today's feeding cards of this feeder. */
    val mealsToday: Int,
    /** The feeder's own daily total, in the server's unit, when it reports one. */
    val dispensed: Quantity?,
    /** Today's cards of this feeder, newest first, each with its follow-ups. */
    val activity: List<ActivityEntry> = emptyList(),
)

/** A recurring meal of today and how it went. */
data class PlannedMeal(val meal: Meal, val state: MealState)

enum class MealState {
    /** A feeding card of this feeder matches it. */
    Served,

    /** Its time passed and nothing was served. */
    Skipped,

    /** Still to come. */
    Upcoming,

    /** The plan is paused: it will not be served. */
    Paused,
}

/** The next food the feeder will give: a recurring meal, or a one-off feed. */
data class NextMeal(val at: Instant, val portions: Portions, val oneOff: Boolean)

/**
 * What "feed now" can do on this feeder. A Dual-Hopper's portions are its `hopper{n}_portions`
 * settings, which the `feed` actions dispense; a single hopper gives its own manual portion, which
 * the API does not let the app choose.
 */
data class ManualFeed(
    /** The per-hopper settings, empty on a single hopper. */
    val hoppers: List<HopperPortions>,
    /** `feed`: every hopper. */
    val feedAll: DeviceAction?,
    /** `feed_hopper_1`, `feed_hopper_2`, by hopper number. */
    val feedHopper: Map<Int, DeviceAction>,
)

/** One hopper's portions for a manual feed: its settable entity, and its bounds. */
data class HopperPortions(
    val hopper: Int,
    val entityKey: String,
    val entityName: String,
    val portions: Int,
    val min: Int,
    val max: Int,
)

/** How to feed now with the portions chosen, or why not. */
sealed interface FeedNow {
    /** Write [settings] (a hopper's entity and its new count), then press [action]. */
    data class Steps(val settings: List<Pair<HopperPortions, Int>>, val action: DeviceAction) : FeedNow

    /** Every hopper was set to zero. */
    data object NothingChosen : FeedNow

    /** The feeder offers no feed action. */
    data object NoFeedAction : FeedNow
}

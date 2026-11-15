package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `Meal`: one recurring meal. Amounts are portions, 0 to 255. */
@Serializable
data class MealDto(
    /** Keep it when re-sending a meal; a new one may leave it out. */
    @SerialName("id") val id: String,
    /** Seconds since local midnight, 0 to 86399. */
    @SerialName("time") val time: Int,
    /** A single-hopper feeder's portion; null on a Dual-Hopper. */
    @SerialName("amount") val amount: Int?,
    /** A Dual-Hopper's first hopper; null on a single hopper. */
    @SerialName("amount1") val amount1: Int?,
    /** A Dual-Hopper's second hopper; null on a single hopper. */
    @SerialName("amount2") val amount2: Int?,
)

/** `FeedingGroup`: the meals of a set of days. */
@Serializable
data class FeedingGroupDto(
    /** The device's day numbers, 1 = Sunday ... 7 = Saturday. */
    @SerialName("days") val days: List<Int>,
    /** At most 24. */
    @SerialName("meals") val meals: List<MealDto>,
)

/** `FeedingPlan`. */
@Serializable
data class FeedingPlanDto(
    @SerialName("device_id") val deviceId: Long,
    /** False while paused: the meals are kept, none is served. */
    @SerialName("active") val active: Boolean,
    /** Meals use `amount1`/`amount2`, not `amount`. */
    @SerialName("dual_hopper") val dualHopper: Boolean,
    @SerialName("groups") val groups: List<FeedingGroupDto>,
)

/** `FeedingPlanWrite`: either field, or both. At most 7 groups of 24 meals. */
@Serializable
data class FeedingPlanWriteDto(
    @SerialName("active") val active: Boolean? = null,
    @SerialName("groups") val groups: List<FeedingGroupDto>? = null,
)

/** `FeedingPlanResult`. */
@Serializable
data class FeedingPlanResultDto(
    @SerialName("ok") val ok: Boolean,
    /** One of [Delivered.KNOWN]. */
    @SerialName("delivered") val delivered: String,
    @SerialName("schedule") val schedule: FeedingPlanDto,
)

/** `DeferredFeed`: one pending one-off feed. */
@Serializable
data class DeferredFeedDto(
    @SerialName("id") val id: String,
    @SerialName("fire_at") val fireAt: Double?,
    /** Single hopper; null on a Dual-Hopper. */
    @SerialName("amount") val amount: Int?,
    /** Dual-Hopper; null on a single hopper. */
    @SerialName("amount1") val amount1: Int?,
    /** Dual-Hopper; null on a single hopper. */
    @SerialName("amount2") val amount2: Int?,
)

/**
 * `DeferredFeedRequest`: [amount] for a single-hopper feeder, [amount1], [amount2] or both for a
 * Dual-Hopper. [date] (`YYYY-MM-DD`) and [time] (`HH:MM`) are in the feeder's timezone.
 */
@Serializable
data class DeferredFeedRequestDto(
    @SerialName("date") val date: String,
    @SerialName("time") val time: String,
    @SerialName("amount") val amount: Int? = null,
    @SerialName("amount1") val amount1: Int? = null,
    @SerialName("amount2") val amount2: Int? = null,
)

/** `DeferredFeedResult`. */
@Serializable
data class DeferredFeedResultDto(
    @SerialName("ok") val ok: Boolean,
    /** One of [Delivered.KNOWN]. */
    @SerialName("delivered") val delivered: String,
    @SerialName("feed") val feed: DeferredFeedDto,
)

/** Body of `GET /devices/{id}/schedule`. */
@Serializable
data class FeedingPlanResponseDto(@SerialName("schedule") val schedule: FeedingPlanDto)

/** Body of `GET /devices/{id}/deferred-feeds`: soonest first. */
@Serializable
data class DeferredFeedListResponseDto(@SerialName("deferred_feeds") val deferredFeeds: List<DeferredFeedDto>)

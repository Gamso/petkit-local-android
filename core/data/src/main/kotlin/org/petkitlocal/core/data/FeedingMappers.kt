package org.petkitlocal.core.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.DeferredFeedRequest
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.FeedingPlan
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.network.dto.DeferredFeedDto
import org.petkitlocal.core.network.dto.DeferredFeedRequestDto
import org.petkitlocal.core.network.dto.Delivered
import org.petkitlocal.core.network.dto.FeedingGroupDto
import org.petkitlocal.core.network.dto.FeedingPlanDto
import org.petkitlocal.core.network.dto.MealDto

/*
 * The feeding plan between the API's shapes and the domain's. The API counts days as the device
 * does, 1 = Sunday ... 7 = Saturday, and times as seconds since local midnight.
 */

private const val DAYS_PER_WEEK = 7
private const val MILLIS_PER_SECOND = 1000.0

/** The device's day number of [this]: Sunday is 1, Saturday 7. */
internal fun DayOfWeek.toDeviceDay(): Int = value % DAYS_PER_WEEK + 1

/** The day of the device's day number [day], or null outside 1..7. */
internal fun deviceDayOf(day: Int): DayOfWeek? =
    if (day in 1..DAYS_PER_WEEK) DayOfWeek.of((day + DAYS_PER_WEEK - 2) % DAYS_PER_WEEK + 1) else null

internal fun FeedingPlanDto.toDomain() = FeedingPlan(
    deviceId = deviceId,
    active = active,
    dualHopper = dualHopper,
    groups = groups.map { group ->
        FeedingGroup(
            days = group.days.mapNotNull(::deviceDayOf).toSet(),
            meals = group.meals.map { it.toDomain(dualHopper) },
        )
    },
)

private fun MealDto.toDomain(dual: Boolean) = Meal(
    id = id,
    time = LocalTime.ofSecondOfDay(time.toLong().coerceIn(0, LocalTime.MAX.toSecondOfDay().toLong())),
    portions = portions(dual, amount, amount1, amount2),
)

internal fun FeedingGroup.toDto(dual: Boolean) = FeedingGroupDto(
    days = days.map { it.toDeviceDay() }.sorted(),
    meals = meals.sortedBy { it.time }.map { meal ->
        val portions = meal.portions
        MealDto(
            // A new meal has no id yet; the server gives it one. The field is required on read
            // only, and an empty one is what the API documents as "left out".
            id = meal.id.orEmpty(),
            time = meal.time.toSecondOfDay(),
            amount = (portions as? Portions.Single)?.amount?.takeUnless { dual },
            amount1 = (portions as? Portions.Dual)?.hopper1?.takeIf { dual },
            amount2 = (portions as? Portions.Dual)?.hopper2?.takeIf { dual },
        )
    },
)

internal fun DeferredFeedDto.toDomain(dual: Boolean) = DeferredFeed(
    id = id,
    fireAt = fireAt?.let { Instant.ofEpochMilli((it * MILLIS_PER_SECOND).toLong()) },
    portions = portions(dual, amount, amount1, amount2),
)

internal fun DeferredFeedRequest.toDto() = DeferredFeedRequestDto(
    date = date.toString(),
    time = time.format(HOUR_MINUTE),
    amount = (portions as? Portions.Single)?.amount,
    amount1 = (portions as? Portions.Dual)?.hopper1,
    amount2 = (portions as? Portions.Dual)?.hopper2,
)

private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")

/** A Dual-Hopper's amounts are per hopper; a single one has one amount. A missing one is 0. */
private fun portions(dual: Boolean, amount: Int?, amount1: Int?, amount2: Int?): Portions =
    if (dual) Portions.Dual(amount1 ?: 0, amount2 ?: 0) else Portions.Single(amount ?: 0)

/** `Delivered`; a value the server added later reads as queued, the cautious way to say it. */
internal fun deliveryOf(delivered: String): CommandDelivery = when (delivered) {
    Delivered.MQTT -> CommandDelivery.Mqtt
    Delivered.LOCAL -> CommandDelivery.Local
    else -> CommandDelivery.Queued
}

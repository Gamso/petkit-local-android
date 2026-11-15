package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.DeferredFeedDto
import io.github.gamso.petkitlocal.mock.api.FeedingGroupDto
import io.github.gamso.petkitlocal.mock.api.FeedingPlanDto
import io.github.gamso.petkitlocal.mock.api.MealDto
import io.github.gamso.petkitlocal.mock.api.plainNumber
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One meal in the device's own shape: `t` seconds since local midnight, `a` or `a1`/`a2`. */
data class Meal(
    val id: String,
    val t: Int,
    val a: Int?,
    val a1: Int?,
    val a2: Int?,
)

/** Meals served on the device's day numbers, 1 = Sunday ... 7 = Saturday. */
data class MealGroup(
    val days: List<Int>,
    val meals: List<Meal>,
)

data class DeferredFeed(
    val id: String,
    val fireAt: Double,
    val a: Int?,
    val a1: Int?,
    val a2: Int?,
)

/** A feeder's plan, as petkit-local stores it in `config["feed_schedule"]`. */
class FeedPlan(
    val dual: Boolean,
) {
    var active: Boolean = true
    var groups: List<MealGroup> = emptyList()
    val deferred: MutableList<DeferredFeed> = mutableListOf()

    fun dto(deviceId: Int) =
        FeedingPlanDto(
            deviceId = deviceId,
            active = active,
            dualHopper = dual,
            groups =
                groups.map { g ->
                    FeedingGroupDto(
                        days = g.days.toSortedSet().toList(),
                        meals = g.meals.map { MealDto(it.id, it.t, amount(it.a, it.a1), it.a1.takeIf { dual }, it.a2.takeIf { dual }) },
                    )
                },
        )

    fun deferredDto(f: DeferredFeed) = DeferredFeedDto(f.id, f.fireAt, amount(f.a, f.a1), f.a1.takeIf { dual }, f.a2.takeIf { dual })

    private fun amount(
        a: Int?,
        a1: Int?,
    ): Int? = if (dual) null else (a ?: a1)

    /** What the `feeding_schedule` text entity reads: the stored document, device keys and all. */
    fun document(): JsonObject =
        buildJsonObject {
            put(
                "schedule",
                buildJsonArray {
                    groups.forEach { g ->
                        add(
                            buildJsonObject {
                                put("re", g.days.joinToString(","))
                                put(
                                    "it",
                                    buildJsonArray {
                                        g.meals.forEach { m -> add(mealJson(m)) }
                                    },
                                )
                            },
                        )
                    }
                },
            )
            put(
                "deferred",
                buildJsonArray {
                    deferred.forEach { f ->
                        add(
                            buildJsonObject {
                                put("id", f.id)
                                put("fire_at", plainNumber(f.fireAt))
                                if (dual) {
                                    put("a1", f.a1)
                                    put("a2", f.a2)
                                } else {
                                    put("a", f.a)
                                }
                            },
                        )
                    }
                },
            )
            put("v", 2)
        }

    private fun mealJson(m: Meal) =
        buildJsonObject {
            put("id", m.id)
            put("t", m.t)
            if (dual) {
                put("a1", m.a1)
                put("a2", m.a2)
            } else {
                put("a", m.a)
            }
        }
}

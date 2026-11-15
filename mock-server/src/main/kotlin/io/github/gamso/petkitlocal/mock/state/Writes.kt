package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.api.EntityDto
import io.github.gamso.petkitlocal.mock.catalog.EntityDef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/*
 * The writes `/app/v1` accepts, validated as petkit-local validates them (`ha/commands.py::
 * payload_for` and `handle_ha_command`, `handlers/feeding.py`, `ha/schedules.py`): same codes,
 * same refusals, nothing stored when a value is refused.
 */

private fun refused(message: String) = ApiException(422, "refused", message)

private fun invalidSchedule(message: String) = ApiException(422, "invalid_schedule", message)

private fun invalidFeed(message: String) = ApiException(422, "invalid_feed", message)

const val TEXT_MAX_LENGTH = 255
const val MAX_AMOUNT = 255
const val MAX_GROUPS = 7
const val MAX_MEALS = 24
const val DAY_SECONDS = 86400

/** A JSON value that is an integer and not a boolean (Python's `isinstance(v, int)` minus bool). */
fun JsonElement?.asStrictInt(): Int? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p.booleanOrNull != null) return null
    return p.intOrNull ?: p.doubleOrNull?.takeIf { it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE }?.toInt()
}

private fun JsonElement?.asStrictNumber(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p.booleanOrNull != null) return null
    return p.doubleOrNull?.takeIf { it.isFinite() }
}

private fun JsonElement?.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** `HH:MM` or `HH:MM:SS` within the day, as seconds since midnight; null otherwise. */
fun parseTimeOfDay(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.toIntOrNull() ?: return null }
    val (h, m) = numbers
    val s = numbers.getOrElse(2) { 0 }
    if (h !in 0..23 || m !in 0..59 || s !in 0..59) return null
    return h * 3600 + m * 60 + s
}

private fun EntityDef.accepts(n: Double): Boolean = (min == null || n >= min) && (max == null || n <= max)

private fun EntityDef.boundsText(): String =
    when {
        min != null && max != null -> "between ${fmt(min)} and ${fmt(max)}"
        min != null -> "at least ${fmt(min)}"
        else -> "at most ${fmt(max!!)}"
    }

private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/**
 * Stores a typed value into a settable entity and answers `(delivered, entity)`.
 *
 * @throws ApiException 422 `refused` for a value of the wrong type or out of bounds.
 */
fun World.setEntity(
    device: MockDevice,
    def: EntityDef,
    value: JsonElement,
): Pair<String, EntityDto> {
    val path = def.valuePath
    var delivered = delivered(device)
    val stored: JsonElement =
        when (def.component) {
            "switch" -> {
                val b =
                    (value as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: throw refused("${def.name} takes true or false")
                when {
                    path == FEED_SCHEDULE_ACTIVE -> {
                        device.plan?.active = b
                        JsonPrimitive(b)
                    }

                    path.startsWith("local.") || path.startsWith("capabilities.") -> {
                        delivered = "local"
                        JsonPrimitive(b)
                    }

                    else -> {
                        JsonPrimitive(if (b) 1 else 0)
                    }
                }
            }

            "number" -> {
                val n = value.asStrictNumber() ?: throw refused("${def.name} takes a number")
                if (!def.accepts(n)) throw refused("${def.name} must be ${def.boundsText()}")
                if (path.startsWith("local.")) delivered = "local"
                json(n)
            }

            "select" -> {
                val label = value.asString()
                if (label == null || def.options?.contains(label) != true) {
                    throw refused("${def.name} must be one of: ${def.options.orEmpty().joinToString(", ")}")
                }
                def.optionValue(label)!!
            }

            "time" -> {
                val text = value.asString()
                val seconds = text?.let(::parseTimeOfDay) ?: throw refused("${def.name} must be a time of day, HH:MM or HH:MM:SS")
                JsonPrimitive(seconds)
            }

            "text" -> {
                val text = value.asString() ?: throw refused("${def.name} takes a string")
                if (text.length > TEXT_MAX_LENGTH) throw refused("${def.name} takes at most $TEXT_MAX_LENGTH characters")
                storedText(device, def, text)
            }

            else -> {
                throw refused("${def.name} cannot be set")
            }
        }
    // The value is checked first, as `payload_for` runs before the relay is looked for.
    requireRelay(device)
    change(device, reported = false) {
        if (path == "feed_schedule") {
            device.syncPlan()
        } else {
            device.doc[path] = stored
            if (path == FEED_SCHEDULE_ACTIVE) device.syncPlan()
        }
    }
    return delivered to device.render(def)
}

const val FEED_SCHEDULE_ACTIVE = "local.feedScheduleActive"

/** What a `text` entity stores, checked as the panel's schedule editors check it. */
private fun storedText(
    device: MockDevice,
    def: EntityDef,
    text: String,
): JsonElement {
    val path = def.valuePath
    return when {
        path.startsWith("multi_config.") -> {
            val ok = text.isBlank() || text.split(",").all { RANGE.matches(it.trim()) && rangeTimesValid(it.trim()) }
            if (!ok) throw refused("${def.name} must be HH:MM-HH:MM ranges, comma separated (e.g. 22:00-07:00), or empty for none")
            JsonPrimitive(text)
        }

        path == "schedule" -> {
            val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull()
            cleanPointList(parsed) ?: throw refused("not a valid cleaning schedule")
        }

        path == "feed_schedule" -> {
            val plan = device.plan ?: throw refused("${def.name} cannot be set")
            val parsed = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject
            val groups = parsed?.get("schedule")?.let { deviceGroups(it, plan.dual) } ?: throw refused("not a valid feeding schedule")
            plan.groups = groups
            plan.document()
        }

        else -> {
            JsonPrimitive(text)
        }
    }
}

private val RANGE = Regex("^\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}$")

private fun rangeTimesValid(range: String) = range.split("-").all { parseTimeOfDay(it) != null }

/** `[{id, repeats, time, type}]`, a litter box's timed jobs (`schedules.clean_point_list`). */
private fun cleanPointList(value: JsonElement?): JsonArray? {
    if (value !is JsonArray) return null
    for (entry in value) {
        if (entry !is JsonObject) return null
        val minute = entry["time"].asStrictInt() ?: return null
        entry["type"].asStrictInt() ?: return null
        entry["id"].asStrictInt() ?: return null
        if (minute !in 0 until 1440) return null
        val days = (entry["repeats"] as? JsonPrimitive)?.content?.split(",")?.map { it.trim().toIntOrNull() } ?: return null
        if (days.isEmpty() || days.any { it == null || it !in 1..7 }) return null
    }
    return value
}

/** A stored plan's groups (`re`, `it`, `t`, `a`/`a1`/`a2`), as `clean_feed_schedule` accepts them. */
private fun deviceGroups(
    value: JsonElement,
    dual: Boolean,
): List<MealGroup>? {
    if (value !is JsonArray) return null
    return value.map { g ->
        if (g !is JsonObject) return null
        val days = (g["re"] as? JsonPrimitive)?.content?.split(",")?.map { it.trim().toIntOrNull() } ?: return null
        if (days.isEmpty() || days.any { it == null || it !in 1..7 }) return null
        val items = g["it"] as? JsonArray ?: JsonArray(emptyList())
        val meals =
            items.map { m ->
                if (m !is JsonObject) return null
                val t = m["t"].asStrictInt()?.takeIf { it in 0 until DAY_SECONDS } ?: return null
                val id = (m["id"] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content ?: "n_$t"
                if (dual) {
                    val a1 = m["a1"].asStrictInt()?.takeIf { it in 0..MAX_AMOUNT } ?: return null
                    val a2 = (m["a2"] ?: JsonPrimitive(0)).asStrictInt()?.takeIf { it in 0..MAX_AMOUNT } ?: return null
                    Meal(id, t, null, a1, a2)
                } else {
                    val a = (m["a"] ?: m["a1"]).asStrictInt()?.takeIf { it in 0..MAX_AMOUNT } ?: return null
                    Meal(id, t, a, null, null)
                }
            }
        MealGroup(days.filterNotNull(), meals)
    }
}

/** Keeps the doc in step with a feeder's plan: the text entity and the pause switch read it. */
fun MockDevice.syncPlan() {
    val plan = plan ?: return
    doc["feed_schedule"] = plan.document()
    doc[FEED_SCHEDULE_ACTIVE] = JsonPrimitive(plan.active)
}

// --- feeding plan ------------------------------------------------------------------------------------

/** `PUT /devices/{id}/schedule`: `groups`, `active`, or both, applied together. */
fun World.writePlan(
    device: MockDevice,
    body: JsonObject,
): String {
    val plan = device.plan ?: throw ApiException(404, "not_a_feeder", "this device has no feeding plan")
    if ("groups" !in body && "active" !in body) throw ApiException(400, "bad_request", "send groups, active, or both")
    val active =
        body["active"]?.let {
            (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.booleanOrNull ?: throw invalidSchedule("active must be true or false")
        }
    val groups = body["groups"]?.let { apiGroups(it, plan.dual) }
    change(device, reported = false) {
        if (groups != null) plan.groups = groups
        if (active != null) plan.active = active
        device.syncPlan()
    }
    return delivered(device)
}

private fun apiGroups(
    value: JsonElement,
    dual: Boolean,
): List<MealGroup> {
    if (value !is JsonArray || value.size > MAX_GROUPS) throw invalidSchedule("groups must be a list of at most $MAX_GROUPS")
    return value.map { group ->
        if (group !is JsonObject) throw invalidSchedule("each group must be an object")
        val days = group["days"] as? JsonArray
        if (days == null || days.isEmpty() || days.any { it.asStrictInt() == null }) {
            throw invalidSchedule("a group's days must be a non-empty list of integers 1-7")
        }
        val dayNumbers = days.map { it.asStrictInt()!! }
        if (dayNumbers.any { it !in 1..7 }) throw invalidSchedule("not a valid feeding schedule")
        val meals = group["meals"] ?: JsonArray(emptyList())
        if (meals !is JsonArray || meals.size > MAX_MEALS) throw invalidSchedule("a group's meals must be a list of at most $MAX_MEALS")
        MealGroup(dayNumbers, meals.map { apiMeal(it, dual) })
    }
}

private fun apiMeal(
    meal: JsonElement,
    dual: Boolean,
): Meal {
    if (meal !is JsonObject) throw invalidSchedule("each meal must be an object")
    val t = meal["time"].asStrictInt() ?: throw invalidSchedule("a meal's time is seconds since local midnight, an integer")
    if (t !in 0 until DAY_SECONDS) throw invalidSchedule("not a valid feeding schedule")
    val id = meal["id"].asString()?.takeIf { it.isNotEmpty() }?.take(64) ?: "n_$t"

    fun amount(
        name: String,
        default: Int?,
    ): Int {
        val raw = meal[name] ?: default?.let { JsonPrimitive(it) }
        if (raw == null || raw is JsonNull) throw invalidSchedule("a meal on this feeder needs an integer $name")
        val n = raw.asStrictInt() ?: throw invalidSchedule("$name must be an integer")
        if (n !in 0..MAX_AMOUNT) throw invalidSchedule("not a valid feeding schedule")
        return n
    }
    return if (dual) Meal(id, t, null, amount("amount1", null), amount("amount2", 0)) else Meal(id, t, amount("amount", null), null, null)
}

// --- one-off feeds -------------------------------------------------------------------------------------

private val DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(java.time.format.ResolverStyle.STRICT)

/** `POST /devices/{id}/deferred-feeds`: one feed at a date and time of the feeder's timezone. */
fun World.addDeferredFeed(
    device: MockDevice,
    body: JsonObject,
): Pair<String, DeferredFeed> {
    val plan = device.plan ?: throw ApiException(404, "not_a_feeder", "this device has no feeding plan")
    val date = body["date"].asString()
    val time = body["time"].asString()
    if (date == null || time == null) throw invalidFeed("date (YYYY-MM-DD) and time (HH:MM) are required")
    val names = if (plan.dual) listOf("amount1", "amount2") else listOf("amount")
    if (names.none { it in body }) {
        throw invalidFeed(if (plan.dual) "this feeder needs amount1, amount2 or both" else "this feeder needs an amount")
    }
    val amounts =
        names.map { name ->
            val raw = body[name] ?: JsonPrimitive(0)
            val n = raw.asStrictInt() ?: throw invalidFeed("amounts must be integers")
            if (n !in
                0..MAX_AMOUNT
            ) {
                throw invalidFeed("${if (plan.dual) "a" + name.last() else "a"} must be a whole number from 0 to $MAX_AMOUNT")
            }
            n
        }
    val at =
        try {
            if (!Regex("^\\d{1,2}:\\d{2}$").matches(time)) throw DateTimeParseException("time", time, 0)
            val (h, m) = time.split(":").map { it.toInt() }
            LocalDate.parse(date, DATE).atTime(LocalTime.of(h, m)).atZone(clock.zone)
        } catch (_: DateTimeParseException) {
            throw invalidFeed("bad date/time")
        } catch (_: java.time.DateTimeException) {
            throw invalidFeed("bad date/time")
        }
    val fireAt = at.toEpochSecond().toDouble()
    if (fireAt <= clock.now()) throw invalidFeed("time is in the past")
    val seconds = at.hour * 3600 + at.minute * 60
    val id = "d_${at.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE)}_$seconds"
    val feed =
        if (plan.dual) DeferredFeed(id, fireAt, null, amounts[0], amounts[1]) else DeferredFeed(id, fireAt, amounts[0], null, null)
    change(device, reported = false) {
        plan.deferred.removeAll { it.id == id }
        plan.deferred += feed
        device.syncPlan()
    }
    return delivered(device) to feed
}

fun World.deleteDeferredFeed(
    device: MockDevice,
    feedId: String,
): String {
    val plan = device.plan ?: throw ApiException(404, "not_a_feeder", "this device has no feeding plan")
    if (plan.deferred.none { it.id == feedId }) throw ApiException(404, "feed_not_found", "no such one-off feed")
    change(device, reported = false) {
        plan.deferred.removeAll { it.id == feedId }
        device.syncPlan()
    }
    return delivered(device)
}

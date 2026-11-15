package org.petkitlocal.core.domain.device

import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue

/** Why a value cannot be written to a setting; [code] is what a refusal carries. */
sealed interface EntityWriteProblem {
    val code: String

    data object NotSettable : EntityWriteProblem {
        override val code = "not_settable"
    }

    /** Not the type the entity's component takes (a text for a switch...). */
    data object WrongType : EntityWriteProblem {
        override val code = "wrong_type"
    }

    data class BelowMin(val min: Double) : EntityWriteProblem {
        override val code = "below_min"
    }

    data class AboveMax(val max: Double) : EntityWriteProblem {
        override val code = "above_max"
    }

    data object NotAnOption : EntityWriteProblem {
        override val code = "not_an_option"
    }

    /** Not `HH:MM` or `HH:MM:SS` within a day. */
    data object InvalidTime : EntityWriteProblem {
        override val code = "invalid_time"
    }

    data class TooLong(val maxLength: Int) : EntityWriteProblem {
        override val code = "too_long"
    }
}

/**
 * The rules of `PUT /devices/{id}/entities/{key}` (api/openapi.yaml): a boolean for a switch, a
 * number within `min`/`max` for a number, one of `options` for a select, `HH:MM` for a time, at
 * most 255 characters for a text. The server checks them too; checking first lets an editor say
 * what is wrong next to the value instead of after a round trip.
 */
object EntityWrites {
    const val TEXT_MAX_LENGTH = 255

    /** The components a settings screen has an editor for. */
    val EDITABLE = setOf(
        EntityComponent.Switch,
        EntityComponent.Number,
        EntityComponent.Select,
        EntityComponent.Time,
        EntityComponent.Text,
    )

    private const val SECONDS_PER_DAY = 24 * 60 * 60

    /** Whether a settings screen can offer an editor for [entity]. */
    fun isEditable(entity: Entity): Boolean = entity.settable && entity.component in EDITABLE

    /** What is wrong with writing [value] to [entity], or null when the server should take it. */
    fun problem(entity: Entity, value: EntityValue): EntityWriteProblem? {
        if (!isEditable(entity)) return EntityWriteProblem.NotSettable
        return when (entity.component) {
            EntityComponent.Switch -> if (value is EntityValue.Bool) null else EntityWriteProblem.WrongType
            EntityComponent.Number -> numberProblem(entity, value)
            else -> textProblem(entity, value as? EntityValue.Text ?: return EntityWriteProblem.WrongType)
        }
    }

    /** A select, time or text: each takes a string. */
    private fun textProblem(entity: Entity, value: EntityValue.Text): EntityWriteProblem? = when (entity.component) {
        EntityComponent.Select -> if (value.value in entity.options) null else EntityWriteProblem.NotAnOption
        EntityComponent.Time -> if (parseTime(value.value) == null) EntityWriteProblem.InvalidTime else null
        else -> if (value.value.length > TEXT_MAX_LENGTH) EntityWriteProblem.TooLong(TEXT_MAX_LENGTH) else null
    }

    private fun numberProblem(entity: Entity, value: EntityValue): EntityWriteProblem? {
        val number = (value as? EntityValue.Number)?.value?.takeIf { it.isFinite() }
            ?: return EntityWriteProblem.WrongType
        val min = entity.min
        val max = entity.max
        return when {
            min != null && number < min -> EntityWriteProblem.BelowMin(min)
            max != null && number > max -> EntityWriteProblem.AboveMax(max)
            else -> null
        }
    }

    /** The step a number moves by: the entity's own, or 1. */
    fun step(entity: Entity): Double = entity.step?.takeIf { it > 0 } ?: 1.0

    /**
     * [value] on the entity's grid: a whole number of steps from its minimum (or from 0), within
     * its bounds. A slider's free position becomes a value the device has.
     */
    fun snap(entity: Entity, value: Double): Double {
        val step = step(entity)
        val origin = entity.min ?: 0.0
        val snapped = origin + ((value - origin) / step).roundToLong() * step
        val low = entity.min ?: Double.NEGATIVE_INFINITY
        val high = entity.max ?: Double.POSITIVE_INFINITY
        // Rounding error must not leave a value like 0.30000000000000004 on screen or on the wire.
        return roundTo(snapped.coerceIn(low, high), step)
    }

    private fun roundTo(value: Double, step: Double): Double {
        val decimals = step.toBigDecimal().stripTrailingZeros().scale().coerceAtLeast(0)
        return value.toBigDecimal().setScale(decimals, java.math.RoundingMode.HALF_UP).toDouble()
    }

    /**
     * A time entity's value as a time of day: the API sends seconds since midnight; a text
     * `HH:MM[:SS]` is read too. Null when unreported or unreadable.
     */
    fun timeOfDay(value: EntityValue): LocalTime? = when (value) {
        is EntityValue.Number -> value.value.toInt().takeIf { it in 0 until SECONDS_PER_DAY }
            ?.let { LocalTime.ofSecondOfDay(it.toLong()) }

        is EntityValue.Text -> parseTime(value.value)

        else -> null
    }

    /** `HH:MM`, what a time write sends. */
    fun timeText(time: LocalTime): String = "%02d:%02d".format(java.util.Locale.ROOT, time.hour, time.minute)

    /** `HH:MM` or `HH:MM:SS` within a day, or null. */
    fun parseTime(text: String): LocalTime? {
        val trimmed = text.trim()
        if (!TIME_PATTERN.matches(trimmed)) return null
        return try {
            LocalTime.parse(trimmed)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private val TIME_PATTERN = Regex("""\d{2}:\d{2}(:\d{2})?""")

    /** The option a select is on: its `display` label, or a text value that is one of the options. */
    fun selectedOption(entity: Entity): String? = entity.display?.takeIf { it in entity.options }
        ?: (entity.value as? EntityValue.Text)?.value?.takeIf { it in entity.options }
}

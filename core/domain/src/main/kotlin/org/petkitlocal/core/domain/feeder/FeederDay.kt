package org.petkitlocal.core.domain.feeder

import java.util.Locale
import org.petkitlocal.core.domain.activity.ActivityEntry
import org.petkitlocal.core.domain.activity.ActivityKind
import org.petkitlocal.core.domain.activity.ActivityRules
import org.petkitlocal.core.domain.activity.StepLabel
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.model.TimelineItem

/** What a row of the feeder's day is about, which picks the chips that count it. */
enum class FeederDayKind {
    /** A meal the feeder served, or tried to serve and failed. */
    Meal,

    /** A pet the camera saw: passing by, or eating. */
    Camera,

    /** Anything else, a fault say: counted under All only. */
    Other,
}

/** The chips over the feeder's day, in their order. */
enum class FeederDayFilter {
    All,
    Meals,

    /** Only on a feeder the server says has a camera (`is_camera`). */
    Camera,
}

/** One row of the feeder's day: a timeline card read as an activity row, and what the feeder adds. */
data class FeederDayRow(
    val entry: ActivityEntry,
    val kind: FeederDayKind,
    /**
     * What a meal put out, as its card says: per hopper on a Dual-Hopper. Null when the card does
     * not say, and for a row that is not a meal.
     */
    val portions: Portions?,
    /** The clip the row opens, the event's short one first; null when it has none. */
    val clip: MediaFile?,
) {
    val id: Long get() = entry.item.id

    fun matches(filter: FeederDayFilter): Boolean = when (filter) {
        FeederDayFilter.All -> true
        FeederDayFilter.Meals -> kind == FeederDayKind.Meal
        FeederDayFilter.Camera -> kind == FeederDayKind.Camera
    }
}

/**
 * The feeder's day (SPEC.md §5.6, Today): its meals, served or failed, and the pets its camera
 * saw, newest first, behind counted chips. A camera feeder's eating episodes (#68) are rows of it
 * too, under "At the camera"; the figures and chart drawn from them ([FeederEating]) go above the
 * chips.
 */
data class FeederDay(
    /** Today's rows, newest first. */
    val rows: List<FeederDayRow> = emptyList(),
    /** The chips this feeder can fill: no "At the camera" without a camera. */
    val filters: List<FeederDayFilter> = listOf(FeederDayFilter.All, FeederDayFilter.Meals),
) {
    fun rows(filter: FeederDayFilter): List<FeederDayRow> = rows.filter { it.matches(filter) }

    fun count(filter: FeederDayFilter): Int = rows.count { it.matches(filter) }
}

/** How the feeder page reads its day. */
object FeederDayRules {
    /** The first word petkit-local gives an eating episode's reports: "Eating started", "Eating done". */
    private const val EATING = "eating"

    /**
     * The day of a feeder from [entries] (its cards of today, newest first); [camera] adds the
     * chip of what the camera saw.
     */
    fun day(entries: List<ActivityEntry>, camera: Boolean): FeederDay = FeederDay(
        rows = entries.map { entry ->
            val kind = kind(entry.item)
            FeederDayRow(
                entry = entry,
                kind = kind,
                portions = entry.item.served.takeIf { kind == FeederDayKind.Meal },
                clip = clip(entry.item),
            )
        },
        filters = listOfNotNull(FeederDayFilter.All, FeederDayFilter.Meals, FeederDayFilter.Camera.takeIf { camera }),
    )

    fun kind(item: TimelineItem): FeederDayKind = when (ActivityRules.kind(item)) {
        // Served and eaten meals share the feeding chip on the server; only the label tells them apart.
        ActivityKind.Meal -> if (eatingLabel(item)) FeederDayKind.Camera else FeederDayKind.Meal

        ActivityKind.PetSeen -> FeederDayKind.Camera

        else -> FeederDayKind.Other
    }

    /** The card says it did not run to its end: "Feeding failed (nothing dispensed)". */
    fun failed(item: TimelineItem): Boolean = item.label?.let(StepLabel::parse)?.done == false

    /**
     * The card is a pet eating at a camera feeder: "Eating done", or "Eating started" for an
     * episode whose end never arrived. Not a meal served, nor a pet that only came by.
     */
    fun isEating(item: TimelineItem): Boolean = ActivityRules.kind(item) == ActivityKind.Meal && eatingLabel(item)

    private fun eatingLabel(item: TimelineItem): Boolean =
        item.label?.trim()?.lowercase(Locale.ROOT)?.startsWith(EATING) == true

    /** The short clip, else the whole recording, else the timelapse. */
    private fun clip(item: TimelineItem): MediaFile? {
        val clips = item.media?.clips.orEmpty()
        return clips.firstOrNull { it.kind == MediaKind.Highlight } ?: clips.firstOrNull()
    }
}

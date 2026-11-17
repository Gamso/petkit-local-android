package org.petkitlocal.core.domain.activity

import java.time.Instant
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem

/** What a row of a device's activity is about, which picks its sentence and its picture. */
enum class ActivityKind {
    /** A toilet visit. */
    Visit,

    /** A drink at a fountain. */
    Drink,

    /** A meal served, or eaten by a pet a camera feeder recognised. */
    Meal,

    /** A pet seen without using the device (a camera box's code 20). */
    PetSeen,

    /** Anything else: a cleaning on its own, a fault. */
    Event,
}

/** The picture of a row about a pet. */
sealed interface ActivityAvatar {
    /**
     * A pet of the household: its photo when it has one ([photoUrl], fetched with the bearer
     * header), else its initial on its colour, [colorIndex] being its place in the household.
     */
    data class Pet(val name: String, val photoUrl: String?, val colorIndex: Int) : ActivityAvatar

    /** The device could not tell which pet it was, or the pet is no longer in the household. */
    data object Unknown : ActivityAvatar
}

/** A follow-up shown under its row: the cleaning after a visit, the deodorizing after that. */
data class ActivityStep(
    val id: Long,
    val at: Instant?,
    /** The server's English label, shown as it is when [parsed] could not read it. */
    val label: String,
    val parsed: StepLabel?,
) {
    /** It did not run to its end: shown in the error colour, with its reason. */
    val failed: Boolean get() = parsed?.done == false
}

/**
 * One row of a device's activity (docs/DESIGN.md, TimelineRow): a timeline card and the steps the
 * server grouped under it, which the row lists rather than showing each on a row of its own.
 */
data class ActivityEntry(
    val item: TimelineItem,
    val kind: ActivityKind,
    /** The pet's name: the household's, else the one the card carries; null when it names none. */
    val petName: String?,
    /** The pet's picture; null for a row drawn with its kind's symbol. */
    val avatar: ActivityAvatar?,
    /** The card's own label read as an outcome ("Periodic cleaning completed"), when it is one. */
    val outcome: StepLabel?,
    /** The steps the official app shows, in the server's order; low-level ones are left out. */
    val steps: List<ActivityStep>,
)

/** How a device page lists its day: one row per visit, drink or meal, its follow-ups under it. */
object ActivityRules {
    /** A row for each of [items] (one device's cards, newest first), [pets] in the household's order. */
    fun entries(items: List<TimelineItem>, pets: List<Pet>): List<ActivityEntry> = items.map { entry(it, pets) }

    fun entry(item: TimelineItem, pets: List<Pet>): ActivityEntry {
        val kind = kind(item)
        val index = pets.indexOfFirst { it.id == item.pet?.id }
        val pet = pets.getOrNull(index)
        val pictured = when (kind) {
            ActivityKind.Visit, ActivityKind.Drink -> true

            // A feeder without a camera names no pet: its meal keeps the feeding symbol.
            ActivityKind.Meal -> item.pet != null

            ActivityKind.PetSeen, ActivityKind.Event -> false
        }
        return ActivityEntry(
            item = item,
            kind = kind,
            petName = pet?.name ?: item.pet?.name,
            avatar = when {
                !pictured -> null
                pet != null -> ActivityAvatar.Pet(pet.name, pet.photoUrl, index)
                else -> ActivityAvatar.Unknown
            },
            outcome = item.label?.let(StepLabel::parse),
            steps = item.steps
                .filterNot { it.detail }
                .map { ActivityStep(it.id, it.ts, it.label, StepLabel.parse(it.label)) },
        )
    }

    fun kind(item: TimelineItem): ActivityKind {
        val categories = item.categories
        return when {
            TimelineCategory.Toileting in categories -> ActivityKind.Visit
            TimelineCategory.Drinking in categories -> ActivityKind.Drink
            TimelineCategory.Feeding in categories -> ActivityKind.Meal
            TimelineCategory.Pet in categories -> ActivityKind.PetSeen
            else -> ActivityKind.Event
        }
    }
}

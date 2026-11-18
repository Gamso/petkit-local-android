package org.petkitlocal.core.domain.model

import java.time.Instant

/**
 * One History card. Home reads the day's cards for its counts and 24-hour strips; History shows
 * them, with their [media]; a device page lists them with their [steps].
 */
data class TimelineItem(
    val id: Long,
    /** `visit`, `event`, or a grouped cycle kind, as the server names it. */
    val kind: String,
    /** The filter chips this card falls under. */
    val categories: Set<TimelineCategory>,
    val deviceId: Long?,
    val deviceType: String?,
    /** Sort key: when the closing report arrived. */
    val ts: Instant?,
    /** What to show: when the pet entered, say. Falls back to [ts]. */
    val displayTs: Instant?,
    val eventType: String?,
    val eventKind: String?,
    /** The server's English label; null for a visit. */
    val label: String?,
    val pet: PetRef?,
    /**
     * How long the episode lasted: set on a visit, a drink and a meal eaten at a camera feeder;
     * null when not known, and on every other card.
     */
    val durationSec: Int?,
    val weightG: Int?,
    val facts: List<String>,
    /** Its photos and videos; null when it has none. */
    val media: TimelineMedia? = null,
    /** The episode's other reports, in the server's order: the cleaning that followed a visit. */
    val steps: List<TimelineStep> = emptyList(),
    /**
     * What a finished feed put out, per hopper on a Dual-Hopper; null on every other card, and on
     * a feed whose report states no amount (a meal that put nothing out is a total of 0).
     */
    val served: Portions? = null,
) {
    /** When the card happened, as a screen places it. */
    val shownAt: Instant? get() = displayTs ?: ts
}

/**
 * One report of a card's episode besides the one heading it. [label] is the server's English
 * label; a [detail] step is a low-level one (a weight check, a mechanism start) the official app
 * does not show.
 */
data class TimelineStep(val id: Long, val ts: Instant?, val label: String, val detail: Boolean)

/** A pet as a timeline card names it; [name] is null once the pet was deleted. */
data class PetRef(val id: Long, val name: String?)

/** The filter chips of `GET /timeline` (except `all`), in the server's order. */
enum class TimelineCategory(val key: String) {
    Pet("pet"),
    Toileting("toileting"),
    Drinking("drinking"),
    Feeding("feeding"),
    Cleaning("cleaning"),
    HealthAlert("health_alert"),
    Fault("fault"),
    ;

    companion object {
        /** The category for a server filter key, or null for one this app does not know. */
        fun fromKey(key: String): TimelineCategory? = entries.firstOrNull { it.key == key }
    }
}

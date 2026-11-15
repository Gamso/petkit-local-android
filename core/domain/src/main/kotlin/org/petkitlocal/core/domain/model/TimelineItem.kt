package org.petkitlocal.core.domain.model

import java.time.Instant

/**
 * One History card. Home reads the day's cards for its counts and 24-hour strips; History (M3)
 * shows them. Media and steps are not cached yet.
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
    val durationSec: Int?,
    val weightG: Int?,
    val facts: List<String>,
) {
    /** When the card happened, as a screen places it. */
    val shownAt: Instant? get() = displayTs ?: ts
}

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

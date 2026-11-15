package org.petkitlocal.core.domain.device

import org.petkitlocal.core.domain.model.DeviceAction

/** A device's actions as its action sheet lays them out. */
data class ActionLayout(
    /** The everyday ones, always in view: at most [DeviceActionOrder.VISIBLE]. */
    val main: List<DeviceAction>,
    /** Everything else, behind "More": overflow, then maintenance, then what cannot be undone. */
    val more: List<DeviceAction>,
)

/**
 * Which actions a device page keeps in view, and in what order. The server lists a model's
 * buttons in its own order; the app puts the ones used every day first (cleaning, deodorizing
 * and levelling a litter box; feeding; refilling and flushing a fountain), by key and never by
 * model, so an action the server adds later still shows, after the known ones.
 *
 * Maintenance (calibration, resets, maintenance mode, emptying the litter) and destructive
 * actions are never in [ActionLayout.main]: they stay one tap further away, and a destructive
 * one is still confirmed.
 */
object DeviceActionOrder {
    /** The actions in view while the sheet is collapsed. */
    const val VISIBLE = 3

    /** Lower first. An action not listed ranks [DEFAULT_RANK], after every known everyday one. */
    private val RANKS: Map<String, Int> = mapOf(
        // Litter box.
        "cleaning_start" to 0,
        "deodorize" to 1,
        "level_litter" to 2,
        "pack_waste" to 6,
        // Feeder.
        "feed" to 0,
        "feed_hopper_1" to 1,
        "feed_hopper_2" to 2,
        "food_replenished" to 4,
        "cancel_manual_feed" to 7,
        // Fountain.
        "fountain_refill" to 0,
        "fountain_flush" to 1,
        // Any family.
        "light" to 3,
        "play_sound" to 5,
        "power_on" to 8,
        "power_off" to 8,
        "pause" to 9,
        "resume" to 9,
        "ptz_left" to 10,
        "ptz_right" to 10,
    )
    private const val DEFAULT_RANK = 20

    /** Kept behind "More" even when there is room in view. */
    private val MAINTENANCE_KEYS = setOf(
        "finish_litter_calibration",
        "dump_litter",
        "open_sealed_door",
        "fountain_reset_circulation_pump",
    )
    private val MAINTENANCE_PREFIXES = listOf("maintenance_", "reset")

    fun isMaintenance(action: DeviceAction): Boolean =
        action.key in MAINTENANCE_KEYS || MAINTENANCE_PREFIXES.any { action.key.startsWith(it) }

    /** An action for every day: neither maintenance nor destructive. */
    fun isEveryday(action: DeviceAction): Boolean = !action.destructive && !isMaintenance(action)

    /**
     * Whether a page needs its action sheet: when the device has an everyday action, or an action
     * the page offers nowhere else. [onParts] holds the keys the page's hotspot pills offer. A
     * device whose only buttons are maintenance or destructive ones, each on its part's pill (a
     * Bluetooth fountain's filter reset), gets no sheet: it would hold nothing but "More".
     */
    fun needsSheet(actions: List<DeviceAction>, onParts: Set<String>): Boolean =
        actions.any { isEveryday(it) || it.key !in onParts }

    /**
     * [actions] in the server's order, laid out for the sheet. [pinned] is the user's own choice
     * for this device ([QuickActionRow]), null when they made none: then the app chooses.
     *
     * A pinned key keeps its slot while the device lists it; when the server no longer does, that
     * slot falls back to the app's own ranking (the best everyday action not already in view),
     * and nothing else changes. Pinned, a maintenance or destructive action is in view like any
     * other, and a destructive one is still confirmed when pressed. [ActionLayout.more] holds
     * every action not in view, in the app's order, whoever chose the row.
     */
    fun layout(actions: List<DeviceAction>, pinned: List<String>? = null, visible: Int = VISIBLE): ActionLayout {
        val (destructive, safe) = actions.partition { it.destructive }
        val (maintenance, everyday) = safe.partition(::isMaintenance)
        // sortedBy is stable: equal ranks keep the server's order.
        val ranked = everyday.sortedBy { RANKS[it.key] ?: DEFAULT_RANK }
        if (pinned == null) {
            return ActionLayout(
                main = ranked.take(visible),
                more = ranked.drop(visible) + maintenance + destructive,
            )
        }
        val byKey = actions.associateBy { it.key }
        val slots = pinned.distinct().take(visible)
        val fallbacks = ranked.filter { it.key !in slots }.iterator()
        val main = slots.mapNotNull { key -> byKey[key] ?: fallbacks.nextOrNull() }
        val inView = main.mapTo(HashSet()) { it.key }
        return ActionLayout(
            main = main,
            more = (ranked + maintenance + destructive).filterNot { it.key in inView },
        )
    }

    private fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null
}

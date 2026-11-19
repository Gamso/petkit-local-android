package org.petkitlocal.core.domain.fountain

import org.petkitlocal.core.domain.activity.ActivityEntry
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.Pet

/** Everything the fountain page shows, derived from the cache by [FountainRules]. */
data class FountainPage(
    val device: Device,
    /** The live status line: idle, offline, or the device's error. */
    val status: DeviceStatus,
    /** For a Bluetooth fountain, the device relaying it, once the cache holds it. */
    val relay: Device?,
    /** The header's chips: the features the fountain reports ON, nothing for one that is off. */
    val modes: List<FountainMode>,
    /** The fountain's mode as the bottom bar offers it, when the server lets it be set. */
    val modeBar: FountainModeBar?,
    val hotspots: List<Hotspot>,
    /** The buttons of the action sheet: the device's, but those on a part this model lacks. */
    val actions: List<DeviceAction>,
    /** Whether this model records drinks (the W7H); a Bluetooth fountain reports none. */
    val recordsDrinks: Boolean,
    val today: DrinkStats,
    val yesterday: DrinkStats,
    /**
     * Whether this fountain's drinks say how long they lasted: false when it has drinks today or
     * yesterday and none of them has a duration (petkit-local sends `duration_sec: null` then).
     */
    val durationsKnown: Boolean,
    /** How long the pump has run today, in seconds, by the fountain's count; null when not reported. */
    val pumpTodaySec: Int?,
    /** Today's drinks, oldest first: the dots of the 24-hour chart. */
    val drinks: List<Drink>,
    /** The pets that drank today or yesterday, in the household's order. */
    val perPet: List<PetDrinks>,
    /** Pets in the household's order, which picks their colour. */
    val pets: List<Pet>,
    /** Today's cards of this fountain, newest first, each with its follow-ups. */
    val activity: List<ActivityEntry> = emptyList(),
) {
    /** The pets the chart can be filtered on: those with a drink today, in the household's order. */
    val chartPets: List<Pet> get() = drinks.mapNotNull { it.petId }.toSet().let { ids -> pets.filter { it.id in ids } }

    /** The battery's pill, when the fountain reports its level: what the top status line shows. */
    val battery: Hotspot? get() = hotspots.firstOrNull { it.part == DevicePart.Battery }

    /** A pet's place in the household's order, which picks its colour; null for an unknown pet. */
    fun colorIndexOf(petId: Long?): Int? = pets.indexOfFirst { it.id == petId }.takeIf { it >= 0 }
}

/**
 * A status chip of the header: a feature entity [key] reports ON (a switch, or the smart option
 * of a mode select the bottom bar does not offer). An off feature has no chip.
 */
data class FountainMode(val key: String, val kind: FountainModeKind)

/**
 * The fountain's mode select, as the bottom bar offers it: entity [key], its [options] in the
 * server's order (their labels are translated by value), and the one the device last reported
 * ([selected], null while it has not said, or while it shows a state that is not an option, a
 * CTW3's battery mode). The bar shows [selected], never what was tapped.
 *
 * A fountain whose bar in PetKit's app also has a Pause and a battery mode (the CTW3) has them
 * here too: [pause] before the options, [battery] after them.
 */
data class FountainModeBar(
    val key: String,
    val options: List<String>,
    val selected: String?,
    val pause: FountainPause? = null,
    val battery: FountainBatteryMode? = null,
)

/**
 * The fountain's Pause: switch [key], on while the pump runs (petkit-local's `ctw3_working`, the
 * right way up), so [paused] is its value inverted; null while it has not reported.
 */
data class FountainPause(val key: String, val paused: Boolean?)

/**
 * The battery mode a cordless fountain enters by itself when unplugged and leaves when plugged in
 * again: a state, never a choice. [active] while the fountain reports it; [settings] are the keys
 * of its battery working and sleep times the server lets be set, in that order.
 */
data class FountainBatteryMode(val active: Boolean, val settings: List<String>)

enum class FountainModeKind {
    /** Smart mode (the pump runs, then rests) rather than normal (it runs all the time). */
    Smart,
    DoNotDisturb,
    ChildLock,
}

/** A day's drinks at one fountain. [averageDurationSec] is null when no drink had a duration. */
data class DrinkStats(val drinks: Int, val averageDurationSec: Int?)

/** One dot of the 24-hour chart. */
data class Drink(
    val itemId: Long,
    /** Local minute of the day, 0 to 1439. */
    val minuteOfDay: Int,
    val durationSec: Int?,
    val petId: Long?,
)

/** One pet's drinks at this fountain, today and yesterday. */
data class PetDrinks(val pet: Pet, val today: Int, val yesterday: Int)

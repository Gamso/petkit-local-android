package org.petkitlocal.core.domain.fountain

import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.Pet

/** Everything the fountain page shows, derived from the cache by [FountainRules]. */
data class FountainPage(
    val device: Device,
    /** The live status line: idle, offline, or the device's error. */
    val status: DeviceStatus,
    /** For a Bluetooth fountain, the device relaying it, once the cache holds it. */
    val relay: Device?,
    /** The header's mode chips, from the switches and the mode the model reports. */
    val modes: List<FountainMode>,
    val hotspots: List<Hotspot>,
    /** The buttons of the action sheet: the device's, but those on a part this model lacks. */
    val actions: List<DeviceAction>,
    /** Whether this model records drinks (the W7H); a Bluetooth fountain reports none. */
    val recordsDrinks: Boolean,
    val today: DrinkStats,
    val yesterday: DrinkStats,
    /** How long the pump has run today, in seconds, by the fountain's count; null when not reported. */
    val pumpTodaySec: Int?,
    /** Today's drinks, oldest first: the dots of the 24-hour chart. */
    val drinks: List<Drink>,
    /** The pets that drank today or yesterday, in the household's order. */
    val perPet: List<PetDrinks>,
    /** Pets in the household's order, which picks their colour. */
    val pets: List<Pet>,
) {
    /** The pets the chart can be filtered on: those with a drink today, in the household's order. */
    val chartPets: List<Pet> get() = drinks.mapNotNull { it.petId }.toSet().let { ids -> pets.filter { it.id in ids } }

    /** A pet's place in the household's order, which picks its colour; null for an unknown pet. */
    fun colorIndexOf(petId: Long?): Int? = pets.indexOfFirst { it.id == petId }.takeIf { it >= 0 }
}

/**
 * A mode chip: a switch, or the smart mode of a fountain's mode select. A tap writes [onValue] or
 * [offValue] to entity [key]; the chip follows the device's next report, never the tap.
 */
data class FountainMode(
    val key: String,
    val kind: FountainModeKind,
    val on: Boolean,
    val onValue: EntityValue,
    val offValue: EntityValue,
)

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

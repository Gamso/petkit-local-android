package org.petkitlocal.core.domain.litter

import org.petkitlocal.core.domain.activity.ActivityEntry
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.Pet

/** Everything the litter box page shows (SPEC.md §5.3), derived from the cache by [LitterRules]. */
data class LitterBoxPage(
    val device: Device,
    /** The live status line: idle, a work mode, pet inside, offline, or the device's error. */
    val status: DeviceStatus,
    /** The header's mode chips, from the device's switches. */
    val modes: List<DeviceMode>,
    val hotspots: List<Hotspot>,
    /** The buttons of the action sheet: the device's, but those on a part this model lacks. */
    val actions: List<DeviceAction>,
    val today: VisitStats,
    val yesterday: VisitStats,
    /** Today's toilet visits, oldest first: the dots of the 24-hour chart. */
    val visits: List<Visit>,
    /** Pets in the household's order, which picks their colour. */
    val pets: List<Pet>,
    /** Today's cards of this device, newest first, each with its follow-ups. */
    val activity: List<ActivityEntry>,
    /** The Pura Air (K3) in the box's K3 slot, when it has one and the cache holds it. */
    val puraAir: Device? = null,
) {
    /** The pets the chart can be filtered on: those with a visit today, in the household's order. */
    val chartPets: List<Pet> get() = visits.mapNotNull { it.petId }.toSet().let { ids -> pets.filter { it.id in ids } }

    /** A pet's place in the household's order, which picks its colour; null for an unknown pet. */
    fun colorIndexOf(petId: Long?): Int? = pets.indexOfFirst { it.id == petId }.takeIf { it >= 0 }
}

/** A switch shown as a mode chip in the header: auto clean, auto deodorize. */
data class DeviceMode(val key: String, val name: String, val on: Boolean)

/** A day's toilet visits on one box. [averageDurationSec] is null when no visit had a duration. */
data class VisitStats(val visits: Int, val averageDurationSec: Int?)

/** One dot of the 24-hour chart. */
data class Visit(
    val itemId: Long,
    /** Local minute of the day, 0 to 1439. */
    val minuteOfDay: Int,
    val durationSec: Int?,
    val petId: Long?,
)

package org.petkitlocal.core.domain.devicepage

import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction

/**
 * Which parts each litter box model physically has, by codename, and what a paired Pura Air
 * adds. The server publishes some entities and buttons to every litter box alike (its common
 * bundle), so a model can list a consumable it does not have: the page shows a part, and offers
 * the actions on it, only when the model has it. A model not in [BY_TYPE] (one the server adds
 * later) is not narrowed: its pills are whatever it reports with a value, as for any device.
 *
 * Sources, all in petkit-local (`addon/petkit_local`):
 * - **N60 spray**: `events/codes.py` `LITTER_N60 = {"t5", "t6", "t7"}` (the Purobot Max Pro 2,
 *   Ultra and Crystal Duo) have it built in. A box without one can be paired with a Pura Air
 *   (K3), whose cartridge is also an N60 (`k3LightSwitch` in a T4 report; the parent carries
 *   `withK3`/`k3Id`, `devices/payloads.py`): the N60, and its reset, then come from the Pura Air
 *   ([DevicePart.PuraAirSpray]). `/app/v1` lists it in the box's `accessories`; the first purifier
 *   there holds the box's one K3 slot ([Device.puraAirId]).
 * - **N50 pack**: a passive pack in the waste bin (`devices/state_tables.py`), on every litter box
 *   but the Purobot Ultra, which `ha/categories.py` says has no N50 cartridge (its `reset_n50` is
 *   excluded there, and the code it sent is Pack).
 * - **Waste bags**: the Purobot Ultra only, the one box with a bagging mechanism
 *   (`LITTER_PACKAGING_SENSORS`, a T6-only row of `model_entities`).
 * - **Litter level** (`sandPercent`) and **waste bin** (`boxFull`, `boxState`): every litter box
 *   (`LITTER_SENSORS`, `LITTER_BINARY_SENSORS`).
 */
object LitterParts {
    private val BASE = setOf(DevicePart.Litter, DevicePart.WasteBin)

    val BY_TYPE: Map<String, Set<DevicePart>> = mapOf(
        "t3" to BASE + DevicePart.Deodorizer,
        "t4" to BASE + DevicePart.Deodorizer,
        "t5" to BASE + DevicePart.Deodorizer + DevicePart.Spray,
        "t6" to BASE + DevicePart.Spray + DevicePart.Bags,
        "t7" to BASE + DevicePart.Deodorizer + DevicePart.Spray,
    )

    /** Buttons that act on one part only: offered only on a model with that part. */
    private val PART_ACTIONS: Map<String, DevicePart> = mapOf(
        "reset_n50" to DevicePart.Deodorizer,
        "reset_n60" to DevicePart.Spray,
        "pack_waste" to DevicePart.Bags,
        "reset_bagbox" to DevicePart.Bags,
    )

    /** The parts of [type], or null for a model this table does not know. */
    fun of(type: String): Set<DevicePart>? = BY_TYPE[type.lowercase()]

    /**
     * Whether [device] has [part], given the Pura Air paired with it, if any. A model the table
     * does not know is not narrowed; a Pura Air counts only on a box without a sprayer of its own.
     */
    fun has(device: Device, part: DevicePart, puraAir: Device? = null): Boolean = when (part) {
        DevicePart.Other -> true
        DevicePart.PuraAirSpray -> puraAir != null && of(device.type)?.contains(DevicePart.Spray) == false
        else -> of(device.type)?.contains(part) ?: true
    }

    /** Whether [device] has an N60 at all: built in, or a paired Pura Air's. */
    fun hasN60(device: Device, puraAir: Device? = null): Boolean =
        has(device, DevicePart.Spray) || has(device, DevicePart.PuraAirSpray, puraAir)

    /** [device]'s actions without those on a part it does not have, in the server's order. */
    fun actions(device: Device, puraAir: Device? = null): List<DeviceAction> = device.actions.filter { action ->
        when (val part = PART_ACTIONS[action.key]) {
            null -> true
            DevicePart.Spray -> hasN60(device, puraAir)
            else -> has(device, part)
        }
    }
}

package org.petkitlocal.core.domain.devicepage

import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily

/**
 * A device's consumables as its settings list them (issue #59): the parts its page draws as
 * hotspots, so only those the model has ([LitterParts], [FountainParts]) and only with a value,
 * each with the device's resets of that part only. The page's other actions on a part (level the
 * litter, empty the bin, deodorize...) stay on the page: the settings are where a counter is put
 * back after a part was changed.
 */
object ConsumableRules {
    /**
     * Resets of a part's counter, by petkit-local's keys (`ha/entities/buttons.py`, and
     * `ha/entities/ble.py` for a Bluetooth fountain's filter).
     */
    private val RESET_KEYS = setOf(
        "reset_n50",
        "reset_n60",
        "reset_bagbox",
        "reset_desiccant",
        "reset_filter",
        "w5_reset_filter",
        "ctw3_reset_filter",
    )

    /** A pump runs or rests: nothing in it runs out, so it is no consumable. */
    private val NOT_CONSUMED = setOf(DevicePart.Pump)

    /**
     * [device]'s consumables, in its page's order. [puraAir] is the Pura Air paired with a litter
     * box, whose N60 is listed as the box's, as on the page. A purifier, or a family the app has
     * no page for, has no hotspots and so no consumables here.
     */
    fun of(device: Device, puraAir: Device? = null): List<Hotspot> = when (device.family) {
        DeviceFamily.LitterBox -> HotspotRules.litterBox(device, puraAir)
        DeviceFamily.Feeder -> HotspotRules.feeder(device)
        DeviceFamily.Fountain -> HotspotRules.fountain(device)
        DeviceFamily.Purifier, DeviceFamily.Other -> emptyList()
    }
        .filter { it.part !in NOT_CONSUMED }
        .map { hotspot -> hotspot.copy(actions = hotspot.actions.filter { it.key in RESET_KEYS }) }
}

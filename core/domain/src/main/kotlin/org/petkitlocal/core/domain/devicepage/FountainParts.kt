package org.petkitlocal.core.domain.devicepage

import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction

/**
 * Which parts each fountain model physically has, by codename, and whether it records drinks.
 * The fountain page shows a part, and offers the actions on it, only when the model has it. A
 * model not in [BY_TYPE] (one the server adds later) is not narrowed: its pills are whatever it
 * reports with a value, as for any device.
 *
 * Sources, all in petkit-local (`addon/petkit_local`):
 * - **W5, W4, CTW2** (EverSweet 3 Pro, EverSweet / W4X / W4X UVC, Solo 2): one Bluetooth protocol
 *   and one entity list serve the three (`devices/ble/__init__.py` `W5_PROTOCOL`,
 *   `ha/entities/ble.py` `W5_ENTITIES`): the water flag (`w5_water_missing`, `warningWaterMissing`),
 *   the filter (`w5_filter`, `filterPercentage`, and `w5_filter_warn`), the pump (`w5_running`,
 *   `runningStatus`). No battery. **No UV lamp**: a W4X UVC advertises as `Petkit_W4XUVC` but is
 *   type `w4`, and its status block has no UV field (`devices/ble/w5.py`
 *   `_W5_STATUS_STATE_OFFSETS`), so nothing could fill a UV pill.
 * - **CTW3** (EverSweet Max Cordless): its own layout (`devices/ble/ctw3.py`), the same parts
 *   plus a battery (`ha/entities/ble.py` `CTW3_ENTITIES`: `ctw3_battery`, `ctw3_low_battery`).
 * - **W7H** (EverSweet Ultra AI): "no filter and no battery in this hardware", and no water flag
 *   (`ha/categories.py`, the fountain's `model_excludes`). It has a drinking tray (`tray_full`, the
 *   firmware's `pk_hmi_get_water_tary_full_sta`, and `tray_installed`), a clean water tank and a
 *   waste tank (`clean_tank_installed`, `waste_tank_installed`) and a circulation pump
 *   (`pump_running`, the "loop pump"), all in `ha/entities/sensors.py` `FOUNTAIN_W7H_*`.
 * - **Drinks**: only the W7H reports them (`events/codes.py`, `drink_over` and HTTP `5`/`6` are
 *   `FOUNTAIN_NEXT_GEN = {"w7h"}`); a Bluetooth fountain's frames carry its state, never an event.
 */
object FountainParts {
    private val W5_FAMILY = setOf(DevicePart.Water, DevicePart.Filter, DevicePart.Pump)

    val BY_TYPE: Map<String, Set<DevicePart>> = mapOf(
        "w5" to W5_FAMILY,
        "w4" to W5_FAMILY,
        "ctw2" to W5_FAMILY,
        "ctw3" to W5_FAMILY + DevicePart.Battery,
        "w7h" to setOf(DevicePart.Tray, DevicePart.CleanTank, DevicePart.WasteTank, DevicePart.Pump),
    )

    /** The models that record each drink (with the pet, on the W7H's camera). */
    private val RECORDS_DRINKS = setOf("w7h")

    /** Buttons that act on one part only: offered only on a model with that part. */
    private val PART_ACTIONS: Map<String, DevicePart> = mapOf(
        "reset_filter" to DevicePart.Filter,
        "w5_reset_filter" to DevicePart.Filter,
        "ctw3_reset_filter" to DevicePart.Filter,
    )

    /** The parts of [type], or null for a model this table does not know. */
    fun of(type: String): Set<DevicePart>? = BY_TYPE[type.lowercase()]

    /** Whether [device] has [part]; a model the table does not know is not narrowed. */
    fun has(device: Device, part: DevicePart): Boolean =
        part == DevicePart.Other || of(device.type)?.contains(part) ?: true

    /**
     * Whether [device] records drinks, so that "no drink today" means none happened. A model the
     * table does not know is taken to, like a W7H: its cards say whether it does.
     */
    fun recordsDrinks(device: Device): Boolean = of(device.type) == null || device.type.lowercase() in RECORDS_DRINKS

    /** [device]'s actions without those on a part it does not have, in the server's order. */
    fun actions(device: Device): List<DeviceAction> =
        device.actions.filter { action -> PART_ACTIONS[action.key]?.let { has(device, it) } ?: true }
}

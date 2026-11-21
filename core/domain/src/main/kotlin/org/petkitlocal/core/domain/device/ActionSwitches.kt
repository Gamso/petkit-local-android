package org.petkitlocal.core.domain.device

import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.asBoolean

/**
 * The actions a device offers its user: the server's buttons and, among them, the settable
 * switches a page offers as actions (issue #100). One list for every place that shows them, the
 * action sheet, the quick actions editor and the settings' summary of the row, so a switch is an
 * action like any other there: listed, pinned, unpinned and moved, and its default place applies
 * only while the user has not chosen the row.
 *
 * Today one switch: the T4's Pura Air light, `k3_light` (petkit-local#133/#136), which the server
 * lists among the box's entities only while a K3 is linked, never among its `actions`. It goes
 * right after Deodorize. Its state is the box's own report (`k3LightSwitch`), so it follows the
 * K3 turning its light off by itself; unknown reads as off. With the K3 unlinked it is no longer
 * offered, and a saved choice naming it keeps its slot for the app to fill, as for any action the
 * server stops listing ([DeviceActionOrder.layout]).
 */
object ActionSwitches {
    /** The T4's switch for its linked Pura Air's light. */
    const val PURA_AIR_LIGHT = "k3_light"
    private const val DEODORIZE = "deodorize"

    /** [actions] (the device's own, by default) with its switches in their place. */
    fun of(device: Device, actions: List<DeviceAction> = device.actions): List<DeviceAction> {
        val entity = device.entity(PURA_AIR_LIGHT)
            ?.takeIf { it.component == EntityComponent.Switch && it.settable } ?: return actions
        val light = DeviceAction(
            entity.key,
            entity.name,
            destructive = false,
            switchedOn = entity.value.asBoolean == true,
        )
        val after = actions.indexOfFirst { it.key == DEODORIZE }
        return if (after < 0) actions + light else actions.take(after + 1) + light + actions.drop(after + 1)
    }
}

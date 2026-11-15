package org.petkitlocal.core.domain.repository

import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.EntityValue

/** How petkit-local passed a command on: `Delivered` in the API. */
enum class CommandDelivery {
    /** Published to the device now. */
    Mqtt,

    /** Taken by the device on its next HTTP poll, within about 10 s (SPEC.md §6). */
    Queued,

    /** Only petkit-local's own state changed. */
    Local,
}

/**
 * A device's buttons and settings. No optimistic UI (ADR 0002): a write updates the cache only
 * with what the server answered, and the device's next report confirms or overwrites it.
 */
interface DeviceCommandRepository {
    /** Presses action [key] from `Device.actions`; a destructive one needs [confirm]. */
    suspend fun runAction(deviceId: Long, key: String, confirm: Boolean = false): Outcome<CommandDelivery>

    /**
     * Writes the settable entity [key]: [EntityValue.Bool] for a switch, [EntityValue.Number] for
     * a number, [EntityValue.Text] for a select option, a time (`HH:MM`) or a text.
     */
    suspend fun setEntity(deviceId: Long, key: String, value: EntityValue): Outcome<CommandDelivery>
}

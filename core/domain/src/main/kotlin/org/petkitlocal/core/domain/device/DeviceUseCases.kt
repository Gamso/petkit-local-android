package org.petkitlocal.core.domain.device

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.DeviceCommandRepository
import org.petkitlocal.core.domain.repository.DeviceRepository

/** One device from the cache, null when it is not (or no longer) known. */
class ObserveDeviceUseCase @Inject constructor(private val devices: DeviceRepository) {
    operator fun invoke(id: Long): Flow<Device?> = devices.observeDevice(id)
}

/** Pull to refresh on a device screen: every device and its detail, as Home does. */
class RefreshDevicesUseCase @Inject constructor(private val devices: DeviceRepository) {
    suspend operator fun invoke(): Outcome<Unit> = devices.refresh()
}

/**
 * Presses one of a device's buttons. A destructive one cannot be undone, so it is sent only once
 * the user [confirmed] it, and then with `confirm: true`; unconfirmed, it is refused here with
 * the server's own code, [CONFIRMATION_REQUIRED], before any request.
 */
class RunDeviceActionUseCase @Inject constructor(private val commands: DeviceCommandRepository) {
    suspend operator fun invoke(deviceId: Long, action: DeviceAction, confirmed: Boolean): Outcome<CommandDelivery> {
        if (action.destructive && !confirmed) {
            return Outcome.Failure(DomainError.Validation(field = null, code = CONFIRMATION_REQUIRED, message = ""))
        }
        return commands.runAction(deviceId, action.key, confirm = action.destructive)
    }

    companion object {
        const val CONFIRMATION_REQUIRED = "confirmation_required"
    }
}

/**
 * Writes a setting. A value [EntityWrites] already knows the server would refuse is refused here,
 * with the problem as the error's code, so the editor can say what is wrong where it is wrong.
 */
class SetEntityValueUseCase @Inject constructor(private val commands: DeviceCommandRepository) {
    suspend operator fun invoke(deviceId: Long, entity: Entity, value: EntityValue): Outcome<CommandDelivery> {
        EntityWrites.problem(entity, value)?.let { problem ->
            return Outcome.Failure(DomainError.Validation(field = entity.key, code = problem.code, message = ""))
        }
        return commands.setEntity(deviceId, entity.key, value)
    }
}

package org.petkitlocal.core.testing

import kotlinx.coroutines.CompletableDeferred
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.DeviceCommandRepository

/**
 * A [DeviceCommandRepository] that records what it was asked and answers [result]; a test that
 * looks at a command in flight sets [gate] and completes it when it is ready.
 */
class FakeDeviceCommandRepository : DeviceCommandRepository {
    var result: Outcome<CommandDelivery> = Outcome.Success(CommandDelivery.Mqtt)
    var gate: CompletableDeferred<Unit>? = null

    val actions = mutableListOf<Action>()
    val writes = mutableListOf<Write>()

    data class Action(val deviceId: Long, val key: String, val confirm: Boolean)

    data class Write(val deviceId: Long, val key: String, val value: EntityValue)

    override suspend fun runAction(deviceId: Long, key: String, confirm: Boolean): Outcome<CommandDelivery> {
        actions += Action(deviceId, key, confirm)
        gate?.await()
        return result
    }

    override suspend fun setEntity(deviceId: Long, key: String, value: EntityValue): Outcome<CommandDelivery> {
        writes += Write(deviceId, key, value)
        gate?.await()
        return result
    }
}

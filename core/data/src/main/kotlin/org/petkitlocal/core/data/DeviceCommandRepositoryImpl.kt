package org.petkitlocal.core.data

import javax.inject.Inject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.petkitlocal.core.database.dao.DeviceDao
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.DeviceCommandRepository
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.dto.ActionRequestDto
import org.petkitlocal.core.network.dto.Delivered
import org.petkitlocal.core.network.dto.EntityWriteRequestDto
import org.petkitlocal.core.network.error.apiCall

/**
 * `POST /devices/{id}/actions/{action}` and `PUT /devices/{id}/entities/{key}`, server first
 * (ADR 0002). An action changes nothing in the cache: what it did arrives with the device's next
 * report, over `/ws`. A write stores the value the server answered it now holds, which the
 * device's next report then confirms or overwrites; a refused write changes nothing.
 */
class DeviceCommandRepositoryImpl @Inject constructor(private val api: PetkitApi, private val dao: DeviceDao) :
    DeviceCommandRepository {
    override suspend fun runAction(deviceId: Long, key: String, confirm: Boolean): Outcome<CommandDelivery> {
        // A non-destructive action takes no body; `{}` is the same to the server.
        val body = ActionRequestDto(confirm = if (confirm) true else null)
        return when (val result = apiCall { api.runDeviceAction(deviceId, key, body) }) {
            is Outcome.Failure -> result
            is Outcome.Success -> Outcome.Success(commandDelivery(result.value.delivered))
        }
    }

    override suspend fun setEntity(deviceId: Long, key: String, value: EntityValue): Outcome<CommandDelivery> {
        val json = value.toJson() ?: return Outcome.Failure(
            DomainError.Validation(field = key, code = NOT_WRITABLE, message = "This value cannot be written"),
        )
        val answer = when (val result = apiCall { api.setDeviceEntity(deviceId, key, EntityWriteRequestDto(json)) }) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val entity = answer.entity
        dao.patchEntityValue(
            deviceId = deviceId,
            key = entity.key,
            valueJson = PetkitJson.encodeToString(JsonElement.serializer(), entity.value),
            display = entity.display,
        )
        return Outcome.Success(commandDelivery(answer.delivered))
    }

    private companion object {
        /** Not a server code: refused here, before any request. */
        const val NOT_WRITABLE = "not_writable"
    }
}

/**
 * The API's `delivered`. A value this app does not know yet reads as [CommandDelivery.Queued],
 * the one that promises least: "the device takes it on its next poll".
 */
internal fun commandDelivery(delivered: String): CommandDelivery = when (delivered) {
    Delivered.MQTT -> CommandDelivery.Mqtt
    Delivered.LOCAL -> CommandDelivery.Local
    else -> CommandDelivery.Queued
}

/**
 * The JSON a write sends, typed by the entity's component (api/openapi.yaml, `setDeviceEntity`):
 * a whole number goes out as an integer, as a number entity's value comes in. Null for a value
 * no write takes (an object or array).
 */
internal fun EntityValue.toJson(): JsonElement? = when (this) {
    EntityValue.None -> JsonNull

    is EntityValue.Bool -> JsonPrimitive(value)

    is EntityValue.Number -> if (value % 1.0 == 0.0 && value in LONG_RANGE) {
        JsonPrimitive(value.toLong())
    } else {
        JsonPrimitive(value)
    }

    is EntityValue.Text -> JsonPrimitive(value)

    is EntityValue.Other -> null
}

private val LONG_RANGE = Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()

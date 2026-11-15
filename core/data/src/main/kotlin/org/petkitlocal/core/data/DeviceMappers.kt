package org.petkitlocal.core.data

import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import org.petkitlocal.core.database.dao.DeviceDetailRows
import org.petkitlocal.core.database.model.ActionRow
import org.petkitlocal.core.database.model.DeviceRow
import org.petkitlocal.core.database.model.DeviceWithDetails
import org.petkitlocal.core.database.model.EntityRow
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.dto.DeviceDetailDto
import org.petkitlocal.core.network.dto.DeviceKind
import org.petkitlocal.core.network.dto.DeviceSummaryDto
import org.petkitlocal.core.network.dto.DeviceTransport as ApiDeviceTransport
import org.petkitlocal.core.network.dto.EntityCategory as ApiEntityCategory
import org.petkitlocal.core.network.dto.EntityComponent as ApiEntityComponent

/*
 * DTO -> row keeps the server's values as they are (strings, JSON text); row -> domain maps them
 * to domain types, with a fallback for any value this app does not know yet.
 */

private val stringList = ListSerializer(String.serializer())

internal fun DeviceSummaryDto.toRow(position: Int, serial: String?) = DeviceRow(
    id = id,
    position = position,
    type = type,
    model = model,
    kind = kind,
    online = online,
    transport = transport,
    lastSeen = lastSeen,
    firmware = firmware,
    isCamera = isCamera,
    serial = serial,
)

internal fun DeviceDetailDto.toDetailRows() = DeviceDetailRows(
    entities = entities.mapIndexed { index, entity ->
        EntityRow(
            deviceId = id,
            key = entity.key,
            position = index,
            name = entity.name,
            component = entity.component,
            valueJson = PetkitJson.encodeToString(JsonElement.serializer(), entity.value),
            display = entity.display,
            unit = entity.unit,
            deviceClass = entity.deviceClass,
            category = entity.category,
            optionsJson = entity.options?.let { PetkitJson.encodeToString(stringList, it) },
            settable = entity.settable,
            min = entity.min,
            max = entity.max,
            step = entity.step,
        )
    },
    actions = actions.mapIndexed { index, action ->
        ActionRow(
            deviceId = id,
            key = action.key,
            position = index,
            name = action.name,
            destructive = action.destructive,
        )
    },
)

internal fun DeviceWithDetails.toDomain() = Device(
    id = device.id,
    type = device.type,
    model = device.model,
    family = deviceFamily(device.kind),
    online = device.online,
    transport = when (device.transport) {
        ApiDeviceTransport.MQTT -> DeviceTransport.Mqtt
        ApiDeviceTransport.HTTP -> DeviceTransport.Http
        else -> DeviceTransport.Unknown
    },
    lastSeen = device.lastSeen?.let(::epochSecondsToInstant),
    firmware = device.firmware,
    isCamera = device.isCamera,
    serial = device.serial,
    entities = entities.sortedBy { it.position }.map { it.toDomain() },
    actions = actions.sortedBy { it.position }.map { DeviceAction(it.key, it.name, it.destructive) },
)

internal fun deviceFamily(kind: String): DeviceFamily = when (kind) {
    DeviceKind.LITTER_BOX -> DeviceFamily.LitterBox
    DeviceKind.FEEDER -> DeviceFamily.Feeder
    DeviceKind.FOUNTAIN -> DeviceFamily.Fountain
    DeviceKind.PURIFIER -> DeviceFamily.Purifier
    else -> DeviceFamily.Other
}

internal fun EntityRow.toDomain() = Entity(
    key = key,
    name = name,
    component = when (component) {
        ApiEntityComponent.SENSOR -> EntityComponent.Sensor
        ApiEntityComponent.BINARY_SENSOR -> EntityComponent.BinarySensor
        ApiEntityComponent.SWITCH -> EntityComponent.Switch
        ApiEntityComponent.NUMBER -> EntityComponent.Number
        ApiEntityComponent.SELECT -> EntityComponent.Select
        ApiEntityComponent.TEXT -> EntityComponent.Text
        ApiEntityComponent.TIME -> EntityComponent.Time
        else -> EntityComponent.Unknown
    },
    value = entityValue(valueJson),
    display = display,
    unit = unit,
    deviceClass = deviceClass,
    category = when (category) {
        ApiEntityCategory.CONFIG -> EntityCategory.Config

        ApiEntityCategory.DIAGNOSTIC -> EntityCategory.Diagnostic

        // An unknown category is not a primary value either: keep it off the headline and alerts.
        null -> null

        else -> EntityCategory.Diagnostic
    },
    options = optionsJson?.let { decodeStrings(it) }.orEmpty(),
    settable = settable,
    min = min,
    max = max,
    step = step,
)

/** An entity's stored JSON value, typed. Text that is not JSON (it never should be) is kept as text. */
internal fun entityValue(json: String): EntityValue {
    val element = try {
        PetkitJson.parseToJsonElement(json)
    } catch (_: IllegalArgumentException) {
        return EntityValue.Text(json)
    }
    return when {
        element is JsonNull -> EntityValue.None

        element !is JsonPrimitive -> EntityValue.Other(json)

        element.isString -> EntityValue.Text(element.content)

        else -> element.booleanOrNull?.let { EntityValue.Bool(it) }
            ?: element.doubleOrNull?.let { EntityValue.Number(it) }
            ?: EntityValue.Text(element.content)
    }
}

internal fun decodeStrings(json: String): List<String> = try {
    PetkitJson.decodeFromString(stringList, json)
} catch (_: IllegalArgumentException) {
    emptyList()
}

internal fun encodeStrings(values: List<String>): String = PetkitJson.encodeToString(stringList, values)

private const val MILLIS_PER_SECOND = 1000.0

internal fun epochSecondsToInstant(seconds: Double): Instant =
    Instant.ofEpochMilli((seconds * MILLIS_PER_SECOND).toLong())

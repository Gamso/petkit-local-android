package org.petkitlocal.core.domain.devicepage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem

/** Builders for the device page tests: entities with the keys petkit-local really uses. */
object PageFixtures {
    val paris: ZoneId = ZoneId.of("Europe/Paris")
    val today: LocalDate = LocalDate.of(2026, 11, 15)

    fun at(hour: Int, minute: Int, day: LocalDate = today): Instant =
        ZonedDateTime.of(day.atTime(hour, minute), paris).toInstant()

    fun device(
        id: Long,
        family: DeviceFamily,
        vararg entities: Entity,
        actions: List<DeviceAction> = emptyList(),
        online: Boolean = true,
        type: String = "t",
    ) = Device(
        id = id,
        type = type,
        modelName = "Model $id",
        family = family,
        online = online,
        transport = DeviceTransport.Http,
        lastSeen = null,
        firmware = null,
        isCamera = false,
        entities = entities.toList(),
        actions = actions,
    )

    fun sensor(key: String, value: Double?, unit: String? = null, display: String? = null) = Entity(
        key = key,
        name = key,
        component = EntityComponent.Sensor,
        value = value?.let { EntityValue.Number(it) } ?: EntityValue.None,
        display = display,
        unit = unit,
    )

    fun binary(key: String, on: Boolean) = Entity(key, key, EntityComponent.BinarySensor, EntityValue.Bool(on))

    fun switch(key: String, on: Boolean) =
        Entity(key, key, EntityComponent.Switch, EntityValue.Bool(on), settable = true)

    fun action(key: String, destructive: Boolean = false) = DeviceAction(key, key, destructive)

    fun pet(id: Long) = Pet(id, "Pet $id", emptyList(), null, null, weightLearned = false, photoUrl = null)

    fun item(
        id: Long,
        deviceId: Long,
        at: Instant,
        vararg categories: TimelineCategory,
        pet: Long? = null,
        duration: Int? = null,
    ) = TimelineItem(
        id = id,
        kind = "visit",
        categories = categories.toSet(),
        deviceId = deviceId,
        deviceType = null,
        ts = at,
        displayTs = at,
        eventType = null,
        eventKind = null,
        label = null,
        pet = pet?.let { PetRef(it, "Pet $it") },
        durationSec = duration,
        weightG = null,
        facts = emptyList(),
    )
}

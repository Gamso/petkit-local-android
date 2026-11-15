package org.petkitlocal.core.domain.home

import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.model.AccessoryRef
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityCategory
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem

class HomeRulesTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `a litter box counts the day's toilet visits, not a pet merely seen`() {
        val box = device(1, DeviceFamily.LitterBox)
        val items = listOf(
            item(1, deviceId = 1, TimelineCategory.Toileting),
            item(2, deviceId = 1, TimelineCategory.Toileting, TimelineCategory.HealthAlert),
            item(3, deviceId = 1, TimelineCategory.Pet),
            item(4, deviceId = 1, TimelineCategory.Cleaning),
        )

        assertEquals(Headline.Visits(2), HomeRules.headline(box, items))
    }

    @Test
    fun `a feeder shows its daily total in the server's unit and counts its meals`() {
        val feeder = device(
            2,
            DeviceFamily.Feeder,
            sensor("total_dispensed", EntityValue.Number(20.0), unit = "g"),
        )
        val items = listOf(item(1, 2, TimelineCategory.Feeding), item(2, 2, TimelineCategory.Feeding))

        assertEquals(Headline.Served(Quantity(20.0, "g"), meals = 2), HomeRules.headline(feeder, items))
        assertEquals(Headline.Served(null, meals = 0), HomeRules.headline(device(3, DeviceFamily.Feeder), emptyList()))
    }

    @Test
    fun `a fountain shows its water flag when it has one, else the day's drinks`() {
        val w5 = device(3, DeviceFamily.Fountain, binary("w5_water_missing", true, "problem"))
        val ctw3 = device(4, DeviceFamily.Fountain, binary("ctw3_water_lack", false, "problem"))
        val w7h = device(5, DeviceFamily.Fountain)

        assertEquals(Headline.Water(low = true), HomeRules.headline(w5, emptyList()))
        assertEquals(Headline.Water(low = false), HomeRules.headline(ctw3, emptyList()))
        assertEquals(
            Headline.Drinks(1),
            HomeRules.headline(w7h, listOf(item(1, 5, TimelineCategory.Drinking), item(2, 5, TimelineCategory.Pet))),
        )
    }

    @Test
    fun `any other device shows its first primary percentage, or nothing`() {
        val k3 = device(
            6,
            DeviceFamily.Purifier,
            sensor("k3_rssi", EntityValue.Number(-50.0), unit = "dBm"),
            sensor("k3_liquid", EntityValue.Number(62.6), unit = "%", name = "K3 Liquid"),
            sensor("k3_battery", EntityValue.Number(88.0), unit = "%"),
        )

        assertEquals(Headline.Level("k3_liquid", "K3 Liquid", 63), HomeRules.headline(k3, emptyList()))
        assertEquals(Headline.None, HomeRules.headline(device(7, DeviceFamily.Other), emptyList()))
    }

    @Test
    fun `a Pura Air with its litter box listed has no card, a Bluetooth fountain names its relay`() {
        val box = device(1, DeviceFamily.LitterBox).copy(
            accessories = listOf(
                AccessoryRef(41, "k3", DeviceFamily.Purifier),
                AccessoryRef(40, "k3", DeviceFamily.Purifier),
            ),
        )
        val feeder = device(
            2,
            DeviceFamily.Feeder,
        ).copy(accessories = listOf(AccessoryRef(30, "w5", DeviceFamily.Fountain)))
        val fountain = device(30, DeviceFamily.Fountain).copy(connection = DeviceConnection.Ble, parentId = 2)
        val low = device(40, DeviceFamily.Purifier, sensor("k3_liquid", EntityValue.Number(9.0), unit = "%"))
            .copy(connection = DeviceConnection.Ble, parentId = 1)
        val full = device(41, DeviceFamily.Purifier, sensor("k3_liquid", EntityValue.Number(80.0), unit = "%"))
            .copy(connection = DeviceConnection.Ble, parentId = 1)
        // Linked to no device the app knows: it has nowhere else to be shown.
        val orphan = device(42, DeviceFamily.Purifier).copy(connection = DeviceConnection.Ble, parentId = 99)

        val overview = HomeRules.overview(
            listOf(box, feeder, fountain, low, full, orphan),
            emptyList(),
            emptyList(),
            paris,
        )

        // The two Pura Airs of the box are on its page, not on Home; the orphan keeps a card.
        assertEquals(listOf(1L, 2L, 30L, 42L), overview.devices.map { it.device.id })
        assertEquals(null, overview.devices[0].relay)
        assertEquals("Model 2", overview.devices[2].relay)
        assertEquals(null, overview.devices[3].relay)
    }

    @Test
    fun `the status band reads offline, then error, then the work mode, then idle`() {
        val idle = sensor("device_status", EntityValue.Number(-1.0), display = "idle")
        val cleaning = sensor("device_status", EntityValue.Number(0.0), display = "cleaning")
        val error = sensor("error", EntityValue.Text("bin full"))

        assertEquals(DeviceStatus.Offline, HomeRules.status(device(1, DeviceFamily.LitterBox, error, online = false)))
        assertEquals(
            DeviceStatus.Error("bin full"),
            HomeRules.status(device(1, DeviceFamily.LitterBox, error, cleaning)),
        )
        assertEquals(
            DeviceStatus.Busy(BusyActivity.Working("cleaning")),
            HomeRules.status(device(1, DeviceFamily.LitterBox, cleaning)),
        )
        assertEquals(DeviceStatus.Idle, HomeRules.status(device(1, DeviceFamily.LitterBox, idle)))
        assertEquals(
            DeviceStatus.Busy(BusyActivity.PetInside),
            HomeRules.status(device(1, DeviceFamily.LitterBox, idle, binary("pet_occupied", true, "occupancy"))),
        )
        assertEquals(
            DeviceStatus.Busy(BusyActivity.Feeding),
            HomeRules.status(device(2, DeviceFamily.Feeder, binary("feeding", true, "running"))),
        )
        // A work mode with no label (a D4S reports -1 with none) is not a mode to show.
        assertEquals(
            DeviceStatus.Idle,
            HomeRules.status(device(2, DeviceFamily.Feeder, sensor("device_status", EntityValue.Number(3.0)))),
        )
        val blankError = sensor("error", EntityValue.Text(" "))
        assertEquals(DeviceStatus.Idle, HomeRules.status(device(1, DeviceFamily.LitterBox, blankError)))
    }

    @Test
    fun `alerts are ordered by urgency and an offline device only says it is offline`() {
        val devices = listOf(
            device(
                1,
                DeviceFamily.LitterBox,
                binary("waste_bin", true, "problem", name = "Waste Bin Full"),
                sensor("litter_percent", EntityValue.Number(12.0), unit = "%", name = "Litter Level"),
                sensor("n50_durability", EntityValue.Number(3.0), unit = "days", name = "N50 Days Left"),
                sensor("n60_spray_days", EntityValue.Number(4.0), unit = "days"),
            ),
            device(
                2,
                DeviceFamily.Feeder,
                sensor("hopper2_level", EntityValue.Number(0.0), display = "Empty", name = "Hopper 2"),
                sensor("food_bowl_pct", EntityValue.Number(5.0), unit = "%"),
                sensor("error", EntityValue.Text("motor stuck")),
            ),
            device(3, DeviceFamily.Fountain, binary("w5_water_missing", true, "problem"), online = false),
            device(4, DeviceFamily.Fountain, binary("ctw3_low_battery", true, "battery", name = "Low Battery")),
            device(
                5,
                DeviceFamily.Feeder,
                // Settings and diagnostics never raise a banner.
                sensor("desiccant_days", EntityValue.Number(1.0), unit = "days", category = EntityCategory.Diagnostic),
            ),
        )

        val alerts = HomeAlerts.alerts(devices)

        assertEquals(
            listOf(
                DeviceAlert(2, "Model 2", AlertKind.Error("motor stuck")),
                DeviceAlert(3, "Model 3", AlertKind.Offline),
                DeviceAlert(1, "Model 1", AlertKind.Problem("waste_bin", "Waste Bin Full")),
                DeviceAlert(4, "Model 4", AlertKind.Problem("ctw3_low_battery", "Low Battery")),
                DeviceAlert(2, "Model 2", AlertKind.Empty("hopper2_level", "Hopper 2")),
                DeviceAlert(1, "Model 1", AlertKind.RunningOut("n50_durability", "N50 Days Left", 3)),
                DeviceAlert(1, "Model 1", AlertKind.Low("litter_percent", "Litter Level", 12)),
            ),
            alerts,
        )
    }

    @Test
    fun `day events sit at their local minute, oldest first, and pets get their visits`() {
        // In Paris: 06:19:19, 08:30:15 and 00:46:40 on 2026-11-15.
        val visit = item(1, 1, TimelineCategory.Toileting, at = 1_794_719_959, pet = PetRef(1, "Mochi"))
        val drink = item(2, 1, TimelineCategory.Drinking, at = 1_794_727_815, pet = PetRef(2, "Tigrou"))
        val fault = item(3, 1, TimelineCategory.Fault, at = 1_794_700_000)
        val undated = item(4, 1, TimelineCategory.Cleaning, at = null)

        val events = HomeRules.dayEvents(listOf(drink, visit, fault, undated), paris)

        assertEquals(listOf(DayEventType.Alert, DayEventType.Visit, DayEventType.Drink), events.map { it.type })
        assertEquals(listOf(46, 6 * 60 + 19, 8 * 60 + 30), events.map { it.minuteOfDay })

        val overview = HomeRules.overview(
            devices = emptyList(),
            pets = listOf(pet(1), pet(2)),
            dayItems = listOf(visit, drink),
            zone = paris,
        )
        assertEquals(listOf(1, 0), overview.pets.map { it.visitsToday })
        assertEquals(listOf(0, 1), overview.pets.map { it.colorIndex })
    }

    private fun device(id: Long, family: DeviceFamily, vararg entities: Entity, online: Boolean = true) = Device(
        id = id,
        type = "x$id",
        model = "Model $id",
        family = family,
        online = online,
        transport = DeviceTransport.Http,
        lastSeen = null,
        firmware = null,
        isCamera = false,
        entities = entities.toList(),
    )

    private fun sensor(
        key: String,
        value: EntityValue,
        unit: String? = null,
        display: String? = null,
        name: String = key,
        category: EntityCategory? = null,
    ) = Entity(key, name, EntityComponent.Sensor, value, display = display, unit = unit, category = category)

    private fun binary(key: String, on: Boolean, deviceClass: String, name: String = key) =
        Entity(key, name, EntityComponent.BinarySensor, EntityValue.Bool(on), deviceClass = deviceClass)

    private fun item(
        id: Long,
        deviceId: Long,
        vararg categories: TimelineCategory,
        at: Long? = 1_794_719_959,
        pet: PetRef? = null,
    ) = TimelineItem(
        id = id,
        kind = "event",
        categories = categories.toSet(),
        deviceId = deviceId,
        deviceType = null,
        ts = at?.let { Instant.ofEpochSecond(it + 60) },
        displayTs = at?.let(Instant::ofEpochSecond),
        eventType = null,
        eventKind = null,
        label = null,
        pet = pet,
        durationSec = null,
        weightG = null,
        facts = emptyList(),
    )

    private fun pet(id: Long) = Pet(id, "Pet $id", emptyList(), null, 4000.0, false, null)
}

package org.petkitlocal.core.domain.litter

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DayOnlyTimeline
import org.petkitlocal.core.domain.ListOnlyPets
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ActionSwitches
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.PageFixtures.action
import org.petkitlocal.core.domain.devicepage.PageFixtures.at
import org.petkitlocal.core.domain.devicepage.PageFixtures.device
import org.petkitlocal.core.domain.devicepage.PageFixtures.item
import org.petkitlocal.core.domain.devicepage.PageFixtures.paris
import org.petkitlocal.core.domain.devicepage.PageFixtures.pet
import org.petkitlocal.core.domain.devicepage.PageFixtures.sensor
import org.petkitlocal.core.domain.devicepage.PageFixtures.switch
import org.petkitlocal.core.domain.devicepage.PageFixtures.today
import org.petkitlocal.core.domain.devicepage.PartReading
import org.petkitlocal.core.domain.devicepage.RefreshDevicePageUseCase
import org.petkitlocal.core.domain.home.BusyActivity
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.model.AccessoryRef
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.DeviceRepository

class LitterRulesTest {
    private val yesterday = today.minusDays(1)
    private val box = device(
        1002,
        DeviceFamily.LitterBox,
        sensor("device_status", 0.0, display = "cleaning"),
        switch("auto_spray", false),
        switch("auto_work", true),
        switch("child_lock", true),
    )

    private val todayItems = listOf(
        item(5, 1002, at(9, 0), TimelineCategory.Toileting, pet = 2, duration = 120),
        item(4, 1002, at(7, 30), TimelineCategory.Cleaning),
        item(3, 9999, at(7, 0), TimelineCategory.Toileting, pet = 1, duration = 30),
        item(2, 1002, at(6, 19), TimelineCategory.Toileting, TimelineCategory.HealthAlert, pet = 1, duration = 60),
        item(1, 1002, at(5, 10), TimelineCategory.Pet, pet = 1),
    )
    private val yesterdayItems = listOf(
        item(0, 1002, at(22, 0, yesterday), TimelineCategory.Toileting, pet = 1, duration = 100),
    )

    @Test
    fun `the page counts this box's visits today and yesterday, and places them on the chart`() {
        val page = LitterRules.page(box, listOf(pet(1), pet(2)), todayItems, yesterdayItems, paris)

        assertEquals(DeviceStatus.Busy(BusyActivity.Working("cleaning")), page.status)
        assertEquals(VisitStats(visits = 2, averageDurationSec = 90), page.today)
        assertEquals(VisitStats(visits = 1, averageDurationSec = 100), page.yesterday)
        assertEquals(
            listOf(Visit(2, 6 * 60 + 19, 60, 1), Visit(5, 9 * 60, 120, 2)),
            page.visits,
        )
        // Every card of this box, the cleaning and the pet seen included; not the other box's.
        assertEquals(listOf(5L, 4L, 2L, 1L), page.activity.map { it.item.id })
        assertEquals(listOf(1L, 2L), page.chartPets.map { it.id })
        assertEquals(1, page.colorIndexOf(2))
        assertNull(page.colorIndexOf(null))
    }

    @Test
    fun `mode chips are the auto switches the device has, in a fixed order`() {
        assertEquals(
            listOf(DeviceMode("auto_work", "auto_work", on = true), DeviceMode("auto_spray", "auto_spray", on = false)),
            LitterRules.modes(box),
        )
        assertEquals(emptyList<DeviceMode>(), LitterRules.modes(device(1, DeviceFamily.LitterBox)))
    }

    @Test
    fun `a day without visits has no average`() {
        assertEquals(VisitStats(0, null), LitterRules.visitStats(emptyList()))
        assertEquals(
            VisitStats(1, null),
            LitterRules.visitStats(listOf(item(1, 1, at(1, 0), TimelineCategory.Toileting))),
        )
    }

    @Test
    fun `the use case follows the cache, and refreshing reads today and yesterday`() = runTest {
        val devices = Devices(listOf(box))
        val timeline = Timeline(mapOf(today to todayItems, yesterday to yesterdayItems))
        val pets = Pets(listOf(pet(1)))
        val clock = Clock.fixed(Instant.parse("2026-11-15T09:00:00Z"), paris)

        val page = ObserveLitterBoxUseCase(devices, pets, timeline, clock)(1002, today).first()
        assertEquals(2, page?.today?.visits)
        assertNull(ObserveLitterBoxUseCase(devices, pets, timeline, clock)(42, today).first())

        assertEquals(Outcome.Success(Unit), RefreshDevicePageUseCase(devices, pets, timeline)(today))
        assertEquals(setOf(today, yesterday), timeline.refreshed.toSet())
        assertEquals(1, devices.refreshes)
    }

    @Test
    fun `a T4 shows the N60 of the Pura Air in its K3 slot, and follows its level`() = runTest {
        val t4 = device(1002, DeviceFamily.LitterBox, type = "t4").copy(
            accessories = listOf(
                AccessoryRef(3003, "ctw3", DeviceFamily.Fountain),
                AccessoryRef(4001, "k3", DeviceFamily.Purifier),
                AccessoryRef(4002, "k3", DeviceFamily.Purifier),
            ),
        )
        fun k3(id: Long, liquid: Double) =
            device(id, DeviceFamily.Purifier, sensor("k3_liquid", liquid, unit = "%"), type = "k3")
                .copy(connection = DeviceConnection.Ble, parentId = 1002)
        val devices = Devices(listOf(t4, k3(4001, 63.0), k3(4002, 10.0)))
        val clock = Clock.fixed(Instant.parse("2026-11-15T09:00:00Z"), paris)
        val pages = ObserveLitterBoxUseCase(devices, Pets(emptyList()), Timeline(emptyMap()), clock)(1002, today)

        val page = pages.first()!!
        assertEquals(4001L, page.puraAir?.id)
        val n60 = page.hotspots.single { it.part == DevicePart.PuraAirSpray }
        assertEquals(PartReading.Percent(63), n60.reading)

        // A new report of the Pura Air's level reaches the box's page.
        devices.devices.value = listOf(t4, k3(4001, 12.0), k3(4002, 10.0))
        val low = pages.first()!!.hotspots.single { it.part == DevicePart.PuraAirSpray }
        assertEquals(PartReading.Percent(12), low.reading)
        assertEquals(true, low.attention)

        // No Pura Air listed: no N60 pill on a T4, which has no sprayer of its own.
        devices.devices.value = listOf(t4.copy(accessories = emptyList()), k3(4001, 63.0))
        val alone = pages.first()!!
        assertNull(alone.puraAir)
        assertEquals(false, alone.hotspots.any { it.part == DevicePart.PuraAirSpray || it.part == DevicePart.Spray })
    }

    private class Devices(initial: List<Device>) : DeviceRepository {
        val devices = MutableStateFlow(initial)
        var refreshes = 0

        override fun observeDevices(): Flow<List<Device>> = devices

        override fun observeDevice(id: Long): Flow<Device?> = devices.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit).also { refreshes++ }
    }

    private class Pets(private val initial: List<Pet>) : ListOnlyPets() {
        override fun observePets(): Flow<List<Pet>> = MutableStateFlow(initial)

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private class Timeline(initial: Map<LocalDate, List<TimelineItem>>) : DayOnlyTimeline() {
        val days = MutableStateFlow(initial)
        val refreshed = mutableListOf<LocalDate>()

        override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> = days.map { it[day].orEmpty() }

        override suspend fun refreshDay(day: LocalDate): Outcome<Unit> = Outcome.Success(Unit).also { refreshed += day }
    }

    @Test
    fun `the Pura Air light is a switch right after Deodorize, in the state the box reports`() {
        val actions = listOf(action("cleaning_start"), action("deodorize"), action("level_litter"))
        val withLight = box.copy(
            entities = box.entities + switch("k3_light", true).copy(name = "K3 Light"),
            actions = actions,
        )

        val light = DeviceAction("k3_light", "K3 Light", destructive = false, switchedOn = true)
        assertEquals(
            listOf(action("cleaning_start"), action("deodorize"), light, action("level_litter")),
            ActionSwitches.of(withLight, actions),
        )
        // The K3 switched its light off by itself: the box's next report says so.
        val off = withLight.copy(entities = box.entities + switch("k3_light", false))
        assertEquals(false, ActionSwitches.of(off, actions)[2].switchedOn)
    }

    @Test
    fun `no Pura Air light without its switch, and an unknown state reads as off`() {
        val actions = listOf(action("cleaning_start"), action("deodorize"))
        assertEquals(actions, ActionSwitches.of(box.copy(actions = actions), actions))

        val unknown = Entity("k3_light", "K3 Light", EntityComponent.Switch, EntityValue.None, settable = true)
        val light = ActionSwitches.of(box.copy(entities = box.entities + unknown), actions).last()
        assertEquals(false, light.switchedOn)
        // Without Deodorize, it comes last.
        assertEquals(
            "k3_light",
            ActionSwitches.of(box.copy(entities = box.entities + unknown), listOf(action("cleaning_start"))).last().key,
        )
    }
}

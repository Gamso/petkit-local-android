package org.petkitlocal.core.domain.fountain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.PageFixtures.at
import org.petkitlocal.core.domain.devicepage.PageFixtures.binary
import org.petkitlocal.core.domain.devicepage.PageFixtures.device
import org.petkitlocal.core.domain.devicepage.PageFixtures.item
import org.petkitlocal.core.domain.devicepage.PageFixtures.paris
import org.petkitlocal.core.domain.devicepage.PageFixtures.pet
import org.petkitlocal.core.domain.devicepage.PageFixtures.sensor
import org.petkitlocal.core.domain.devicepage.PageFixtures.switch
import org.petkitlocal.core.domain.devicepage.PageFixtures.today
import org.petkitlocal.core.domain.devicepage.PartReading
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

class FountainRulesTest {
    private val yesterday = today.minusDays(1)
    private val pets = listOf(pet(1), pet(2), pet(3))

    private val w7h = device(
        3001,
        DeviceFamily.Fountain,
        binary("pump_running", true),
        switch("child_lock", false),
        type = "w7h",
    )

    private fun mode(key: String, display: String?) = Entity(
        key,
        "Mode",
        EntityComponent.Select,
        EntityValue.Number(if (display == "smart") 2.0 else 1.0),
        display = display,
        options = listOf("normal", "smart"),
        settable = true,
    )

    private val w5 = device(
        3002,
        DeviceFamily.Fountain,
        sensor("w5_filter", 64.0, unit = "%"),
        binary("w5_running", true),
        mode("w5_mode", "smart"),
        switch("w5_dnd", false),
        switch("w5_child_lock", true),
        sensor("w5_pump_today", 14_400.0, unit = "s"),
        type = "w5",
    ).copy(connection = DeviceConnection.Ble, parentId = 2004)

    private val todayItems = listOf(
        item(30, 3001, at(9, 30), TimelineCategory.Drinking, pet = 2, duration = 40),
        item(29, 1002, at(8, 0), TimelineCategory.Toileting, pet = 1, duration = 90),
        item(28, 3001, at(7, 15), TimelineCategory.Drinking, pet = 1, duration = 20),
        item(27, 3001, at(6, 0), TimelineCategory.Drinking, duration = 30),
        item(26, 3001, at(5, 0), TimelineCategory.Cleaning),
    )
    private val yesterdayItems = listOf(
        item(20, 3001, at(22, 0, yesterday), TimelineCategory.Drinking, pet = 1, duration = 10),
        item(19, 3001, at(12, 0, yesterday), TimelineCategory.Drinking, pet = 3),
    )

    @Test
    fun `a W7H's day counts its drinks, by pet, and places them on the chart`() {
        val page = FountainRules.page(w7h, pets, todayItems, yesterdayItems, paris)

        assertTrue(page.recordsDrinks)
        assertEquals(DrinkStats(3, 30), page.today)
        assertEquals(DrinkStats(2, 10), page.yesterday)
        assertEquals(listOf(360, 435, 570), page.drinks.map { it.minuteOfDay })
        assertEquals(listOf(null, 1L, 2L), page.drinks.map { it.petId })
        assertEquals(
            listOf(PetDrinks(pets[0], 1, 1), PetDrinks(pets[1], 1, 0), PetDrinks(pets[2], 0, 1)),
            page.perPet,
        )
        assertEquals(listOf(pets[0], pets[1]), page.chartPets)
        assertEquals(1, page.colorIndexOf(2))
        assertNull(page.colorIndexOf(9))
        // No pump counter on a W7H, no relay for a WiFi device.
        assertNull(page.pumpTodaySec)
        assertNull(page.relay)
        assertEquals(DeviceStatus.Idle, page.status)
        assertEquals(listOf(DevicePart.Pump), page.hotspots.map { it.part })
    }

    @Test
    fun `a Bluetooth fountain shows its relay and pump time, and no drink per pet`() {
        val parent = device(2004, DeviceFamily.Feeder, type = "d4sh")
        val other = device(2001, DeviceFamily.Feeder, type = "d4")
        val page = FountainRules.page(w5, pets, todayItems, yesterdayItems, paris, relay = parent)

        assertFalse(page.recordsDrinks)
        assertEquals(parent, page.relay)
        assertEquals(14_400, page.pumpTodaySec)
        assertEquals(DrinkStats(0, null), page.today)
        assertTrue(page.perPet.isEmpty())
        // A device that is not its parent is not its relay.
        assertNull(FountainRules.page(w5, pets, emptyList(), emptyList(), paris, relay = other).relay)
    }

    @Test
    fun `mode chips come from the smart mode, do not disturb and lock, when reported`() {
        val modes = FountainRules.modes(w5)
        assertEquals(
            listOf(FountainModeKind.Smart, FountainModeKind.DoNotDisturb, FountainModeKind.ChildLock),
            modes.map { it.kind },
        )
        assertEquals(listOf(true, false, true), modes.map { it.on })
        assertEquals(EntityValue.Text("smart") to EntityValue.Text("normal"), modes[0].onValue to modes[0].offValue)
        assertEquals(EntityValue.Bool(true) to EntityValue.Bool(false), modes[1].onValue to modes[1].offValue)
        assertEquals(listOf("w5_mode", "w5_dnd", "w5_child_lock"), modes.map { it.key })

        // A W7H has a lock and no smart mode; nothing reported is no chip, not "off".
        assertEquals(listOf("child_lock"), FountainRules.modes(w7h).map { it.key })
        val unreported = w5.copy(
            entities = listOf(
                mode("w5_mode", null),
                Entity("w5_dnd", "Do Not Disturb", EntityComponent.Switch, EntityValue.None, settable = true),
            ),
        )
        assertTrue(FountainRules.modes(unreported).isEmpty())
    }

    @Test
    fun `the page follows the fountain and its relay in the cache`() = runTest {
        val parent = device(2004, DeviceFamily.Feeder, type = "d4sh")
        val devices = Devices(listOf(w5, parent))
        val timeline = Timeline(mapOf(today to todayItems, yesterday to yesterdayItems))
        val clock = Clock.fixed(Instant.parse("2026-11-15T09:00:00Z"), paris)
        val pages = ObserveFountainUseCase(devices, Pets(pets), timeline, clock)(3002, today)

        assertEquals(2004L, pages.first()?.relay?.id)
        assertEquals(PartReading.Percent(64), pages.first()?.hotspots?.first { it.part == DevicePart.Filter }?.reading)

        // A reset reported by the relay reaches the page; the relay leaving is no relay.
        val reset = w5.copy(
            entities = w5.entities.map { if (it.key == "w5_filter") sensor("w5_filter", 100.0, "%") else it },
        )
        devices.devices.value = listOf(reset)
        val page = pages.first()!!
        assertEquals(PartReading.Percent(100), page.hotspots.first { it.part == DevicePart.Filter }.reading)
        assertNull(page.relay)

        devices.devices.value = emptyList()
        assertNull(pages.first())
    }

    private class Devices(initial: List<Device>) : DeviceRepository {
        val devices = MutableStateFlow(initial)

        override fun observeDevices(): Flow<List<Device>> = devices

        override fun observeDevice(id: Long): Flow<Device?> = devices.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private class Pets(private val initial: List<Pet>) : PetRepository {
        override fun observePets(): Flow<List<Pet>> = MutableStateFlow(initial)

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private class Timeline(initial: Map<LocalDate, List<TimelineItem>>) : TimelineRepository {
        val days = MutableStateFlow(initial)

        override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> = days.map { it[day].orEmpty() }

        override suspend fun refreshDay(day: LocalDate): Outcome<Unit> = Outcome.Success(Unit)
    }
}

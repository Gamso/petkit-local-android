package org.petkitlocal.core.domain.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.PageFixtures.action
import org.petkitlocal.core.domain.devicepage.PageFixtures.binary
import org.petkitlocal.core.domain.devicepage.PageFixtures.device
import org.petkitlocal.core.domain.devicepage.PageFixtures.sensor
import org.petkitlocal.core.domain.devicepage.PageFixtures.switch
import org.petkitlocal.core.domain.devicepage.PartReading
import org.petkitlocal.core.domain.model.AccessoryRef
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.repository.DeviceRepository

class DeviceSettingsRulesTest {
    private val puraMax = device(
        1002,
        DeviceFamily.LitterBox,
        sensor("litter_percent", 91.0, unit = "%"),
        binary("waste_bin", false),
        sensor("n50_durability", 0.0, unit = "days"),
        sensor("n60_spray_days", 20.0, unit = "days"),
        switch("auto_work", true),
        actions = listOf(
            action("level_litter"),
            action("pack_waste"),
            action("reset", destructive = false),
            action("reset_n50", destructive = true),
            action("reset_n60", destructive = true),
        ),
        type = "t4",
    ).copy(
        transport = DeviceTransport.Mqtt,
        firmware = "1.652",
        serial = "20240305T4001288",
        accessories = listOf(AccessoryRef(4001, "k3", DeviceFamily.Purifier)),
    )

    private val puraAir = device(
        4001,
        DeviceFamily.Purifier,
        sensor("k3_liquid", 63.0, unit = "%"),
        type = "k3",
    ).copy(connection = DeviceConnection.Ble, parentId = 1002)

    @Test
    fun `a WiFi device says its transport, firmware and serial number`() {
        val identity = DeviceSettingsRules.identity(puraMax)

        assertEquals(DeviceIdentity("Model 1002", true, DeviceLink.Mqtt, "1.652", "20240305T4001288"), identity)
        assertEquals(DeviceLink.Http, DeviceSettingsRules.identity(puraMax.copy(transport = DeviceTransport.Http)).link)
    }

    @Test
    fun `what the server does not send is left out, never made up`() {
        val bare = puraMax.copy(firmware = null, serial = " ", transport = DeviceTransport.Unknown)

        with(DeviceSettingsRules.identity(bare)) {
            assertNull(link)
            assertNull(firmware)
            assertNull(serial)
        }
    }

    @Test
    fun `a Bluetooth accessory is reached through its relay, named once the cache holds it`() {
        assertEquals(DeviceLink.Bluetooth("Model 1002"), DeviceSettingsRules.identity(puraAir, relay = puraMax).link)
        assertEquals(DeviceLink.Bluetooth(null), DeviceSettingsRules.identity(puraAir).link)
        // Its transport is its parent's: the link is still Bluetooth.
        assertEquals(
            DeviceLink.Bluetooth(null),
            DeviceSettingsRules.identity(puraAir.copy(transport = DeviceTransport.Mqtt)).link,
        )
    }

    @Test
    fun `a litter box's consumables are its page's parts, each with its resets only`() {
        val page = DeviceSettingsRules.page(puraMax, puraAir = puraAir)

        // A T4 has no sprayer: its N60 is the Pura Air's, not the n60_spray_days it was sent.
        assertEquals(
            listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer, DevicePart.PuraAirSpray),
            page.consumables.map { it.part },
        )
        assertEquals(
            listOf(emptyList(), emptyList(), listOf("reset_n50"), listOf("reset_n60")),
            page.consumables.map { hotspot -> hotspot.actions.map { it.key } },
        )
        assertEquals(PartReading.Percent(63), page.consumables.last().reading)
        assertTrue(page.consumables[2].attention)
        assertEquals(puraAir, page.puraAir)
        assertEquals(listOf("auto_work"), page.groups.flatMap { group -> group.entities.map { it.key } })
    }

    @Test
    fun `a fountain's consumables leave its pump out, and a feeder's keep its desiccant reset`() {
        val w5 = device(
            3002,
            DeviceFamily.Fountain,
            binary("w5_water_missing", false),
            sensor("w5_filter", 12.0, unit = "%"),
            binary("w5_running", true),
            actions = listOf(action("w5_reset_filter"), action("fountain_reset_circulation_pump")),
            type = "w5",
        )
        val feeder = device(
            2001,
            DeviceFamily.Feeder,
            binary("food_low", true),
            sensor("desiccant_days", 3.0, unit = "days"),
            actions = listOf(action("food_replenished"), action("reset_desiccant", destructive = true)),
            type = "d4",
        )

        val fountain = DeviceSettingsRules.page(w5).consumables
        assertEquals(listOf(DevicePart.Water, DevicePart.Filter), fountain.map { it.part })
        assertEquals(listOf("w5_reset_filter"), fountain[1].actions.map { it.key })
        val feeding = DeviceSettingsRules.page(feeder).consumables
        assertEquals(listOf(DevicePart.Food, DevicePart.Desiccant), feeding.map { it.part })
        assertEquals(listOf(emptyList(), listOf("reset_desiccant")), feeding.map { h -> h.actions.map { it.key } })
        assertTrue(DeviceSettingsRules.page(puraAir).consumables.isEmpty())
    }

    @Test
    fun `the settings follow the device, its relay and its Pura Air in the cache`() = runTest {
        val devices = Devices(listOf(puraMax, puraAir))
        val observe = ObserveDeviceSettingsUseCase(devices)

        val box = observe(1002).first()!!
        assertEquals(DevicePart.PuraAirSpray, box.consumables.last().part)
        assertEquals(DeviceLink.Bluetooth("Model 1002"), observe(4001).first()!!.identity.link)
        assertNull(observe(9999).first())

        devices.state.value = listOf(puraMax)
        with(observe(1002).first()!!) {
            assertNull(puraAir)
            assertEquals(
                listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer),
                consumables.map {
                    it.part
                },
            )
        }
        devices.state.value = listOf(puraAir)
        assertEquals(DeviceLink.Bluetooth(null), observe(4001).first()!!.identity.link)
    }

    private class Devices(initial: List<Device>) : DeviceRepository {
        val state = MutableStateFlow(initial)

        override fun observeDevices(): Flow<List<Device>> = state

        override fun observeDevice(id: Long): Flow<Device?> = state.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
    }
}

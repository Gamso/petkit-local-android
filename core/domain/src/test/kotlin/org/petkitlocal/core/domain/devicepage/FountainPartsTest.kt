package org.petkitlocal.core.domain.devicepage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.devicepage.PageFixtures.action
import org.petkitlocal.core.domain.devicepage.PageFixtures.binary
import org.petkitlocal.core.domain.devicepage.PageFixtures.device
import org.petkitlocal.core.domain.devicepage.PageFixtures.sensor
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue

/**
 * The fountains as petkit-local describes them (`ha/entities/ble.py`, `ha/entities/sensors.py`):
 * the W5 family, the CTW3 with its battery, the W7H with its tray and tanks, and the hotspot each
 * reading gives.
 */
class FountainPartsTest {
    private fun w5(
        vararg entities: Entity = arrayOf(
            sensor("w5_filter", 64.0, unit = "%"),
            binary("w5_water_missing", false),
            binary("w5_filter_warn", false),
            binary("w5_running", true),
        ),
        type: String = "w5",
    ): Device = device(
        3002,
        DeviceFamily.Fountain,
        *entities,
        type = type,
        actions = listOf(action("w5_reset_filter", destructive = true)),
    )

    private val ctw3 = device(
        3003,
        DeviceFamily.Fountain,
        sensor("ctw3_filter", 41.0, unit = "%"),
        sensor("ctw3_battery", 76.0, unit = "%"),
        binary("ctw3_water_lack", false),
        binary("ctw3_filter_warn", false),
        binary("ctw3_running", true),
        binary("ctw3_low_battery", false),
        type = "ctw3",
        actions = listOf(action("ctw3_reset_filter", destructive = true)),
    )

    private val w7hActions = listOf(
        action("fountain_flush"),
        action("fountain_refill"),
        action("fountain_reset_circulation_pump"),
        action("fountain_drain", destructive = true),
        action("fountain_deep_clean", destructive = true),
    )

    private fun w7h(vararg entities: Entity): Device = device(
        3001,
        DeviceFamily.Fountain,
        *entities,
        type = "w7h",
        actions = w7hActions,
    )

    private val w7hAsMocked = w7h(
        sensor("temperature", 24.5, unit = "°C"),
        binary("tray_full", false),
        binary("tray_installed", true),
        binary("clean_tank_installed", true),
        binary("waste_tank_installed", true),
        binary("pump_running", true),
    )

    private fun parts(device: Device) = HotspotRules.fountain(device).map { it.part }

    @Test
    fun `the table says what each model has, as petkit-local does`() {
        val w5Family = setOf(DevicePart.Water, DevicePart.Filter, DevicePart.Pump)
        listOf("w5", "w4", "ctw2").forEach { assertEquals(w5Family, FountainParts.of(it), it) }
        assertEquals(w5Family + DevicePart.Battery, FountainParts.of("CTW3"))
        assertEquals(
            setOf(DevicePart.Tray, DevicePart.CleanTank, DevicePart.WasteTank, DevicePart.Pump),
            FountainParts.of("w7h"),
        )
        assertNull(FountainParts.of("w9"))
        // Only the W7H records drinks; a model the table does not know is not narrowed.
        assertTrue(FountainParts.recordsDrinks(w7hAsMocked))
        assertFalse(FountainParts.recordsDrinks(w5()))
        assertFalse(FountainParts.recordsDrinks(ctw3))
        assertTrue(FountainParts.recordsDrinks(w5(type = "w9")))
    }

    @Test
    fun `a W5 shows its water, filter and pump, in that order`() {
        val hotspots = HotspotRules.fountain(w5())
        assertEquals(listOf(DevicePart.Water, DevicePart.Filter, DevicePart.Pump), hotspots.map { it.part })
        assertEquals(PartReading.Condition(PartCondition.Ok), hotspots[0].reading)
        assertEquals(PartReading.Percent(64), hotspots[1].reading)
        assertEquals(listOf("w5_reset_filter"), hotspots[1].actions.map { it.key })
        assertEquals(PartReading.Condition(PartCondition.Running), hotspots[2].reading)
        assertTrue(hotspots.none { it.attention })
        assertEquals(listOf("w5_reset_filter"), FountainParts.actions(w5()).map { it.key })
    }

    @Test
    fun `a CTW3 adds its battery, and low readings or raised flags need attention`() {
        assertEquals(
            listOf(DevicePart.Water, DevicePart.Filter, DevicePart.Pump, DevicePart.Battery),
            parts(ctw3),
        )
        val trouble = ctw3.copy(
            entities = listOf(
                sensor("ctw3_filter", 12.0, unit = "%"),
                sensor("ctw3_battery", 60.0, unit = "%"),
                binary("ctw3_water_lack", true),
                binary("ctw3_running", false),
                binary("ctw3_low_battery", true),
            ),
        )
        val byPart = HotspotRules.fountain(trouble).associateBy { it.part }
        assertEquals(PartReading.Condition(PartCondition.Low), byPart.getValue(DevicePart.Water).reading)
        assertTrue(byPart.getValue(DevicePart.Water).attention)
        assertTrue(byPart.getValue(DevicePart.Filter).attention)
        // A pump at rest is not a fault: smart mode rests it between runs.
        assertEquals(PartReading.Condition(PartCondition.Stopped), byPart.getValue(DevicePart.Pump).reading)
        assertFalse(byPart.getValue(DevicePart.Pump).attention)
        // 60 % is plenty, but the fountain says its battery is low.
        assertTrue(byPart.getValue(DevicePart.Battery).attention)
    }

    @Test
    fun `the filter's raised flag needs attention at any level, and days left win over a level`() {
        val warned = w5(sensor("w5_filter", 64.0, unit = "%"), binary("w5_filter_warn", true))
        assertTrue(HotspotRules.fountain(warned).single { it.part == DevicePart.Filter }.attention)

        val wifi = w5(
            sensor("filter_percent", 50.0, unit = "%"),
            sensor("filter_days", 2.0, unit = "days"),
            type = "w9",
        )
        val filter = HotspotRules.fountain(wifi).single()
        assertEquals(PartReading.DaysLeft(2), filter.reading)
        assertTrue(filter.attention)

        // Only the flag reported: the pill says what the flag says.
        val flagOnly = w5(binary("w5_filter_warn", false))
        assertEquals(PartReading.Condition(PartCondition.Ok), HotspotRules.fountain(flagOnly).single().reading)
    }

    @Test
    fun `nothing reported is no pill, never missing`() {
        // A Bluetooth fountain whose parent has relayed nothing yet: every value null.
        val silent = w5(
            sensor("w5_filter", null, unit = "%"),
            Entity("w5_water_missing", "Water Low", EntityComponent.BinarySensor, EntityValue.None),
            Entity("w5_running", "Pump Running", EntityComponent.BinarySensor, EntityValue.None),
        )
        assertEquals(emptyList<DevicePart>(), parts(silent))
        // A W7H that has not reported its tanks shows only what it did.
        assertEquals(listOf(DevicePart.Pump), parts(w7h(binary("pump_running", false))))
    }

    @Test
    fun `a W7H shows its tray, tanks and pump, and no filter or battery even when listed`() {
        val hotspots = HotspotRules.fountain(w7hAsMocked)
        assertEquals(
            listOf(DevicePart.Pump, DevicePart.Tray, DevicePart.CleanTank, DevicePart.WasteTank),
            hotspots.map { it.part },
        )
        val byPart = hotspots.associateBy { it.part }
        assertEquals(
            listOf("fountain_flush", "fountain_drain"),
            byPart.getValue(DevicePart.Tray).actions.map { it.key },
        )
        assertEquals(listOf("fountain_refill"), byPart.getValue(DevicePart.CleanTank).actions.map { it.key })
        assertEquals(
            listOf("fountain_reset_circulation_pump"),
            byPart.getValue(DevicePart.Pump).actions.map { it.key },
        )

        val listed = w7h(sensor("filter_percent", 50.0, unit = "%"), sensor("battery", 80.0, unit = "%"))
            .let { it.copy(actions = it.actions + action("reset_filter", destructive = true)) }
        assertEquals(emptyList<DevicePart>(), parts(listed))
        assertEquals(w7hActions, FountainParts.actions(listed))
    }

    @Test
    fun `a W7H's tray reads removed, full or OK, and a tank removed only when it says so`() {
        fun trayOf(vararg entities: Entity) =
            HotspotRules.fountain(w7h(*entities)).single { it.part == DevicePart.Tray }
        assertEquals(
            PartReading.Condition(PartCondition.Removed),
            trayOf(binary("tray_full", true), binary("tray_installed", false)).reading,
        )
        val full = trayOf(binary("tray_full", true), binary("tray_installed", true))
        assertEquals(PartReading.Condition(PartCondition.Full), full.reading)
        assertTrue(full.attention)
        assertFalse(trayOf(binary("tray_full", false)).attention)

        val tank = HotspotRules.fountain(w7h(binary("clean_tank_installed", false))).single()
        assertEquals(PartReading.Condition(PartCondition.Removed), tank.reading)
        assertTrue(tank.attention)
    }
}

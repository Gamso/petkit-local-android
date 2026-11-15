package org.petkitlocal.core.domain.devicepage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
 * The litter boxes of the mock (mock-server/README.md), each with the entities and buttons
 * petkit-local publishes to it, its common bundle included: every model reports an N50 and an N60
 * countdown and lists both resets, whatever it really has.
 */
class LitterPartsTest {
    private val commonActions = listOf(
        action("cleaning_start"),
        action("deodorize"),
        action("level_litter"),
        action("dump_litter", destructive = true),
        action("reset_n50", destructive = true),
        action("reset_n60", destructive = true),
    )

    private fun box(type: String, presence: Boolean? = null, bags: Boolean = false): Device = device(
        1,
        DeviceFamily.LitterBox,
        sensor("litter_percent", 82.0, unit = "%"),
        binary("waste_bin", false),
        binary("waste_bin_present", true),
        sensor("n50_durability", 21.0, unit = "days"),
        sensor("n60_spray_days", 17.0, unit = "days"),
        *listOfNotNull(
            presence?.let { binary("deodorizer_present", it) },
            sensor("package_count", 7.0).takeIf { bags },
        ).toTypedArray(),
        type = type,
        actions = if (bags) commonActions.filter { it.key != "reset_n50" } + action("pack_waste") else commonActions,
    )

    private fun parts(device: Device) = HotspotRules.litterBox(device).map { it.part }

    /** A Pura Air as the server describes one (`ha/entities/ble.py`): its N60 level and battery. */
    private val puraAir = device(
        4001,
        DeviceFamily.Purifier,
        sensor("k3_liquid", 63.0, unit = "%"),
        sensor("k3_battery", 88.0, unit = "%"),
        type = "k3",
    )

    @Test
    fun `the table says what each model has, as petkit-local does`() {
        val base = listOf(DevicePart.Litter, DevicePart.WasteBin)
        assertEquals(base + DevicePart.Deodorizer, LitterParts.of("t3")!!.sortedBy { it.ordinal })
        assertEquals(base + DevicePart.Deodorizer, LitterParts.of("T4")!!.sortedBy { it.ordinal })
        assertEquals(base + DevicePart.Deodorizer + DevicePart.Spray, LitterParts.of("t5")!!.sortedBy { it.ordinal })
        assertEquals(base + DevicePart.Spray + DevicePart.Bags, LitterParts.of("t6")!!.sortedBy { it.ordinal })
        assertEquals(base + DevicePart.Deodorizer + DevicePart.Spray, LitterParts.of("t7")!!.sortedBy { it.ordinal })
        assertNull(LitterParts.of("t9"))
    }

    @Test
    fun `a Pura X and a Pura Max show no N60, and offer no N60 reset, though the server lists both`() {
        listOf("t3", "t4").forEach { type ->
            val box = box(type)
            assertEquals(listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer), parts(box))
            assertEquals(
                listOf("cleaning_start", "deodorize", "level_litter", "dump_litter", "reset_n50"),
                LitterParts.actions(box).map { it.key },
            )
        }
    }

    @Test
    fun `a Purobot Max Pro 2 and a Crystal Duo show both deodorizers and keep every reset`() {
        listOf("t5", "t7").forEach { type ->
            val box = box(type, presence = true)
            assertEquals(
                listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer, DevicePart.Spray),
                parts(box),
            )
            assertEquals(commonActions, LitterParts.actions(box))
        }
    }

    @Test
    fun `a Purobot Ultra shows its spray and its bags, and no N50 it does not have`() {
        val ultra = box("t6", presence = true, bags = true)

        assertEquals(listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Spray, DevicePart.Bags), parts(ultra))
        val kept = LitterParts.actions(ultra).map { it.key }
        assertEquals(listOf("reset_n60", "pack_waste"), kept.filter { it.startsWith("reset_n") || it == "pack_waste" })
    }

    @Test
    fun `a Pura Max without a Pura Air has no N60 at all`() {
        val t4 = box("t4")

        assertEquals(false, LitterParts.hasN60(t4))
        assertEquals(
            emptyList<DevicePart>(),
            parts(t4).filter {
                it == DevicePart.Spray || it == DevicePart.PuraAirSpray
            },
        )
    }

    @Test
    fun `a Pura Max paired with a Pura Air shows the Pura Air's N60 and offers its reset`() {
        val t4 = box("t4")

        val hotspots = HotspotRules.litterBox(t4, puraAir)

        val n60 = hotspots.single { it.part == DevicePart.PuraAirSpray }
        assertEquals(PartReading.Percent(63), n60.reading)
        assertEquals("k3_liquid", n60.entityKey)
        assertEquals(listOf("deodorize", "reset_n60"), n60.actions.map { it.key })
        assertEquals(false, hotspots.any { it.part == DevicePart.Spray })
        assertEquals(true, "reset_n60" in LitterParts.actions(t4, puraAir).map { it.key })
    }

    @Test
    fun `a box with its own sprayer shows that one, even with a Pura Air`() {
        listOf("t5", "t7").forEach { type ->
            val hotspots = HotspotRules.litterBox(box(type, presence = true), puraAir)
            assertEquals(PartReading.DaysLeft(17), hotspots.single { it.part == DevicePart.Spray }.reading)
            assertEquals(false, hotspots.any { it.part == DevicePart.PuraAirSpray })
        }
    }

    @Test
    fun `the N60 is missing only when the box says so, and an unreported part draws nothing`() {
        val spray = { box: Device -> HotspotRules.litterBox(box).firstOrNull { it.part == DevicePart.Spray }?.reading }

        assertEquals(PartReading.DaysLeft(17), spray(box("t7", presence = true)))
        assertEquals(PartReading.DaysLeft(17), spray(box("t7", presence = null)))
        assertEquals(PartReading.Condition(PartCondition.Missing), spray(box("t7", presence = false)))
        // No countdown and no presence flag: nothing is known, so no pill rather than a guess.
        val silent = device(
            5,
            DeviceFamily.LitterBox,
            Entity("n60_spray_days", "n60", EntityComponent.Sensor, EntityValue.None, unit = "days"),
            Entity("waste_bin", "bin", EntityComponent.BinarySensor, EntityValue.None),
            type = "t5",
        )
        assertEquals(emptyList<DevicePart>(), parts(silent))
    }

    @Test
    fun `a model the table does not know keeps every part and button it reports`() {
        val future = box("t9", presence = true)

        assertEquals(
            listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer, DevicePart.Spray),
            parts(future),
        )
        assertEquals(commonActions, LitterParts.actions(future))
    }
}

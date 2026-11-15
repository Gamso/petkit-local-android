package org.petkitlocal.core.domain.devicepage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.devicepage.PageFixtures.action
import org.petkitlocal.core.domain.devicepage.PageFixtures.binary
import org.petkitlocal.core.domain.devicepage.PageFixtures.device
import org.petkitlocal.core.domain.devicepage.PageFixtures.sensor
import org.petkitlocal.core.domain.model.DeviceFamily

class HotspotRulesTest {
    @Test
    fun `a model the table does not know shows every part it reports, each with the actions on it`() {
        val t4 = device(
            1002,
            DeviceFamily.LitterBox,
            sensor("litter_percent", 91.0, unit = "%"),
            binary("waste_bin", false),
            binary("waste_bin_present", true),
            sensor("n50_durability", 21.0, unit = "days"),
            sensor("n60_spray_days", 2.0, unit = "days"),
            sensor("rssi", -54.0, unit = "dBm"),
            actions = listOf(
                action("cleaning_start"),
                action("level_litter"),
                action("dump_litter", destructive = true),
                action("reset_n50", destructive = true),
                action("reset_n60", destructive = true),
            ),
        )

        val hotspots = HotspotRules.litterBox(t4)

        assertEquals(
            listOf(DevicePart.Litter, DevicePart.WasteBin, DevicePart.Deodorizer, DevicePart.Spray),
            hotspots.map { it.part },
        )
        val litter = hotspots[0]
        assertEquals(PartReading.Percent(91), litter.reading)
        assertEquals(listOf("level_litter", "dump_litter"), litter.actions.map { it.key })
        assertEquals(PartReading.Condition(PartCondition.Ok), hotspots[1].reading)
        assertEquals(false, hotspots[2].attention)
        // Two days of spray left is under the alert threshold.
        assertEquals(PartReading.DaysLeft(2), hotspots[3].reading)
        assertTrue(hotspots[3].attention)
        assertEquals(listOf("reset_n60"), hotspots[3].actions.map { it.key })
    }

    @Test
    fun `low litter, a full or removed bin and a missing spray need attention`() {
        val box = device(
            1,
            DeviceFamily.LitterBox,
            sensor("litter_percent", 12.0, unit = "%"),
            binary("waste_bin", true),
            binary("deodorizer_present", false),
            sensor("n60_spray_days", 30.0, unit = "days"),
        )
        val removed = device(2, DeviceFamily.LitterBox, binary("waste_bin", true), binary("waste_bin_present", false))

        val hotspots = HotspotRules.litterBox(box)

        assertTrue(hotspots.all { it.attention })
        assertEquals(PartReading.Condition(PartCondition.Full), hotspots[1].reading)
        assertEquals(PartReading.Condition(PartCondition.Missing), hotspots[2].reading)
        assertEquals(PartReading.Condition(PartCondition.Removed), HotspotRules.litterBox(removed).single().reading)
    }

    @Test
    fun `an unreported value draws no pill, and a consumable added later gets one of its own`() {
        val box = device(
            1,
            DeviceFamily.LitterBox,
            sensor("litter_percent", null, unit = "%"),
            sensor("filter_days", 2.0, unit = "days"),
            sensor("package_count", 0.0),
        )

        val hotspots = HotspotRules.litterBox(box)

        assertEquals(listOf(DevicePart.Bags, DevicePart.Other), hotspots.map { it.part })
        assertEquals(PartReading.Count(0), hotspots[0].reading)
        assertTrue(hotspots[0].attention)
        assertEquals("filter_days", hotspots[1].entityKey)
    }

    @Test
    fun `a dual hopper shows each hopper, a single one its level or its low flag`() {
        val d4sh = device(
            2004,
            DeviceFamily.Feeder,
            sensor("hopper1_level", 2.0, display = "Has food"),
            sensor("hopper2_level", 0.0, display = "Empty"),
            sensor("desiccant_days", 18.0, unit = "days"),
            actions = listOf(action("food_replenished"), action("reset_desiccant", destructive = true)),
        )
        val d4 = device(2001, DeviceFamily.Feeder, binary("food_low", true), sensor("food_bowl_pct", 40.0, unit = "%"))

        val dual = HotspotRules.feeder(d4sh)
        assertEquals(listOf(DevicePart.Hopper1, DevicePart.Hopper2, DevicePart.Desiccant), dual.map { it.part })
        assertEquals(listOf(false, true, false), dual.map { it.attention })
        assertEquals(PartReading.Option("Empty"), dual[1].reading)
        assertEquals(listOf("reset_desiccant"), dual[2].actions.map { it.key })

        val single = HotspotRules.feeder(d4)
        assertEquals(listOf(DevicePart.Food, DevicePart.Bowl), single.map { it.part })
        assertEquals(PartReading.Condition(PartCondition.Low), single[0].reading)
        // A bowl level is information, not a consumable running out.
        assertEquals(false, single[1].attention)
    }
}

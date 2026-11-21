package org.petkitlocal.core.domain.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.model.DeviceAction

class DeviceActionOrderTest {
    private fun action(key: String, destructive: Boolean = false) = DeviceAction(key, key, destructive)

    private fun ActionLayout.keys() = main.map { it.key } to more.map { it.key }

    @Test
    fun `everyday actions are neither maintenance nor destructive`() {
        assertEquals(true, DeviceActionOrder.isEveryday(action("fountain_refill")))
        assertEquals(true, DeviceActionOrder.isEveryday(action("a_button_added_later")))
        assertEquals(false, DeviceActionOrder.isEveryday(action("fountain_reset_circulation_pump")))
        assertEquals(false, DeviceActionOrder.isEveryday(action("maintenance_start")))
        assertEquals(false, DeviceActionOrder.isEveryday(action("w5_reset_filter", destructive = true)))
        assertEquals(false, DeviceActionOrder.isEveryday(action("fountain_drain", destructive = true)))
    }

    @Test
    fun `a page needs its sheet for an everyday action, or for one no pill offers`() {
        val reset = action("ctw3_reset_filter", destructive = true)
        // A Bluetooth fountain: its one button is on the filter's pill. No sheet.
        assertEquals(false, DeviceActionOrder.needsSheet(listOf(reset), onParts = setOf(reset.key)))
        // The same button on no pill would be out of reach without the sheet.
        assertEquals(true, DeviceActionOrder.needsSheet(listOf(reset), onParts = emptySet()))
        // An everyday action keeps the sheet, even when a pill offers it too.
        val refill = action("fountain_refill")
        val both = setOf("fountain_refill", "ctw3_reset_filter")
        assertEquals(true, DeviceActionOrder.needsSheet(listOf(refill, reset), onParts = both))
        // Nothing to press, nothing to show.
        assertEquals(false, DeviceActionOrder.needsSheet(emptyList(), onParts = emptySet()))
    }

    @Test
    fun `a litter box keeps clean, deodorize and level in view, maintenance and destructive behind More`() {
        // The Pura Max's actions, in the server's order (mock catalog, t4).
        val t4 = listOf(
            action("cleaning_start"),
            action("maintenance_stop"),
            action("deodorize"),
            action("pause"),
            action("resume"),
            action("reset"),
            action("level_litter"),
            action("finish_litter_calibration"),
            action("maintenance_start", destructive = true),
            action("dump_litter", destructive = true),
            action("reset_n50", destructive = true),
        )

        assertEquals(
            listOf("cleaning_start", "deodorize", "level_litter") to listOf(
                "pause",
                "resume",
                "maintenance_stop",
                "reset",
                "finish_litter_calibration",
                "maintenance_start",
                "dump_litter",
                "reset_n50",
            ),
            DeviceActionOrder.layout(t4).keys(),
        )
    }

    @Test
    fun `a feeder feeds first, and a fountain refills first`() {
        val d4sh = listOf(
            action("feed"),
            action("cancel_manual_feed"),
            action("food_replenished"),
            action("play_sound"),
            action("feed_hopper_1"),
            action("feed_hopper_2"),
            action("reset_desiccant", destructive = true),
        )
        val w7h = listOf(
            action("fountain_flush"),
            action("fountain_refill"),
            action("fountain_reset_circulation_pump"),
            action("fountain_drain", destructive = true),
            action("fountain_deep_clean", destructive = true),
        )

        assertEquals(
            listOf("feed", "feed_hopper_1", "feed_hopper_2") to
                listOf("food_replenished", "play_sound", "cancel_manual_feed", "reset_desiccant"),
            DeviceActionOrder.layout(d4sh).keys(),
        )
        assertEquals(
            listOf("fountain_refill", "fountain_flush") to
                listOf("fountain_reset_circulation_pump", "fountain_drain", "fountain_deep_clean"),
            DeviceActionOrder.layout(w7h).keys(),
        )
    }

    @Test
    fun `few everyday actions need no More, and an unknown one comes after the known ones`() {
        assertEquals(
            listOf("feed", "brand_new", "other_new") to emptyList<String>(),
            DeviceActionOrder.layout(listOf(action("brand_new"), action("feed"), action("other_new"))).keys(),
        )
        assertEquals(emptyList<String>() to emptyList<String>(), DeviceActionOrder.layout(emptyList()).keys())
    }

    @Test
    fun `a T4's Pura Air light is in view right after Deodorize`() {
        val light = DeviceAction("k3_light", "K3 Light", destructive = false, switchedOn = false)
        val actions = listOf(
            DeviceAction("cleaning_start", "Scoop", false),
            DeviceAction("deodorize", "Deodorize", false),
            light,
            DeviceAction("level_litter", "Level", false),
            DeviceAction("reset_n50", "Reset N50", true),
        )
        val layout = DeviceActionOrder.layout(actions)
        assertEquals(listOf("cleaning_start", "deodorize", "k3_light"), layout.main.map { it.key })
        assertEquals("level_litter", layout.more.first().key)
    }
}

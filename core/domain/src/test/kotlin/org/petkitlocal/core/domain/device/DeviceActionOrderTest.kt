package org.petkitlocal.core.domain.device

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.model.DeviceAction

class DeviceActionOrderTest {
    private fun action(key: String, destructive: Boolean = false) = DeviceAction(key, key, destructive)

    private fun ActionLayout.keys() = main.map { it.key } to more.map { it.key }

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
}

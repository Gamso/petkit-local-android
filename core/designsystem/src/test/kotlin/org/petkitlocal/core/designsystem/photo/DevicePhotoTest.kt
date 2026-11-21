package org.petkitlocal.core.designsystem.photo

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DevicePhotoTest {
    /**
     * A codename PetKit sells as one product (petkit-local `DEVICE_NAMES`) gives its photo; the
     * server reports no generation for it.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "t3, PuraX",
        "t5, PurobotMaxPro2",
        "t6, PurobotUltra",
        "t7, PurobotCrystalDuo",
        "d4, FreshElementSolo",
        "d4s, FreshElementGemini",
        "w7h, EverSweetUltraAi",
        "w5, EverSweet3Pro",
        "ctw2, EverSweetSolo2",
        "k3, PuraAir",
        "T5, PurobotMaxPro2",
    )
    fun `one product per codename`(type: String, photo: DevicePhoto) {
        assertEquals(photo, DevicePhoto.of(type, generation = null))
    }

    /**
     * Several generations under one codename (petkit-local `MODEL_GENERATIONS`): the photo of the
     * generation the server reports.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
        "t4, 1, PuraMax",
        "t4, 2, PuraMax2",
        "d4h, 1, YumShareSolo",
        "d4h, 2, YumShareSolo2",
        "d4sh, 1, YumShareDualHopper",
        "d4sh, 2, YumShareDualHopper2",
        "ctw3, 2, EverSweetMax2",
        "T4, 2, PuraMax2",
    )
    fun `a generation gets its own photo`(type: String, generation: Int, photo: DevicePhoto) {
        assertEquals(photo, DevicePhoto.of(type, generation))
    }

    /**
     * No generation (unknown to the server, or a server that predates them, which names the
     * device by its codename alone, "Pura Max"): the first generation's photo, matching that name.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource("t4, PuraMax", "d4h, YumShareSolo", "d4sh, YumShareDualHopper")
    fun `no generation is the first`(type: String, photo: DevicePhoto) {
        assertEquals(photo, DevicePhoto.of(type, generation = null))
    }

    /**
     * A generation the app has no photo of keeps the drawing, never another generation's photo:
     * a Pura Max 3, a third Solo or Dual-Hopper, an EverSweet Max Cordless (the photo is the Max
     * 2), a second generation of a one-product codename, and numbers no generation has.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource("t4, 3", "d4h, 3", "d4sh, 3", "ctw3, 1", "ctw3, 3", "t4, 20", "t5, 2", "w5, 2", "t4, 0", "t4, -1")
    fun `no photo for a generation without one`(type: String, generation: Int) {
        assertNull(DevicePhoto.of(type, generation))
    }

    /**
     * A `ctw3` of unknown generation is the first, which the server names EverSweet Max Cordless:
     * no photo, as the one the app has is the Max 2's.
     */
    @Test
    fun `no generation of a ctw3 keeps the drawing`() {
        assertNull(DevicePhoto.of("ctw3", generation = null))
    }

    /** Models petkit-local does not support yet, and codenames nobody knows, keep the drawing. */
    @ParameterizedTest(name = "{0}")
    @CsvSource("w4", "d3", "feedermini", "k2", "z9", "''")
    fun `no photo for a model without one`(type: String) {
        assertNull(DevicePhoto.of(type, generation = null))
        assertNull(DevicePhoto.of(type, generation = 1))
    }

    /** Every photo shipped is some codename's: none is unreachable. */
    @Test
    fun `every photo is chosen for some codename and generation`() {
        val types = listOf("t3", "t4", "t5", "t6", "t7", "d4", "d4s", "d4h", "d4sh", "w7h", "w5", "ctw2", "ctw3", "k3")
        val reached = types.flatMap { type -> (1..3).mapNotNull { DevicePhoto.of(type, it) } }.toSet()
        assertEquals(DevicePhoto.entries.toSet(), reached)
    }

    /** The images are PetKit's: NOTICE names every one shipped, and every photo has its image. */
    @Test
    fun `every shipped photo is in NOTICE`() {
        val shipped = File("src/main/res/drawable-nodpi").list().orEmpty().filter { it.startsWith("device_photo_") }
        val notice = File("../../NOTICE").readText()
        assertEquals(DevicePhoto.entries.size, shipped.size)
        shipped.forEach { assertTrue(it in notice) { "$it is not in NOTICE" } }
        assertTrue("NOT covered by the MIT licence" in notice.replace(Regex("\\s+"), " "))
    }
}

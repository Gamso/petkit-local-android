package org.petkitlocal.core.designsystem.photo

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DevicePhotoTest {
    /** The server's codename and name (petkit-local `DEVICE_NAMES`) give the product's photo. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
        "t3, Pura X, PuraX",
        "t5, Purobot Max Pro 2, PurobotMaxPro2",
        "t6, Purobot Ultra, PurobotUltra",
        "t7, Purobot Crystal Duo, PurobotCrystalDuo",
        "d4, Fresh Element Solo, FreshElementSolo",
        "d4s, Fresh Element Gemini, FreshElementGemini",
        "w7h, EverSweet Ultra AI, EverSweetUltraAi",
        "w5, EverSweet 3 Pro, EverSweet3Pro",
        "ctw2, EverSweet Solo 2, EverSweetSolo2",
        "ctw3, EverSweet Max Cordless, EverSweetMax2",
        "k3, Pura Air K3, PuraAir",
        "T5, Purobot Max Pro 2, PurobotMaxPro2",
    )
    fun `one product per codename`(type: String, model: String, photo: DevicePhoto) {
        assertEquals(photo, DevicePhoto.of(type, model))
    }

    /**
     * Several generations under one codename: the one the server's name says, the first when it
     * names none (what petkit-local calls both today), so the photo matches the name shown.
     */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
        "t4, Pura Max, PuraMax",
        "t4, Pura Max 2, PuraMax2",
        "d4h, YumShare Solo, YumShareSolo",
        "d4h, YumShare Solo 2, YumShareSolo2",
        "d4sh, YumShare Dual-Hopper, YumShareDualHopper",
        "d4sh, YumShare Dual-Hopper 2, YumShareDualHopper2",
        "d4sh, ' YumShare Dual-Hopper 2 ', YumShareDualHopper2",
        "T4, Pura Max, PuraMax",
    )
    fun `a generation by its name`(type: String, model: String, photo: DevicePhoto) {
        assertEquals(photo, DevicePhoto.of(type, model))
    }

    /** A generation the app has no photo of keeps the drawing. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource("t4, Pura Max 3", "d4h, YumShare Solo 3", "d4sh, YumShare Dual-Hopper 3", "t4, Pura Max 20")
    fun `no photo for a generation without one`(type: String, model: String) {
        assertNull(DevicePhoto.of(type, model))
    }

    @Test
    fun `the generation is the name's trailing number`() {
        assertEquals(1, DevicePhoto.generationOf("Pura Max"))
        assertEquals(2, DevicePhoto.generationOf("Pura Max 2"))
        assertEquals(3, DevicePhoto.generationOf("Pura Max 3 "))
        assertEquals(1, DevicePhoto.generationOf("PuraMax2"))
        assertEquals(1, DevicePhoto.generationOf(""))
    }

    /** Models petkit-local does not support yet, and codenames nobody knows, keep the drawing. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource("w4, EverSweet", "d3, Feeder D3", "feedermini, Feeder Mini", "k2, Pura Air", "z9, Unknown", "'', ''")
    fun `no photo for a model without one`(type: String, model: String) {
        assertNull(DevicePhoto.of(type, model))
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

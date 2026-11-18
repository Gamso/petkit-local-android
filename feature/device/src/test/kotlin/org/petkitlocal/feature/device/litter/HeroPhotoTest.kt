package org.petkitlocal.feature.device.litter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.designsystem.photo.DevicePhoto
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.testing.Household

/**
 * A device page's hero is the photo of the generation the server reports (app issue #81), and so
 * are the places of its pills: a Pura Max 2's N60 is on the left of the box, where a Pura Max has
 * none.
 */
class HeroPhotoTest {
    private val puraMax = Household.litterBox

    @Test
    fun `a second generation gets its own photo and pills`() {
        val photo = heroPhoto(puraMax.copy(modelName = "Pura Max 2", generation = 2))
        assertEquals(DevicePhoto.PuraMax2, photo)
        assertEquals(PhotoSpot(6f, 45f, PillSide.Left), PhotoSpots.of(photo!!, DevicePart.PuraAirSpray))

        val dualHopper = Household.dualFeeder.copy(modelName = "YumShare Dual-Hopper 2", generation = 2)
        assertEquals(DevicePhoto.YumShareDualHopper2, heroPhoto(dualHopper))
    }

    @Test
    fun `the first generation, or none reported, gets the first one's`() {
        assertEquals(DevicePhoto.PuraMax, heroPhoto(puraMax.copy(generation = 1)))
        assertEquals(DevicePhoto.PuraMax, heroPhoto(puraMax.copy(generation = null)))
        assertNull(PhotoSpots.of(DevicePhoto.PuraMax, DevicePart.PuraAirSpray))
    }

    @Test
    fun `a generation without a photo keeps the drawing`() {
        assertNull(heroPhoto(puraMax.copy(modelName = "Pura Max 3", generation = 3)))
    }

    @Test
    fun `a photo that places no part is no hero`() {
        assertEquals(DeviceFamily.Purifier, Household.purifier.family)
        assertNull(heroPhoto(Household.purifier))
    }
}

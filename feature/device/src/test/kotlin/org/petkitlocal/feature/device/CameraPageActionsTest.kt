package org.petkitlocal.feature.device

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.device.DeviceActionOrder
import org.petkitlocal.core.domain.devicepage.Hotspot
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.feature.device.feeder.FeederFixtures
import org.petkitlocal.feature.device.fountain.FountainFixtures
import org.petkitlocal.feature.device.litter.LitterFixtures

/**
 * A camera's page has no part pills (issue #101), so every action their dialogs offered must be in
 * its action sheet, and the sheet must be there: the pages pass it no pill keys.
 */
class CameraPageActionsTest {
    private fun assertInSheet(hotspots: List<Hotspot>, sheet: List<DeviceAction>) {
        val keys = sheet.map { it.key }.toSet()
        val offered = hotspots.flatMap { it.actions }.map { it.key }
        assertTrue(offered.isNotEmpty(), "the fixture's pills offer actions")
        assertTrue(keys.containsAll(offered), "missing from the sheet: ${offered - keys}")
        assertTrue(DeviceActionOrder.needsSheet(sheet, onParts = emptySet()))
    }

    @Test
    fun `a camera litter box's part actions are in its sheet`() {
        val page = LitterFixtures.trouble.page!!
        assertTrue(page.device.isCamera)
        assertInSheet(page.hotspots, page.actions)
    }

    @Test
    fun `a camera feeder's part actions are in its sheet`() {
        val page = FeederFixtures.dualPaused.page!!
        assertTrue(page.device.isCamera)
        assertInSheet(page.hotspots, page.device.actions)
    }

    @Test
    fun `a camera fountain's part actions are in its sheet`() {
        val page = FountainFixtures.content.page!!
        assertTrue(page.device.isCamera)
        assertInSheet(page.hotspots, page.actions)
    }
}

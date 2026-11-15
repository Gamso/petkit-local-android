package org.petkitlocal.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The keyed translation of the server's labels (SPEC.md §7), on real resources. */
@RunWith(RobolectricTestRunner::class)
class ServerLabelsTest {
    private val labels get() = ServerLabels(ApplicationProvider.getApplicationContext<Context>().resources)

    @Test
    fun `English is the server's own text`() {
        assertEquals("Auto Clean", labels.entity("auto_work", "Auto Clean"))
        assertEquals("Scoop", labels.action("cleaning_start", "Scoop"))
        assertEquals("idle", labels.option("idle"))
        assertEquals("Toileting", labels.filter("toileting"))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `French is found by key, whatever the server's text`() {
        assertEquals("Nettoyage automatique", labels.entity("auto_work", "Auto Clean"))
        assertEquals("Nettoyer", labels.action("cleaning_start", "Scoop"))
        assertEquals("au repos", labels.option("idle"))
        assertEquals("Toilettes", labels.filter("toileting"))
        assertEquals("Passage aux toilettes", labels.eventKind("toilet_visit", "Pet used the toilet"))
        assertEquals("Vidanger et rincer", labels.action("fountain_flush", "Drain & Flush"))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `an unknown key shows the server's text, never a blank or a crash`() {
        assertEquals("Hall: Dump", labels.entity("hall_dump", "Hall: Dump"))
        assertEquals("Brand new", labels.action("brand_new", "Brand new"))
        assertEquals("sparkling", labels.option("sparkling"))
        assertEquals("Something", labels.eventKind("new_kind", "Something"))
        assertEquals("new_kind", labels.eventKind("new_kind", null))
        assertFalse(labels.knowsEntity("hall_dump"))
        assertTrue(labels.knowsEntity("auto_work"))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `every keyed label resolves to a non-blank text`() {
        val maps = listOf(EntityLabels, ActionLabels, OptionLabels, FilterLabels, EventKindLabels)
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        maps.forEach { map -> map.forEach { (key, id) -> assertTrue(key, resources.getString(id).isNotBlank()) } }
        assertEquals(260, maps.sumOf { it.size })
    }
}

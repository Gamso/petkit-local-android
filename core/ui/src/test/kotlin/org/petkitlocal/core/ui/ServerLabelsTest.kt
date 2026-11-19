package org.petkitlocal.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.activity.StepLabel
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
        assertEquals(261, maps.sumOf { it.size })
        EntityOptionLabels.forEach { (entity, options) ->
            options.forEach { (option, id) -> assertTrue("$entity $option", resources.getString(id).isNotBlank()) }
        }
    }

    @Test
    fun `a CTW3 names its modes as PetKit does, and a W5 keeps its own names`() {
        assertEquals("continuous", labels.option("normal", "ctw3_mode"))
        assertEquals("intermittent", labels.option("smart", "ctw3_mode"))
        assertEquals("battery mode", labels.option("battery", "ctw3_mode"))
        assertEquals("smart", labels.option("smart", "w5_mode"))
        assertEquals("normal", labels.option("normal", null))
        assertEquals("Intermittent Run Time", labels.entity("ctw3_smart_work", "Intermittent Run Time"))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `in French, a CTW3's modes are Continu, Intermittent and Mode batterie`() {
        assertEquals("continu", labels.option("normal", "ctw3_mode"))
        assertEquals("intermittent", labels.option("smart", "ctw3_mode"))
        assertEquals("mode batterie", labels.option("battery", "ctw3_mode"))
        assertEquals("intelligent", labels.option("smart", "w5_mode"))
        assertEquals("Mode batterie", labels.entity("ctw3_battery_mode", "Battery Mode"))
    }

    @Test
    fun `a step reads as its operation and how it ended`() {
        assertEquals(
            "Cleaning done",
            labels.step(StepLabel.parse("Auto cleaning completed"), "Auto cleaning completed"),
        )
        assertEquals("Deodorizing failed", labels.step(StepLabel.parse("Auto deodorizing failed"), "x"))
        assertEquals(
            "Cleaning failed: bin full",
            labels.step(StepLabel.parse("Manual cleaning stopped (bin full)"), "x"),
        )
    }

    @Test
    @Config(qualifiers = "fr")
    fun `a step is translated, with the reason when the app knows it`() {
        assertEquals("Désodorisation terminée", labels.step(StepLabel.parse("Auto deodorizing completed"), "x"))
        assertEquals("Désodorisation impossible", labels.step(StepLabel.parse("Auto deodorizing failed"), "x"))
        assertEquals(
            "Repas impossible : gamelle pas vide",
            labels.step(StepLabel.parse("Feeding skipped (bowl not empty)"), "x"),
        )
        assertEquals("Nettoyage impossible : interrompu", labels.step(StepLabel.parse("Auto cleaning terminated"), "x"))
        // A reason the app does not know stays in the server's words; so does an unknown operation.
        assertEquals("Nettoyage impossible : code 256", labels.step(StepLabel.parse("Cleaning failed - code 256"), "x"))
        assertEquals("Sparkling completed", labels.step(StepLabel.parse("Sparkling completed"), "Sparkling completed"))
        assertEquals("Error cleared", labels.step(null, "Error cleared"))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `every step operation and reason resolves to a non-blank text`() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        StepOperationLabels.forEach { (key, op) ->
            assertTrue(key, resources.getString(op.name).isNotBlank() && resources.getString(op.done).isNotBlank())
        }
        StepReasonLabels.forEach { (key, id) -> assertTrue(key, resources.getString(id).isNotBlank()) }
    }
}

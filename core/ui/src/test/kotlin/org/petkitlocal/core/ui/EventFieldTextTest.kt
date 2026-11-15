package org.petkitlocal.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An event's decoded fields, translated by key, their texts in the phone's formats (SPEC.md §7). */
@RunWith(RobolectricTestRunner::class)
class EventFieldTextTest {
    private val labels get() = ServerLabels(ApplicationProvider.getApplicationContext<Context>().resources)
    private val paris = ZoneId.of("Europe/Paris")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun `English keeps the server's labels and words`() {
        assertEquals("Pet weight", labels.eventField("petWeight", "Pet weight"))
        assertEquals("Yes", localizeEventFieldText("yes", labels, Locale.ENGLISH, paris))
        assertEquals("completed", localizeEventFieldText("completed", labels, Locale.ENGLISH, paris))
        assertEquals("4.21 kg", localizeEventFieldText("4.21 kg", labels, Locale.ENGLISH, paris))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `French is found by key, and the words by their text`() {
        assertEquals("Poids de l’animal", labels.eventField("petWeight", "Pet weight"))
        assertEquals("Poids de l’animal", labels.eventField("pet_weight", "Pet weight"))
        assertEquals("Origine du démarrage", labels.eventField("start_reason", "Start reason"))
        assertEquals("Oui", localizeEventFieldText("yes", labels, Locale.FRENCH, paris))
        assertEquals("terminé", localizeEventFieldText("completed", labels, Locale.FRENCH, paris))
        assertEquals("Manuel (appli)", localizeEventFieldText("Manual (app)", labels, Locale.FRENCH, paris))
        // A key or a word this app does not know: the server's text, never a blank.
        assertEquals("auto_clear", labels.eventField("auto_clear", "auto_clear"))
        assertEquals("id 3, score 412", localizeEventFieldText("id 3, score 412", labels, Locale.FRENCH, paris))
    }

    @Test
    fun `quantities take the locale's decimal sign and keep their precision`() {
        assertEquals("4,21 kg", localizeEventFieldText("4.21 kg", labels, Locale.FRENCH, paris))
        assertEquals("35 g", localizeEventFieldText("35 g", labels, Locale.FRENCH, paris))
        assertEquals("91\u202F%", localizeEventFieldText("91%", labels, Locale.FRENCH, paris))
        assertEquals("91%", localizeEventFieldText("91%", labels, Locale.ENGLISH, paris))
        assertEquals("12.5 s", localizeEventFieldText("12.5 s", labels, Locale.ENGLISH, paris))
    }

    @Test
    fun `a server time is shown in the phone's zone and format`() {
        // 10:03 in Paris (UTC+1) is 18:03 in Tokyo.
        assertEquals(
            "Nov 15, 2026, 6:03:12\u202FPM",
            localizeEventFieldText("2026-11-15 10:03:12 +0100", labels, Locale.US, tokyo),
        )
        assertEquals(
            "15 nov. 2026, 10:03:12",
            localizeEventFieldText("2026-11-15 10:03:12 +0100", labels, Locale.FRENCH, paris),
        )
    }

    @Test
    fun `the keys with a translation are told apart from the rest`() {
        assertTrue(labels.knowsEventField("time_in"))
        assertFalse(labels.knowsEventField("aesKey"))
    }
}

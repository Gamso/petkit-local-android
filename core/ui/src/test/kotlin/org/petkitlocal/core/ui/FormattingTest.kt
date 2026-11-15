package org.petkitlocal.core.ui

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormattingTest {
    @Test
    fun `weights use the locale's decimal separator`() {
        assertEquals("4.3 kg", formatWeightKg(4280.0, Locale.ENGLISH))
        assertEquals("4,3 kg", formatWeightKg(4280.0, Locale.FRENCH))
    }

    @Test
    fun `durations are m colon ss`() {
        assertEquals("0:00", formatDurationMinSec(-3))
        assertEquals("1:05", formatDurationMinSec(65))
        assertEquals("12:00", formatDurationMinSec(720))
    }

    @Test
    fun `times of day follow the locale, in the server's zone`() {
        val instant = Instant.parse("2026-11-15T14:30:00Z")
        val paris = ZoneId.of("Europe/Paris")

        assertEquals("15:30", formatTimeOfDay(instant, paris, Locale.FRENCH))
        assertEquals("3:30\u202fPM", formatTimeOfDay(instant, paris, Locale.US))
    }

    @Test
    fun `dates follow the locale`() {
        val day = LocalDate.of(2026, 11, 15)

        assertEquals("15 nov. 2026", formatDate(day, Locale.FRENCH))
        assertEquals("Nov 15, 2026", formatDate(day, Locale.US))
    }
}

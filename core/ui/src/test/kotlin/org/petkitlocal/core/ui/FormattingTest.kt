package org.petkitlocal.core.ui

import java.util.Locale
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
}

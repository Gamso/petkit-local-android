package org.petkitlocal.feature.device.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Today against yesterday under the Today cards' figures: shown with an arrow, read in words. */
@RunWith(RobolectricTestRunner::class)
class DayChangeFormatterTest {
    private val format get() = DayChangeFormatter(ApplicationProvider.getApplicationContext<Context>().resources)

    @Test
    fun `a count says how many more or fewer, the arrow giving the way`() {
        assertEquals(
            DayChangeText(DayTrend.Up, "2 since yesterday", "2 more than yesterday"),
            format.count(today = 4, yesterday = 2),
        )
        assertEquals(
            DayChangeText(DayTrend.Down, "3 since yesterday", "3 fewer than yesterday"),
            format.count(today = 1, yesterday = 4),
        )
    }

    @Test
    fun `a duration is shown as minutes and seconds and read in words`() {
        assertEquals(
            DayChangeText(DayTrend.Down, "0:25 since yesterday", "25 seconds shorter than yesterday"),
            format.duration(todaySec = 95, yesterdaySec = 120),
        )
        assertEquals(
            DayChangeText(DayTrend.Up, "1:05 since yesterday", "1 minute 5 seconds longer than yesterday"),
            format.duration(todaySec = 125, yesterdaySec = 60),
        )
        assertEquals("2 minutes", format.spokenDuration(120))
        assertEquals("1 second", format.spokenDuration(1))
        assertEquals("0 seconds", format.spokenDuration(0))
    }

    @Test
    fun `the same figure as yesterday has no arrow, and no yesterday says nothing`() {
        val same = DayChangeText(DayTrend.Same, "Same as yesterday", "Same as yesterday")
        assertEquals(same, format.count(today = 3, yesterday = 3))
        assertEquals(same, format.duration(todaySec = 40, yesterdaySec = 40))
        assertNull(format.count(today = 3, yesterday = null))
        assertNull(format.duration(todaySec = 40, yesterdaySec = null))
    }

    @Test
    @Config(qualifiers = "fr")
    fun `French says depuis hier, and de plus or de moins in words`() {
        assertEquals(
            DayChangeText(DayTrend.Up, "2 depuis hier", "2 de plus qu'hier"),
            format.count(today = 4, yesterday = 2),
        )
        assertEquals(
            DayChangeText(DayTrend.Down, "1 depuis hier", "1 de moins qu'hier"),
            format.count(today = 1, yesterday = 2),
        )
        assertEquals(
            DayChangeText(DayTrend.Down, "0:25 depuis hier", "25 secondes de moins qu'hier"),
            format.duration(todaySec = 95, yesterdaySec = 120),
        )
        assertEquals(
            DayChangeText(DayTrend.Up, "1:01 depuis hier", "1 minute 1 seconde de plus qu'hier"),
            format.duration(todaySec = 121, yesterdaySec = 60),
        )
        assertEquals(DayChangeText(DayTrend.Same, "Comme hier", "Comme hier"), format.count(2, 2))
        assertNull(format.duration(todaySec = 40, yesterdaySec = null))
    }
}

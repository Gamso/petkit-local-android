package org.petkitlocal.feature.device.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stem chart's geometry: the duration scale, where each stem stands, and close events. */
class DayChartLayoutTest {
    // 1450 px wide with a 5 px inset: 1440 px for 1440 minutes, so one minute is one pixel.
    private val frame = StemFrame(width = 1450f, top = 10f, bottom = 110f, inset = 5f, minGap = 6f)

    private fun dot(minute: Int, seconds: Int?, pet: Long? = 1) = DayDot(minute, seconds, pet)

    @Test
    fun `the scale is whole minutes, never below its minimum, and the same whatever is filtered`() {
        assertEquals(5, DayChartLayout.topMinutes(emptyList(), minMinutes = 5))
        assertEquals(5, DayChartLayout.topMinutes(listOf(dot(60, 95), dot(70, null)), minMinutes = 5))
        // 6 min 10 s needs a 7-minute axis.
        assertEquals(7, DayChartLayout.topMinutes(listOf(dot(60, 370), dot(70, 30)), minMinutes = 5))
        assertEquals(1, DayChartLayout.topMinutes(listOf(dot(60, 18)), minMinutes = 1))
    }

    @Test
    fun `a stem stands at its time and rises to its duration on the scale`() {
        val stems = DayChartLayout.stems(
            listOf(dot(720, 150), dot(0, 300), dot(1440, null, pet = null), dot(360, 900)),
            topMinutes = 5,
            frame = frame,
        )

        // In the order of the day, whatever the order given.
        assertEquals(listOf(5f, 365f, 725f, 1445f), stems.map { it.x })
        // 5 min is the top, 2:30 halfway, an unknown duration on the axis, 15 min capped at the top.
        assertEquals(listOf(10f, 10f, 60f, 110f), stems.map { it.top })
        assertEquals(listOf(1L, 1L, 1L, null), stems.map { it.petId })
    }

    @Test
    fun `events far enough apart keep their exact time`() {
        val stems = DayChartLayout.stems(listOf(dot(100, 60), dot(106, 60), dot(300, 60)), 5, frame)

        assertEquals(listOf(105f, 111f, 305f), stems.map { it.x })
    }

    @Test
    fun `events too close are spread around their own centre, a gap apart`() {
        // Two at the same minute, and a third one minute later: one cluster centred on 100.33.
        val stems = DayChartLayout.stems(listOf(dot(100, 60), dot(100, 120), dot(101, 30)), 5, frame)
        val xs = stems.map { it.x }

        assertEquals(3, xs.size)
        assertEquals(6f, xs[1] - xs[0], 0.001f)
        assertEquals(6f, xs[2] - xs[1], 0.001f)
        assertEquals((105f + 105f + 106f) / 3, xs.average().toFloat(), 0.001f)
        // Each keeps its own height.
        assertEquals(listOf(90f, 70f, 100f), stems.map { it.top })
    }

    @Test
    fun `a cluster that grows into its neighbour joins it`() {
        // 100 and 104 spread to 98 and 106 (in minutes), too close to 110: one cluster of three.
        val xs = DayChartLayout.stems(listOf(dot(100, 60), dot(104, 60), dot(110, 60)), 5, frame).map { it.x }

        assertEquals(6f, xs[1] - xs[0], 0.001f)
        assertEquals(6f, xs[2] - xs[1], 0.001f)
        assertEquals((105f + 109f + 115f) / 3, xs.average().toFloat(), 0.001f)
    }

    @Test
    fun `a cluster at either end of the day stays inside the chart`() {
        val start = DayChartLayout.stems(listOf(dot(0, 60), dot(0, 60), dot(0, 60)), 5, frame).map { it.x }
        val end = DayChartLayout.stems(listOf(dot(1440, 60), dot(1439, 60)), 5, frame).map { it.x }

        assertEquals(listOf(5f, 11f, 17f), start)
        assertEquals(listOf(1439f, 1445f), end)
        assertTrue((start + end).all { it in frame.inset..frame.width - frame.inset })
    }
}

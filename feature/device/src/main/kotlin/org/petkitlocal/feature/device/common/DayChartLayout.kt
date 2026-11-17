package org.petkitlocal.feature.device.common

import kotlin.math.ceil
import kotlin.math.max

/** Where one event stands on the day chart: its stem at [x], from the axis up to its dot at [top]. */
internal data class Stem(val x: Float, val top: Float, val petId: Long?)

/**
 * The chart's drawing area, in pixels: [width] across, the dots' centres between [top] (the
 * longest duration of the scale) and [bottom] (the axis), the first and last hour [inset] from the
 * edges so a dot there is whole. Two stems are never closer than [minGap].
 */
internal data class StemFrame(val width: Float, val top: Float, val bottom: Float, val inset: Float, val minGap: Float)

/** The geometry of the 24-hour stem chart (docs/DESIGN.md, DayStemChart), apart from drawing it. */
internal object DayChartLayout {

    /**
     * The top of the duration axis in whole minutes: at least [minMinutes], and enough for the
     * longest of [dots]. Every pet's dots count, so filtering on one does not rescale the chart.
     */
    fun topMinutes(dots: List<DayDot>, minMinutes: Int): Int =
        max(minMinutes, ceil((dots.maxOfOrNull { it.durationSec ?: 0 } ?: 0) / SECONDS_PER_MINUTE).toInt())

    /**
     * One stem per dot, in the order of the day. A stem's height is its duration on a scale of
     * [topMinutes]; an unknown duration stands on the axis and a longer one is capped at the top.
     *
     * Events closer in time than the frame's `minGap` would draw one stem over the other, so each
     * such cluster is spread around its own centre, `minGap` apart: a few minutes of offset, never
     * a hidden visit. A cluster at either end is kept inside the frame.
     */
    fun stems(dots: List<DayDot>, topMinutes: Int, frame: StemFrame): List<Stem> {
        val ordered = dots.sortedBy { it.minuteOfDay }
        val span = frame.width - 2 * frame.inset
        val xs = spread(
            ordered.map { frame.inset + span * it.minuteOfDay.coerceIn(0, MINUTES_PER_DAY) / MINUTES_PER_DAY },
            frame,
        )
        val height = frame.bottom - frame.top
        return ordered.mapIndexed { index, dot ->
            val fraction = ((dot.durationSec ?: 0) / SECONDS_PER_MINUTE / topMinutes).toFloat().coerceIn(0f, 1f)
            Stem(xs[index], frame.bottom - height * fraction, dot.petId)
        }
    }

    /** [wanted] positions, ascending, moved apart by clusters as [stems] says. */
    private fun spread(wanted: List<Float>, frame: StemFrame): List<Float> {
        val gap = frame.minGap
        val left = frame.inset
        val right = frame.width - frame.inset
        // Each cluster: how many positions it holds, and the sum of where they wanted to be.
        val clusters = ArrayDeque<Pair<Int, Float>>()

        fun centre(count: Int, sum: Float): Float {
            val half = (count - 1) * gap / 2
            val mean = sum / count
            return if (right - left < 2 * half) (left + right) / 2 else mean.coerceIn(left + half, right - half)
        }
        wanted.forEach { x ->
            var count = 1
            var sum = x
            while (clusters.isNotEmpty()) {
                val (lastCount, lastSum) = clusters.last()
                val lastEnd = centre(lastCount, lastSum) + (lastCount - 1) * gap / 2
                val start = centre(count, sum) - (count - 1) * gap / 2
                if (start - lastEnd >= gap) break
                clusters.removeLast()
                count += lastCount
                sum += lastSum
            }
            clusters.addLast(count to sum)
        }
        return clusters.flatMap { (count, sum) ->
            val first = centre(count, sum) - (count - 1) * gap / 2
            List(count) { first + it * gap }
        }
    }

    const val MINUTES_PER_DAY = 24 * 60
    private const val SECONDS_PER_MINUTE = 60.0
}

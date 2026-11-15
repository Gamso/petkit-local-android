package io.github.gamso.petkitlocal.mock

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * The mock's notion of "now": the real clock plus an offset the control API can advance, so a
 * test can age a session past its 90 days or a lockout past its end without waiting.
 */
class MockClock(
    val zone: ZoneId,
) {
    private val offsetMillis = AtomicLong(0)

    /** Unix epoch seconds, with milliseconds. */
    fun now(): Double = (System.currentTimeMillis() + offsetMillis.get()) / 1000.0

    fun advance(seconds: Double) {
        offsetMillis.addAndGet((seconds * 1000).toLong())
    }

    fun reset() = offsetMillis.set(0)

    val offsetSeconds: Double get() = offsetMillis.get() / 1000.0

    fun today(): LocalDate = localDateTime(now()).toLocalDate()

    fun localDateTime(epochSeconds: Double): ZonedDateTime = Instant.ofEpochMilli((epochSeconds * 1000).toLong()).atZone(zone)

    /** `[start, end)` of a local day. The end is the next midnight, never start + 86400 (DST). */
    fun dayBounds(day: LocalDate): Pair<Double, Double> =
        day.atStartOfDay(zone).toEpochSecond().toDouble() to
            day
                .plusDays(1)
                .atStartOfDay(zone)
                .toEpochSecond()
                .toDouble()
}

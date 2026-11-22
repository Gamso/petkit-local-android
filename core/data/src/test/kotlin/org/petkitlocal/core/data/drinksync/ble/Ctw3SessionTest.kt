package org.petkitlocal.core.data.drinksync.ble

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure

/** PetKit's session with a CTW3, replayed against [ScriptedFountain]. */
@Suppress("MagicNumber")
@OptIn(ExperimentalCoroutinesApi::class) // currentTime
class Ctw3SessionTest {
    /** 2026-11-15 10:00 in Paris (UTC+1). */
    private val clock = Clock.fixed(Instant.parse("2026-11-15T09:00:00Z"), ZoneId.of("Europe/Paris"))
    private val secret = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9a.toByte(), 0xbc.toByte(), 0xde.toByte(), 0xf0.toByte())
    private val first = DrinkRecord(Instant.parse("2026-11-14T20:00:00Z").epochSecond - Ctw3Payloads.CLOCK_EPOCH, 12)
    private val second = DrinkRecord(Instant.parse("2026-11-15T08:00:00Z").epochSecond - Ctw3Payloads.CLOCK_EPOCH, 30)

    private fun session(fountain: ScriptedFountain) = Ctw3Session(fountain, secret, clock)

    @Test
    fun `runs the app's whole session and reads a stream of two records`() = runTest {
        val fountain = ScriptedFountain(records = listOf(first, second), mtu = 247)
        var announced = -1

        val records = session(fountain).run { announced = it }

        assertEquals(listOf(first, second), records)
        assertEquals(2, announced)
        val requests = fountain.written.filter { it.type == PetkitFrame.REQUEST }
        assertEquals(listOf(213, 86, 84, 200, 201, 66, 210, 211, 215, 216, 212, 80), requests.map { it.cmd })
        // One request in flight, numbered from 0 as the app's counter.
        assertEquals((0..11).toList(), requests.map { it.seq })
        assertArrayEquals(secret, requests.single { it.cmd == 86 }.data)
        // 00, seconds since 2000 (UTC), Paris's +1 h + 12.
        val seconds = Instant.parse("2026-11-15T09:00:00Z").epochSecond - Ctw3Payloads.CLOCK_EPOCH
        assertArrayEquals(
            byteArrayOf(
                0,
                (seconds shr 24).toByte(),
                (seconds shr 16).toByte(),
                (seconds shr 8).toByte(),
                seconds.toByte(),
                13,
            ),
            requests.single { it.cmd == 84 }.data,
        )
        assertArrayEquals(byteArrayOf(0, 0, 0, 32, 0, 0, 0, 247.toByte()), requests.single { it.cmd == 80 }.data)
    }

    @Test
    fun `answers 67 with the bitmap, 69 with an empty type-2 69, and acks the pushed 230`() = runTest {
        val fountain = ScriptedFountain(records = listOf(first, second))

        session(fountain).run {}

        val answers = fountain.written.filter { it.type == PetkitFrame.RESPONSE }
        val check = answers.single { it.cmd == 67 }
        assertArrayEquals(byteArrayOf(0xC0.toByte(), 0, 0, 0), check.data)
        val end = answers.single { it.cmd == 69 }
        assertEquals(0, end.data.size)
        // Both carry the sequence of the request they answer (the fountain's own, from 100).
        assertTrue(check.seq >= 100 && end.seq == check.seq + 1)
        val ack = answers.single { it.cmd == 230 }
        assertEquals(0, ack.seq)
        assertArrayEquals(byteArrayOf(1), ack.data)
        assertArrayEquals(
            byteArrayOf(0xFA.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xE6.toByte(), 2, 0, 1, 0, 1, 0xFB.toByte()),
            PetkitFrames.encode(230, 2, 0, byteArrayOf(1)),
        )
    }

    @Test
    fun `a packet missing from a batch is asked for again by the bitmap`() = runTest {
        val fountain = ScriptedFountain(records = listOf(first, second), dropOnce = setOf(1))

        val records = session(fountain).run {}

        assertEquals(listOf(first, second), records)
        val checks = fountain.written.filter { it.cmd == 67 }
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0, 0, 0), checks[0].data)
        assertArrayEquals(byteArrayOf(0xC0.toByte(), 0, 0, 0), checks[1].data)
    }

    @Test
    fun `a refused security check stops the session there`() = runTest {
        val fountain = ScriptedFountain(replies = mapOf(86 to byteArrayOf(0)))

        val failure = assertThrows<BleLinkException> { session(fountain).run {} }

        assertEquals(FountainLinkFailure.SecretRefused, failure.failure)
        assertEquals(listOf(213, 86), fountain.written.filter { it.type == PetkitFrame.REQUEST }.map { it.cmd })
    }

    @Test
    fun `a refused clock and a refused stream each say so`() = runTest {
        val clockRefused = ScriptedFountain(replies = mapOf(84 to byteArrayOf(2)))
        assertEquals(
            FountainLinkFailure.ClockRefused,
            assertThrows<BleLinkException> {
                session(clockRefused).run {}
            }.failure,
        )

        val streamRefused = ScriptedFountain(records = listOf(first), replies = mapOf(80 to byteArrayOf(0)))
        assertEquals(
            FountainLinkFailure.StreamRefused,
            assertThrows<BleLinkException> {
                session(streamRefused).run {}
            }.failure,
        )
    }

    @Test
    fun `a request unanswered is sent again with its sequence, then times out`() = runTest {
        val fountain = ScriptedFountain(silent = setOf(201))

        val failure = assertThrows<BleLinkException> { session(fountain).run {} }

        assertEquals(FountainLinkFailure.Timeout, failure.failure)
        val sent = fountain.written.filter { it.cmd == 201 }
        assertEquals(3, sent.size)
        assertEquals(setOf(4), sent.map { it.seq }.toSet())
        assertEquals(9_000L, currentTime)
    }

    @Test
    fun `a history of zero bytes is done with nothing, and no stream is asked for`() = runTest {
        val fountain = ScriptedFountain(records = emptyList())
        var announced = -1

        assertEquals(emptyList<DrinkRecord>(), session(fountain).run { announced = it })

        assertEquals(-1, announced)
        assertTrue(fountain.written.none { it.cmd == 80 })
    }

    @Test
    fun `notifications cut across frames, or two frames in one, are reassembled`() = runTest {
        val cut = ScriptedFountain(records = listOf(first, second), recordsPerPacket = 2, chunk = 5)
        assertEquals(listOf(first, second), session(cut).run {})

        val glued = ScriptedFountain(records = listOf(first, second), glue = true, chunk = 7)
        assertEquals(listOf(first, second), session(glued).run {})
        assertTrue(glued.written.any { it.cmd == 230 && it.type == PetkitFrame.RESPONSE })
    }

    @Test
    fun `a link that breaks mid-session is a lost connection`() = runTest {
        val fountain = ScriptedFountain(silent = setOf(200))
        fountain.notifications.close(BleLinkException(FountainLinkFailure.ConnectionLost, "gone"))

        val failure = assertThrows<BleLinkException> { session(fountain).run {} }

        assertEquals(FountainLinkFailure.ConnectionLost, failure.failure)
    }
}

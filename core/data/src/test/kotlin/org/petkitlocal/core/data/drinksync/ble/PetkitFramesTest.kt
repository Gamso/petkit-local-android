package org.petkitlocal.core.data.drinksync.ble

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure
import org.petkitlocal.core.domain.drinksync.FountainLinkProgress
import org.petkitlocal.core.domain.drinksync.FountainReadResult
import org.petkitlocal.core.domain.drinksync.FountainSecret

@Suppress("MagicNumber")
class PetkitFramesTest {
    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `a command frame is the app's bytes, its length little-endian`() {
        // An empty 212, sequence 2: petkit-local's relay sent exactly this (#140).
        assertArrayEquals(hex("fafcfdd401020000fb"), PetkitFrames.encode(212, 1, 2))
        assertArrayEquals(hex("fafcfd500103080000000020000000f7fb"), PetkitFrames.encode(80, 1, 3, Ctw3Payloads.streamSetting(32, 247)))
    }

    @Test
    fun `the assembler reads both headers, skips noise and a trailer, and waits for a cut frame`() {
        val assembler = PetkitFrames.Assembler()
        // Noise, then a 210 reply cut in two.
        assertTrue(assembler.feed(hex("0102fafcfdd2")).isEmpty())
        val reply = assembler.feed(hex("02010200aabbfb")).single()
        assertEquals(210, reply.cmd)
        assertEquals(2, reply.type)
        assertEquals(1, reply.seq)
        assertArrayEquals(hex("aabb"), reply.data)

        // A stream packet (no trailer) and a command frame in one notification.
        val two = assembler.feed(hex("fafcfe4403000206000000000a000c") + hex("fafcfd4301070000fb"))
        assertEquals(2, two.size)
        assertTrue(two[0].isStream)
        assertEquals(68, two[0].cmd)
        assertEquals(0, two[0].seq)
        assertEquals(2, two[0].total)
        assertEquals(listOf(DrinkRecord(10, 12)), Ctw3Payloads.records(two[0].data))
        assertFalse(two[1].isStream)
        assertEquals(67, two[1].cmd)
        assertEquals(7, two[1].seq)
    }

    @Test
    fun `a header announcing an impossible length is skipped`() {
        val assembler = PetkitFrames.Assembler()
        val frames = assembler.feed(hex("fafcfd010100ffff") + hex("fafcfdd4020500000001fb".substring(0, 16)) + hex("fb"))
        assertEquals(listOf(212), frames.map { it.cmd })
    }

    @Test
    fun `payloads are built and read as the app does`() {
        // 2000-01-01 + 1000 s, at UTC+5:45 (rounded to 6 h): zone byte 18.
        assertArrayEquals(hex("00000003e812"), Ctw3Payloads.time(946_684_800L + 1000, (5 * 60 + 45) * 60))
        assertArrayEquals(hex("00000000000c"), Ctw3Payloads.time(0, 0))

        val (bitmap, complete) = Ctw3Payloads.streamCheck(setOf(0, 2), total = 3)
        assertArrayEquals(hex("a0000000"), bitmap)
        assertFalse(complete)
        assertTrue(Ctw3Payloads.streamCheck((0 until 32).toSet(), 32).second)

        assertEquals(18L, Ctw3Payloads.historyBytes(hex("0000000012")))
        assertNull(Ctw3Payloads.historyBytes(hex("00000012")))

        // A negative stay reads as itself (the server skips it); a part-record is dropped.
        assertEquals(listOf(DrinkRecord(0xFFFFFFFFL, -1)), Ctw3Payloads.records(hex("ffffffffffff0102")))
    }

    @Test
    fun `reading the history reports each stage, closes the link, and says why it failed`() = runTest {
        val fountain = ScriptedFountain(records = listOf(DrinkRecord(1, 5)))
        val transport = ScriptedTransport(fountain)
        val bluetooth = BleFountainBluetooth(transport, java.time.Clock.systemUTC())
        val progress = mutableListOf<FountainLinkProgress>()

        val result = bluetooth.readHistory("aa:bb:cc:dd:ee:ff", FountainSecret.fromHex("0000000000000001")!!) {
            progress += it
        }

        assertEquals(FountainReadResult.Success(listOf(DrinkRecord(1, 5))), result)
        assertEquals(
            listOf(FountainLinkProgress.Searching, FountainLinkProgress.Connected, FountainLinkProgress.Reading(1)),
            progress,
        )
        assertTrue(fountain.closed)
        assertEquals("aa:bb:cc:dd:ee:ff", transport.connectedTo)

        val away = BleFountainBluetooth(ScriptedTransport(null, FountainLinkFailure.NotFound), java.time.Clock.systemUTC())
        assertEquals(
            FountainReadResult.Failure(FountainLinkFailure.NotFound),
            away.readHistory("aa:bb:cc:dd:ee:ff", FountainSecret.fromHex("0000000000000001")!!) {},
        )

        val refusing = ScriptedFountain(replies = mapOf(86 to byteArrayOf(0)))
        val refused = BleFountainBluetooth(ScriptedTransport(refusing), java.time.Clock.systemUTC())
            .readHistory("aa:bb:cc:dd:ee:ff", FountainSecret.fromHex("0000000000000001")!!) {}
        assertEquals(FountainReadResult.Failure(FountainLinkFailure.SecretRefused), refused)
        assertTrue(refusing.closed)
    }
}

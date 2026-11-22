package org.petkitlocal.core.data.drinksync.ble

import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure

/** How long the session waits, and how often it asks again. */
data class Ctw3Timing(
    /** For the reply to one request, before sending it again. */
    val replyTimeoutMs: Long = 3_000,
    /** Sends of one request before the fountain is taken to have stopped answering. */
    val attempts: Int = 3,
    /** How long the history stream may go quiet. */
    val streamIdleMs: Long = 15_000,
)

/**
 * PetKit's session with a CTW3 over a direct link, as its app 13.8.1 runs it
 * (`PetkitBleClient.onReceiveCustomData`, `CTW3BleClient.onReceiveCustomData`, `BaseDataConvertor`;
 * Gamso/petkit-local#140, petkit-local's `codes.CTW3_SESSION_STEPS`):
 *
 *     213 -> 86 (secret) -> 84 (clock) -> 200 -> 201 -> 66 -> 210 -> 211 -> 215 -> 216 -> 212
 *         -> 80 [u32 32][u32 MTU] -> the stream: 68/82 packets, 67 answered with the bitmap,
 *            69 answered with an empty type-2 69
 *
 * One request in flight at a time, its reply matched by cmd and sequence
 * (`PetkitBleClientImpl.parseReceivedPetkitBleMsg`); a request unanswered is sent again with the same
 * sequence, as the app's dispatcher resends. A reply to 86, 84 or 80 whose first byte is not `01`
 * is a refusal (`receive CMD_PETKIT_BLE_V/T/P fail`), and the session stops there. The 212's reply
 * says how many bytes of history wait (data bytes 1-4); none means nothing new. Every 230 the
 * fountain pushes is answered with cmd 230, type 2, data `01` (`CTW3DataConvertor.getDeviceUpdateResponse`).
 *
 * Throws [BleLinkException] with the failure to say; the caller closes the link.
 */
class Ctw3Session(
    private val link: BleLink,
    private val secret: ByteArray,
    private val clock: Clock,
    private val timing: Ctw3Timing = Ctw3Timing(),
) {
    private val frames = Channel<PetkitFrame>(Channel.UNLIMITED)
    private var sequence = 0

    /** Runs the session; [onReading] hears how many drinks the fountain announced. */
    suspend fun run(onReading: suspend (Int) -> Unit): List<DrinkRecord> = coroutineScope {
        val reader = launch { readNotifications() }
        try {
            for (step in STEPS) {
                val reply = request(step, payload(step))
                refusal(step, reply)?.let { fail(it, "the fountain refused $step") }
            }
            val history = request(Ctw3Cmd.SYNC_HISTORY)
            val bytes = Ctw3Payloads.historyBytes(history) ?: fail(FountainLinkFailure.ConnectionLost, "short 212 reply")
            if (bytes == 0L) return@coroutineScope emptyList()
            onReading((bytes / Ctw3Payloads.RECORD_LEN).toInt())
            val setting = request(Ctw3Cmd.STREAM_SETTING, Ctw3Payloads.streamSetting(Ctw3Payloads.STREAM_BATCH, link.mtu))
            if (setting.firstOrNull()?.toInt() != OK) fail(FountainLinkFailure.StreamRefused, "the fountain refused 80")
            stream(bytes)
        } finally {
            reader.cancel()
        }
    }

    /** Hands every frame to the session, answering the pushed 230s on the way. */
    private suspend fun readNotifications() {
        val assembler = PetkitFrames.Assembler()
        try {
            for (bytes in link.notifications) {
                for (frame in assembler.feed(bytes)) {
                    if (!frame.isStream && frame.cmd == Ctw3Cmd.DEVICE_STATUS && frame.type != PetkitFrame.RESPONSE) {
                        link.write(PetkitFrames.encode(Ctw3Cmd.DEVICE_STATUS, PetkitFrame.RESPONSE, 0, byteArrayOf(OK.toByte())))
                    } else {
                        frames.send(frame)
                    }
                }
            }
            frames.close(BleLinkException(FountainLinkFailure.ConnectionLost, "the link closed"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: BleLinkException) {
            frames.close(e)
        }
    }

    /** Sends one request and returns its reply's data, sending it again while unanswered. */
    private suspend fun request(cmd: Int, data: ByteArray = ByteArray(0)): ByteArray {
        val seq = sequence
        sequence = (sequence + 1) % SEQUENCES
        val frame = PetkitFrames.encode(cmd, PetkitFrame.REQUEST, seq, data)
        repeat(timing.attempts) {
            link.write(frame)
            val reply = withTimeoutOrNull(timing.replyTimeoutMs) {
                var next = receive()
                while (next.isStream || next.cmd != cmd || next.seq != seq) next = receive()
                next
            }
            if (reply != null) return reply.data
        }
        fail(FountainLinkFailure.Timeout, "no answer to $cmd")
    }

    /** The stream after a 80 accepted: packets in batches, each batch checked, until the 69. */
    private suspend fun stream(announced: Long): List<DrinkRecord> {
        val records = mutableListOf<DrinkRecord>()
        val batch = sortedMapOf<Int, ByteArray>()
        var batchTotal = 0
        var received = 0L
        fun commit() {
            batch.values.forEach { records += Ctw3Payloads.records(it) }
            received += batch.values.sumOf { it.size }
            batch.clear()
        }
        while (true) {
            val frame = withTimeoutOrNull(timing.streamIdleMs) { receive() }
            if (frame == null) {
                commit()
                // Everything announced came, only the end was lost: the records are whole.
                if (received >= announced) return records
                fail(FountainLinkFailure.Timeout, "the stream stopped")
            }
            when {
                frame.isStream && frame.cmd in DATA -> {
                    if (batch.isEmpty()) batchTotal = frame.total
                    batch[frame.seq] = frame.data
                }

                !frame.isStream && frame.cmd == Ctw3Cmd.STREAM_CHECK -> {
                    val total = if (batch.isEmpty()) Ctw3Payloads.STREAM_BATCH else batchTotal
                    val (bitmap, complete) = Ctw3Payloads.streamCheck(batch.keys, total)
                    link.write(PetkitFrames.encode(Ctw3Cmd.STREAM_CHECK, PetkitFrame.RESPONSE, frame.seq, bitmap))
                    if (complete) commit()
                }

                !frame.isStream && frame.cmd == Ctw3Cmd.STREAM_END -> {
                    link.write(PetkitFrames.encode(Ctw3Cmd.STREAM_END, PetkitFrame.RESPONSE, frame.seq))
                    commit()
                    return records
                }
            }
        }
    }

    private suspend fun receive(): PetkitFrame = try {
        frames.receive()
    } catch (e: ClosedReceiveChannelException) {
        throw BleLinkException(FountainLinkFailure.ConnectionLost, "the link closed", e)
    }

    private fun payload(cmd: Int): ByteArray = when (cmd) {
        Ctw3Cmd.SECURITY_CHECK -> secret.copyOf()
        Ctw3Cmd.SYNC_TIME -> {
            val now = clock.instant()
            Ctw3Payloads.time(now.epochSecond, clock.zone.rules.getOffset(now).totalSeconds)
        }

        else -> ByteArray(0)
    }

    private fun refusal(cmd: Int, reply: ByteArray): FountainLinkFailure? {
        val refused = reply.firstOrNull()?.toInt() != OK
        return when {
            !refused -> null
            cmd == Ctw3Cmd.SECURITY_CHECK -> FountainLinkFailure.SecretRefused
            cmd == Ctw3Cmd.SYNC_TIME -> FountainLinkFailure.ClockRefused
            else -> null
        }
    }

    private fun fail(failure: FountainLinkFailure, message: String): Nothing = throw BleLinkException(failure, message)

    companion object {
        /** Everything before the 212, in the app's order. */
        val STEPS = listOf(
            Ctw3Cmd.GET_DEVICE_ID,
            Ctw3Cmd.SECURITY_CHECK,
            Ctw3Cmd.SYNC_TIME,
            Ctw3Cmd.GET_VERSION,
            Ctw3Cmd.GET_DEVICE_LOG,
            Ctw3Cmd.SYNC_BATTERY,
            Ctw3Cmd.GET_STATE,
            Ctw3Cmd.GET_CONFIG,
            Ctw3Cmd.GET_LIGHT_SCHEDULE,
            Ctw3Cmd.GET_DND_SCHEDULE,
        )

        private val DATA = setOf(Ctw3Cmd.STREAM_DATA, Ctw3Cmd.STREAM_DATA_82)
        private const val OK = 1

        /** `PetkitBleClientImpl.generateSendSequence`: a counter mod 256. */
        private const val SEQUENCES = 256
    }
}

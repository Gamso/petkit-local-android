package org.petkitlocal.core.data.drinksync.ble

import java.util.UUID
import org.petkitlocal.core.domain.drinksync.DrinkRecord

/*
 * The frames PetKit's Bluetooth fountains speak, as PetKit's app 13.8.1 builds and parses them
 * (Gamso/petkit-local#140; the same frames petkit-local's `devices/ble/framing.py` relays).
 *
 * A command frame:  FA FC FD | cmd | type | seq | len_lo | len_hi | data | FB
 *   (`PetkitBleMsg.toRawDataBytes`: the length is 16-bit LITTLE-endian, then the trailer).
 * A stream packet:  FA FC FE | cmd | type | index | total | len_lo | len_hi | data
 *   (`PetkitBleMsg.<init>([BI)` @0x007f-0x00bb: an index within its batch and the batch's size
 *   where a command has its sequence; the data is read by its length).
 *
 * `type` is 1 a request, 2 a response, 3 a request wanting no answer (the stream's packets).
 */

/** The PetKit service and its two characteristics (`PetkitBleParameter.<clinit>`, app 13.8.1). */
object PetkitGatt {
    /** `PetkitBleParameter.SERVICE_UUID`. */
    val SERVICE: UUID = UUID.fromString("0000aaa0-0000-1000-8000-00805f9b34fb")

    /** `PetkitBleParameter.DATA_UUID`: what the app writes to (`PetkitBleClientImpl.mWriteChar`). */
    val WRITE: UUID = UUID.fromString("0000aaa1-0000-1000-8000-00805f9b34fb")

    /** `PetkitBleParameter.CONTROL_UUID`: what notifies (`mNotifyChar`, notifications enabled on it). */
    val NOTIFY: UUID = UUID.fromString("0000aaa2-0000-1000-8000-00805f9b34fb")

    /** The standard Client Characteristic Configuration descriptor (`BLEConsts.CLIENT_CHARACTERISTIC_CONFIG`). */
    val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** What the app asks for once notifications are on (`GattCallback.onDescriptorWrite`: `requestMtu(255)`). */
    const val REQUESTED_MTU = 255

    /** The ATT default, when the phone did not get a larger one. */
    const val DEFAULT_MTU = 23
}

/** The commands of a CTW3 session, the numbers in both directions (graded in petkit-local's `codes.BLE_OPCODES`). */
object Ctw3Cmd {
    const val SYNC_BATTERY = 66
    const val STREAM_CHECK = 67
    const val STREAM_DATA = 68
    const val STREAM_END = 69
    const val STREAM_SETTING = 80
    const val STREAM_DATA_82 = 82
    const val SYNC_TIME = 84
    const val SECURITY_CHECK = 86
    const val GET_VERSION = 200
    const val GET_DEVICE_LOG = 201
    const val GET_STATE = 210
    const val GET_CONFIG = 211
    const val SYNC_HISTORY = 212
    const val GET_DEVICE_ID = 213
    const val GET_LIGHT_SCHEDULE = 215
    const val GET_DND_SCHEDULE = 216
    const val DEVICE_STATUS = 230
}

/** One frame, either kind. For a stream packet [seq] is its index in the batch and [total] the batch's size. */
class PetkitFrame(
    val cmd: Int,
    val type: Int,
    val seq: Int,
    val data: ByteArray,
    val isStream: Boolean = false,
    val total: Int = 0,
) {
    override fun toString(): String =
        "PetkitFrame(cmd=$cmd, type=$type, ${if (isStream) "index" else "seq"}=$seq, ${data.size} bytes)"

    companion object {
        const val REQUEST = 1
        const val RESPONSE = 2
        const val NO_RESPONSE = 3
    }
}

@Suppress("MagicNumber") // Byte offsets of the frame layout above.
object PetkitFrames {
    private val COMMAND_HEADER = byteArrayOf(0xFA.toByte(), 0xFC.toByte(), 0xFD.toByte())
    private const val COMMAND_MARK = 0xFD
    private const val STREAM_MARK = 0xFE
    private const val TRAILER = 0xFB
    private const val COMMAND_HEADER_LEN = 8
    private const val STREAM_HEADER_LEN = 9

    /** Longer than anything a fountain sends: a length past it is noise, not a frame to wait for. */
    const val MAX_DATA = 4096

    /** A command frame, as the app writes it. */
    fun encode(cmd: Int, type: Int, seq: Int, data: ByteArray = ByteArray(0)): ByteArray {
        require(data.size <= MAX_DATA) { "frame data too long: ${data.size}" }
        val out = ByteArray(COMMAND_HEADER_LEN + data.size + 1)
        COMMAND_HEADER.copyInto(out)
        out[3] = cmd.toByte()
        out[4] = type.toByte()
        out[5] = seq.toByte()
        out[6] = (data.size and 0xFF).toByte()
        out[7] = (data.size shr 8 and 0xFF).toByte()
        data.copyInto(out, COMMAND_HEADER_LEN)
        out[out.size - 1] = TRAILER.toByte()
        return out
    }

    /**
     * Gathers the bytes the fountain notifies into whole frames. A notification is normally one
     * frame, but nothing in GATT promises it: one may be cut across notifications (an MTU smaller
     * than the frame), or two may share one. Bytes before a header are skipped, as is a trailer.
     */
    class Assembler {
        private var buffer = ByteArray(0)

        /** Adds [bytes] and returns every frame now complete, in order. */
        fun feed(bytes: ByteArray): List<PetkitFrame> {
            buffer += bytes
            val frames = mutableListOf<PetkitFrame>()
            while (true) {
                val start = headerStart()
                if (start < 0) {
                    // Keep a last FA (FC) that may begin the next header.
                    buffer = buffer.copyOfRange(partialHeaderStart(), buffer.size)
                    return frames
                }
                buffer = buffer.copyOfRange(start, buffer.size)
                when (val head = parseHead()) {
                    Head.Incomplete -> return frames
                    Head.NotAHeader -> buffer = buffer.copyOfRange(1, buffer.size)
                    is Head.Frame -> frames += head.frame
                }
            }
        }

        private sealed interface Head {
            data object Incomplete : Head

            /** A header's bytes announcing a length no fountain sends: noise, skipped. */
            data object NotAHeader : Head

            class Frame(val frame: PetkitFrame) : Head
        }

        /** The frame at the start of the buffer, consumed when whole. */
        private fun parseHead(): Head {
            val stream = u8(2) == STREAM_MARK
            val headerLen = if (stream) STREAM_HEADER_LEN else COMMAND_HEADER_LEN
            if (buffer.size < headerLen) return Head.Incomplete
            val lengthAt = headerLen - 2
            val length = u8(lengthAt) or (u8(lengthAt + 1) shl 8)
            if (length > MAX_DATA) return Head.NotAHeader
            if (buffer.size < headerLen + length) return Head.Incomplete
            val data = buffer.copyOfRange(headerLen, headerLen + length)
            var consumed = headerLen + length
            if (buffer.size > consumed && u8(consumed) == TRAILER) consumed++
            val frame = if (stream) {
                PetkitFrame(u8(3), u8(4), u8(5), data, isStream = true, total = u8(6))
            } else {
                PetkitFrame(u8(3), u8(4), u8(5), data)
            }
            buffer = buffer.copyOfRange(consumed, buffer.size)
            return Head.Frame(frame)
        }

        private fun headerStart(): Int {
            for (i in 0..buffer.size - 3) {
                if (buffer[i] == COMMAND_HEADER[0] && buffer[i + 1] == COMMAND_HEADER[1] &&
                    (u8(i + 2) == COMMAND_MARK || u8(i + 2) == STREAM_MARK)
                ) {
                    return i
                }
            }
            return -1
        }

        private fun partialHeaderStart(): Int {
            val n = buffer.size
            return when {
                n >= 2 && buffer[n - 2] == COMMAND_HEADER[0] && buffer[n - 1] == COMMAND_HEADER[1] -> n - 2
                n >= 1 && buffer[n - 1] == COMMAND_HEADER[0] -> n - 1
                else -> n
            }
        }

        private fun u8(i: Int): Int = buffer[i].toInt() and 0xFF
    }
}

/** The payloads a CTW3 session builds, as PetKit's app builds them. */
@Suppress("MagicNumber") // Byte layouts, cited per function.
object Ctw3Payloads {
    /** The fountain's clock counts from 2000-01-01 UTC. */
    const val CLOCK_EPOCH = 946_684_800L

    /** One record: `u32 workTime`, `s16 stayTime`, big-endian. */
    const val RECORD_LEN = 6

    /** Packets per batch, the app's constant (`CTW3BleClient.onReceiveCustomData` @0x0070, `setMaxPackageCount(32)`). */
    const val STREAM_BATCH = 32

    /**
     * The 84's six bytes: `00`, the seconds since 2000 (UTC: `CTW3BleClient.isAqDevice` is false)
     * as a big-endian u32, then the zone's offset in whole hours + 12
     * (`BaseDataConvertor.securityCheckAndSyncData` @0x0035-0x0077).
     */
    fun time(nowEpochSecond: Long, offsetSeconds: Int): ByteArray {
        val seconds = (nowEpochSecond - CLOCK_EPOCH).coerceAtLeast(0) and 0xFFFF_FFFFL
        val zone = Math.round(offsetSeconds / SECONDS_PER_HOUR) + 12
        return byteArrayOf(0) + u32(seconds) + byteArrayOf(zone.toByte())
    }

    /** The 80's two big-endian u32: packets per batch, then the link's MTU (`BaseDataConvertor.setStreamSetting`). */
    fun streamSetting(batch: Int, mtu: Int): ByteArray = u32(batch.toLong()) + u32(mtu.toLong())

    /**
     * The answer to a 67: bit 31-i set for each packet index i (0-31) held, a big-endian u32, and
     * whether no index below [total] is missing (`BaseDataConvertor.checkStreamData` @0x0000-0x0049).
     */
    fun streamCheck(held: Set<Int>, total: Int): Pair<ByteArray, Boolean> {
        var bitmap = 0L
        var missing = false
        for (i in 0 until STREAM_BATCH) {
            if (i in held) {
                bitmap = bitmap or (1L shl (31 - i))
            } else if (i < total) {
                missing = true
            }
        }
        return u32(bitmap) to !missing
    }

    /** The 212 reply's byte count: data bytes 1-4, big-endian (`ByteUtil.bytes2Int` -> `maxDataLength`); null if short. */
    fun historyBytes(data: ByteArray): Long? {
        if (data.size < 5) return null
        return (1..4).fold(0L) { acc, i -> (acc shl 8) or (data[i].toLong() and 0xFF) }
    }

    /** The records of one packet; a trailing part-record is dropped, as the app's `len / 6` drops it. */
    fun records(data: ByteArray): List<DrinkRecord> = (0 until data.size / RECORD_LEN).map { n ->
        val at = n * RECORD_LEN
        val work = (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (data[at + i].toLong() and 0xFF) }
        val stay = ((data[at + 4].toInt() shl 8) or (data[at + 5].toInt() and 0xFF)).toShort().toInt()
        DrinkRecord(work, stay)
    }

    private fun u32(value: Long): ByteArray =
        byteArrayOf((value shr 24).toByte(), (value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    private const val SECONDS_PER_HOUR = 3600.0
}

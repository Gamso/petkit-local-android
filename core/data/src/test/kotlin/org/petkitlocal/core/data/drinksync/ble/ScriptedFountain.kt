package org.petkitlocal.core.data.drinksync.ble

import kotlinx.coroutines.channels.Channel
import org.petkitlocal.core.domain.drinksync.BluetoothAvailability
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure

/**
 * A CTW3 as PetKit's app meets it over a direct link, scripted: it answers each request with the
 * same cmd and sequence, pushes a 230 after the first reply, and once its 80 is accepted streams
 * [records] in 68 packets of [recordsPerPacket], asks 67, and ends with 69. It records every frame
 * the session writes. [chunk] cuts what it notifies into pieces of that many bytes, and [glue]
 * sends a reply and the next push in one notification, to exercise reassembly.
 */
@Suppress("MagicNumber")
class ScriptedFountain(
    private val records: List<DrinkRecord> = emptyList(),
    private val recordsPerPacket: Int = 1,
    override val mtu: Int = 247,
    /** Data of the reply to each cmd; 01 for anything not listed. */
    replies: Map<Int, ByteArray> = emptyMap(),
    /** Requests it never answers. */
    private val silent: Set<Int> = emptySet(),
    /** Packet indexes it leaves out the first time, sending them only once a 67 answer asks. */
    private val dropOnce: Set<Int> = emptySet(),
    private val chunk: Int = Int.MAX_VALUE,
    private val glue: Boolean = false,
) : BleLink {
    override val notifications = Channel<ByteArray>(Channel.UNLIMITED)

    /** Every frame the session wrote, in order. */
    val written = mutableListOf<PetkitFrame>()
    var closed = false
        private set

    private val replies: Map<Int, ByteArray> = mapOf(
        Ctw3Cmd.SYNC_HISTORY to historyReply(records.size * Ctw3Payloads.RECORD_LEN),
    ) + replies
    private val assembler = PetkitFrames.Assembler()
    private var pushedStatus = false
    private var pushSeq = 100
    private val packets: List<ByteArray> = records.chunked(recordsPerPacket).map { batch ->
        batch.fold(ByteArray(0)) { acc, r -> acc + record(r) }
    }

    override suspend fun write(bytes: ByteArray) {
        assembler.feed(bytes).forEach { frame ->
            written += frame
            if (frame.type == PetkitFrame.REQUEST) answer(frame) else afterAnswer(frame)
        }
    }

    override fun close() {
        closed = true
        notifications.close()
    }

    private fun answer(request: PetkitFrame) {
        if (request.cmd in silent) return
        val reply = PetkitFrames.encode(request.cmd, PetkitFrame.RESPONSE, request.seq, replies[request.cmd] ?: OK)
        val status = if (!pushedStatus) {
            pushedStatus = true
            PetkitFrames.encode(Ctw3Cmd.DEVICE_STATUS, PetkitFrame.REQUEST, pushSeq++, ByteArray(42))
        } else {
            null
        }
        if (glue && status != null) notify(reply + status) else listOfNotNull(reply, status).forEach(::notify)
        if (request.cmd == Ctw3Cmd.STREAM_SETTING && (replies[request.cmd] ?: OK)[0].toInt() == 1) stream(firstPass = true)
    }

    /** The session's answers to the stream (67, 69) and its 230 acks. */
    private fun afterAnswer(answer: PetkitFrame) {
        if (answer.cmd == Ctw3Cmd.STREAM_CHECK) {
            val bitmap = answer.data.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            val missing = packets.indices.filter { bitmap and (1L shl (31 - it)) == 0L }
            if (missing.isEmpty()) {
                notify(PetkitFrames.encode(Ctw3Cmd.STREAM_END, PetkitFrame.REQUEST, pushSeq++))
            } else {
                missing.forEach { notify(packet(it)) }
                notify(PetkitFrames.encode(Ctw3Cmd.STREAM_CHECK, PetkitFrame.REQUEST, pushSeq++))
            }
        }
    }

    private fun stream(firstPass: Boolean) {
        packets.indices.filter { !firstPass || it !in dropOnce }.forEach { notify(packet(it)) }
        notify(PetkitFrames.encode(Ctw3Cmd.STREAM_CHECK, PetkitFrame.REQUEST, pushSeq++))
    }

    private fun packet(index: Int): ByteArray {
        val data = packets[index]
        val header = byteArrayOf(
            0xFA.toByte(), 0xFC.toByte(), 0xFE.toByte(),
            Ctw3Cmd.STREAM_DATA.toByte(), PetkitFrame.NO_RESPONSE.toByte(),
            index.toByte(), packets.size.toByte(),
            (data.size and 0xFF).toByte(), (data.size shr 8).toByte(),
        )
        return header + data
    }

    private fun notify(bytes: ByteArray) {
        bytes.toList().chunked(chunk).forEach { notifications.trySend(it.toByteArray()) }
    }

    companion object {
        private val OK = byteArrayOf(1)

        fun historyReply(bytes: Int): ByteArray =
            byteArrayOf(0, (bytes shr 24).toByte(), (bytes shr 16).toByte(), (bytes shr 8).toByte(), bytes.toByte())

        fun record(r: DrinkRecord): ByteArray = byteArrayOf(
            (r.workTime shr 24).toByte(), (r.workTime shr 16).toByte(), (r.workTime shr 8).toByte(), r.workTime.toByte(),
            (r.stayTime shr 8).toByte(), r.stayTime.toByte(),
        )
    }
}

/** A [BleTransport] handing out one [ScriptedFountain], or failing as told. */
class ScriptedTransport(
    private val fountain: ScriptedFountain?,
    private val failure: FountainLinkFailure? = null,
    var availability: BluetoothAvailability = BluetoothAvailability.Ready,
) : BleTransport {
    var connectedTo: String? = null

    override fun availability() = availability

    override suspend fun connect(mac: String): BleLink {
        failure?.let { throw BleLinkException(it, "scripted") }
        connectedTo = mac
        return checkNotNull(fountain)
    }
}

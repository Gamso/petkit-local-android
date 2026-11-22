package org.petkitlocal.android

import kotlinx.coroutines.channels.Channel
import org.petkitlocal.core.data.drinksync.ble.BleLink
import org.petkitlocal.core.data.drinksync.ble.BleTransport
import org.petkitlocal.core.data.drinksync.ble.Ctw3Cmd
import org.petkitlocal.core.data.drinksync.ble.PetkitFrame
import org.petkitlocal.core.data.drinksync.ble.PetkitFrames
import org.petkitlocal.core.domain.drinksync.BluetoothAvailability
import org.petkitlocal.core.domain.drinksync.DrinkRecord

/**
 * A CTW3 in place of the radio, for the end-to-end tests (an emulator has no fountain): it answers
 * PetKit's session as the real one does, checks the pairing secret it was given against [secret],
 * and streams [records] in one batch of one-record packets. Installed through the debug build's
 * [EndToEndBleTransport]; the protocol itself is tested in core:data (`Ctw3SessionTest`).
 */
class ScriptedFountainTransport(
    private val secret: ByteArray,
    private val records: List<DrinkRecord>,
) : BleTransport {
    /** Every MAC the app connected to. */
    val connectedTo = mutableListOf<String>()

    override fun availability() = BluetoothAvailability.Ready

    override suspend fun connect(mac: String): BleLink {
        connectedTo += mac
        return Link()
    }

    private inner class Link : BleLink {
        override val mtu = MTU
        override val notifications = Channel<ByteArray>(Channel.UNLIMITED)
        private val assembler = PetkitFrames.Assembler()
        private var pushSeq = FIRST_PUSH_SEQ

        override suspend fun write(bytes: ByteArray) {
            for (frame in assembler.feed(bytes)) {
                when {
                    frame.type == PetkitFrame.REQUEST -> answer(frame)
                    frame.cmd == Ctw3Cmd.STREAM_CHECK -> push(PetkitFrames.encode(Ctw3Cmd.STREAM_END, PetkitFrame.REQUEST, pushSeq++))
                }
            }
        }

        override fun close() {
            notifications.close()
        }

        private fun answer(request: PetkitFrame) {
            val data = when (request.cmd) {
                Ctw3Cmd.SECURITY_CHECK -> byteArrayOf(if (request.data.contentEquals(secret)) 1 else 0)
                Ctw3Cmd.SYNC_HISTORY -> history(records.size * RECORD_LEN)
                else -> byteArrayOf(1)
            }
            push(PetkitFrames.encode(request.cmd, PetkitFrame.RESPONSE, request.seq, data))
            if (request.cmd == Ctw3Cmd.STREAM_SETTING) stream()
        }

        private fun stream() {
            records.forEachIndexed { index, record ->
                val data = record.bytes()
                push(
                    byteArrayOf(
                        0xFA.toByte(), 0xFC.toByte(), 0xFE.toByte(),
                        Ctw3Cmd.STREAM_DATA.toByte(), PetkitFrame.NO_RESPONSE.toByte(),
                        index.toByte(), records.size.toByte(), data.size.toByte(), 0,
                    ) + data,
                )
            }
            push(PetkitFrames.encode(Ctw3Cmd.STREAM_CHECK, PetkitFrame.REQUEST, pushSeq++))
        }

        private fun push(bytes: ByteArray) {
            notifications.trySend(bytes)
        }
    }

    private companion object {
        const val MTU = 247
        const val RECORD_LEN = 6
        const val FIRST_PUSH_SEQ = 100

        fun history(bytes: Int) =
            byteArrayOf(0, (bytes shr 24).toByte(), (bytes shr 16).toByte(), (bytes shr 8).toByte(), bytes.toByte())

        fun DrinkRecord.bytes() = byteArrayOf(
            (workTime shr 24).toByte(), (workTime shr 16).toByte(), (workTime shr 8).toByte(), workTime.toByte(),
            (stayTime shr 8).toByte(), stayTime.toByte(),
        )
    }
}

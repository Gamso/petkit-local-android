package org.petkitlocal.core.data.drinksync.ble

import kotlinx.coroutines.channels.ReceiveChannel
import org.petkitlocal.core.domain.drinksync.BluetoothAvailability
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure

/**
 * The phone's Bluetooth, as small as a PetKit session needs it: find a fountain by its address,
 * open the PetKit service, write frames and hear its notifications. Behind an interface so the
 * session can be replayed against a script in tests ([Ctw3Session]), and so the end-to-end tests
 * can put a scripted fountain in place of the radio (app, debug builds only).
 */
interface BleTransport {
    fun availability(): BluetoothAvailability

    /**
     * Scans for the fountain at [mac] and connects to it: the PetKit service discovered, its
     * notifications on, the MTU negotiated. Throws [BleLinkException] when it cannot.
     */
    suspend fun connect(mac: String): BleLink
}

/** One open link to a fountain. Closing it disconnects; it is closed once, by whoever opened it. */
interface BleLink {
    /** The ATT MTU the phone and the fountain agreed on. */
    val mtu: Int

    /**
     * Every notification of the PetKit characteristic, in order. It closes with a
     * [BleLinkException] ([FountainLinkFailure.ConnectionLost]) when the link breaks.
     */
    val notifications: ReceiveChannel<ByteArray>

    /** Writes [bytes] to the PetKit characteristic, waiting for the write to complete. Throws [BleLinkException]. */
    suspend fun write(bytes: ByteArray)

    fun close()
}

/** A link that could not be opened, or broke: [failure] is what the page says. */
class BleLinkException(val failure: FountainLinkFailure, message: String, cause: Throwable? = null) :
    Exception(message, cause)

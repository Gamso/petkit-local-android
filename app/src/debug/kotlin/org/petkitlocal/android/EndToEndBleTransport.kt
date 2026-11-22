package org.petkitlocal.android

import javax.inject.Inject
import javax.inject.Singleton
import org.petkitlocal.core.data.drinksync.ble.AndroidBleTransport
import org.petkitlocal.core.data.drinksync.ble.BleTransport

/**
 * The Bluetooth a debug build syncs a fountain through: the phone's [radio], unless an end-to-end
 * test set [replacement] (a scripted fountain, since an emulator has none). Nothing in the app sets
 * it, so a debug build on a phone talks to the real fountain.
 */
@Singleton
class EndToEndBleTransport @Inject constructor(private val radio: AndroidBleTransport) : BleTransport {
    @Volatile
    var replacement: BleTransport? = null

    private val current: BleTransport get() = replacement ?: radio

    override fun availability() = current.availability()

    override suspend fun connect(mac: String) = current.connect(mac)
}

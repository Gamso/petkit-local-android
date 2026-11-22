package org.petkitlocal.core.data.drinksync.ble

import java.time.Clock
import javax.inject.Inject
import org.petkitlocal.core.domain.drinksync.BluetoothAvailability
import org.petkitlocal.core.domain.drinksync.FountainBluetooth
import org.petkitlocal.core.domain.drinksync.FountainLinkProgress
import org.petkitlocal.core.domain.drinksync.FountainReadResult
import org.petkitlocal.core.domain.drinksync.FountainSecret

/**
 * [FountainBluetooth] on a [BleTransport]: the link opened, [Ctw3Session] run over it, the link
 * closed whatever happened (cancellation included). The secret's bytes are copied for the session
 * only and zeroed after it. Nothing here logs.
 */
class BleFountainBluetooth @Inject constructor(private val transport: BleTransport, private val clock: Clock) :
    FountainBluetooth {
    /** Waits, overridable by tests. */
    internal var timing: Ctw3Timing = Ctw3Timing()

    override fun availability(): BluetoothAvailability = transport.availability()

    override suspend fun readHistory(
        mac: String,
        secret: FountainSecret,
        onProgress: suspend (FountainLinkProgress) -> Unit,
    ): FountainReadResult {
        onProgress(FountainLinkProgress.Searching)
        val link = try {
            transport.connect(mac)
        } catch (e: BleLinkException) {
            return FountainReadResult.Failure(e.failure)
        }
        val bytes = secret.bytes()
        return try {
            onProgress(FountainLinkProgress.Connected)
            val records = Ctw3Session(link, bytes, clock, timing).run { onProgress(FountainLinkProgress.Reading(it)) }
            FountainReadResult.Success(records)
        } catch (e: BleLinkException) {
            FountainReadResult.Failure(e.failure)
        } finally {
            bytes.fill(0)
            link.close()
        }
    }
}

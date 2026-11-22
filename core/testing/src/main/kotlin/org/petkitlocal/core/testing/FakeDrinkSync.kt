package org.petkitlocal.core.testing

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.drinksync.BleSyncInfo
import org.petkitlocal.core.domain.drinksync.BluetoothAvailability
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.domain.drinksync.DrinkSyncRepository
import org.petkitlocal.core.domain.drinksync.DrinkUploadResult
import org.petkitlocal.core.domain.drinksync.FountainBluetooth
import org.petkitlocal.core.domain.drinksync.FountainLinkFailure
import org.petkitlocal.core.domain.drinksync.FountainLinkProgress
import org.petkitlocal.core.domain.drinksync.FountainReadResult
import org.petkitlocal.core.domain.drinksync.FountainSecret

/** The sync routes, scripted: [bleSyncResult] answers, every upload is kept and stored whole. */
class FakeDrinkSyncRepository : DrinkSyncRepository {
    var bleSyncResult: Outcome<BleSyncInfo> = Outcome.Success(info())
    var uploadResult: ((List<DrinkRecord>) -> Outcome<DrinkUploadResult>)? = null
    val uploads = mutableListOf<List<DrinkRecord>>()
    val lastSyncs = MutableStateFlow<Map<Long, Instant>>(emptyMap())

    override suspend fun bleSync(accessoryId: Long): Outcome<BleSyncInfo> = when (val result = bleSyncResult) {
        // A fresh secret each time: the use case wipes the one it got.
        is Outcome.Success -> Outcome.Success(result.value.copy(secret = FountainSecret.fromHex(SECRET)))
        is Outcome.Failure -> result
    }

    override suspend fun uploadDrinks(accessoryId: Long, records: List<DrinkRecord>): Outcome<DrinkUploadResult> {
        uploads += records
        return uploadResult?.invoke(records) ?: Outcome.Success(DrinkUploadResult(records.size, 0, 0, null))
    }

    override fun observeLastSync(accessoryId: Long): Flow<Instant?> = lastSyncs.map { it[accessoryId] }

    override suspend fun recordSync(accessoryId: Long, at: Instant) {
        lastSyncs.value += accessoryId to at
    }

    companion object {
        const val SECRET = "0000a1b2c3d4e5f6"
        const val MAC = "A4:C1:38:00:0B:BB"

        fun info(id: Long = 3003L, cursor: Long? = null) = BleSyncInfo(
            id = id,
            mac = MAC,
            syncSupported = true,
            secret = FountainSecret.fromHex(SECRET),
            historyCursor = cursor,
            clockEpoch = 946_684_800L,
        )
    }
}

/**
 * The phone's Bluetooth side, scripted: it reports Searching, Connected and Reading, then gives
 * [records] or fails with [failure]. With [gate] set, it waits for it after connecting.
 */
class FakeFountainBluetooth : FountainBluetooth {
    var availability = BluetoothAvailability.Ready
    var records: List<DrinkRecord> = emptyList()
    var failure: FountainLinkFailure? = null
    var gate: CompletableDeferred<Unit>? = null
    val macs = mutableListOf<String>()

    override fun availability() = availability

    override suspend fun readHistory(
        mac: String,
        secret: FountainSecret,
        onProgress: suspend (FountainLinkProgress) -> Unit,
    ): FountainReadResult {
        macs += mac
        onProgress(FountainLinkProgress.Searching)
        onProgress(FountainLinkProgress.Connected)
        gate?.await()
        failure?.let { return FountainReadResult.Failure(it) }
        onProgress(FountainLinkProgress.Reading(records.size))
        return FountainReadResult.Success(records)
    }
}

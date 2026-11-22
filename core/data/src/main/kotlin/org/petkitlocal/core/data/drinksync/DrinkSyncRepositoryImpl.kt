package org.petkitlocal.core.data.drinksync

import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.datastore.DrinkSyncStore
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.auth.ServerScopedCache
import org.petkitlocal.core.domain.drinksync.BleSyncInfo
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.domain.drinksync.DrinkSyncRepository
import org.petkitlocal.core.domain.drinksync.DrinkUploadResult
import org.petkitlocal.core.domain.drinksync.FountainSecret
import org.petkitlocal.core.domain.map
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.dto.DrinkRecordUploadDto
import org.petkitlocal.core.network.dto.DrinkUploadRequestDto
import org.petkitlocal.core.network.error.apiCall

/**
 * The two sync routes (`/accessories/{id}/ble-sync`, `/accessories/{id}/drinks`) and the last
 * success, kept on the phone. The secret goes from the answer straight into a [FountainSecret];
 * it is not cached, logged or written, and the answer is `Cache-Control: no-store`.
 */
class DrinkSyncRepositoryImpl @Inject constructor(
    private val api: PetkitApi,
    private val store: DrinkSyncStore,
) : DrinkSyncRepository {
    override suspend fun bleSync(accessoryId: Long): Outcome<BleSyncInfo> = apiCall {
        api.getAccessoryBleSync(accessoryId)
    }.map { dto ->
        BleSyncInfo(
            id = dto.id,
            mac = dto.mac,
            syncSupported = dto.syncSupported,
            secret = FountainSecret.fromHex(dto.secret),
            historyCursor = dto.historyCursor,
            clockEpoch = dto.clockEpoch,
        )
    }

    override suspend fun uploadDrinks(accessoryId: Long, records: List<DrinkRecord>): Outcome<DrinkUploadResult> =
        apiCall {
            api.uploadAccessoryDrinks(
                accessoryId,
                DrinkUploadRequestDto(
                    records = records.map { DrinkRecordUploadDto(workTime = it.workTime, stayTime = it.stayTime) },
                    source = DrinkUploadRequestDto.SOURCE_PHONE_BLE,
                ),
            )
        }.map { DrinkUploadResult(it.stored, it.duplicates, it.skipped, it.cursor) }

    override fun observeLastSync(accessoryId: Long): Flow<Instant?> =
        store.lastSync(accessoryId).map { millis -> millis?.let(Instant::ofEpochMilli) }

    override suspend fun recordSync(accessoryId: Long, at: Instant) {
        try {
            store.setLastSync(accessoryId, at.toEpochMilli())
        } catch (_: IOException) {
            // An unwritable disk: the drinks are on the server all the same; only the time is lost.
        }
    }
}

/** The last syncs are of one server's accessories: another server's ids are other devices. */
class DrinkSyncServerCache @Inject constructor(private val store: DrinkSyncStore) : ServerScopedCache {
    override suspend fun keepOnlyFor(serverBaseUrl: String) {
        try {
            store.keepOnlyFor(serverBaseUrl)
        } catch (_: IOException) {
            // Unwritable: an old time stays, and only ever matches an accessory id by chance.
        }
    }
}

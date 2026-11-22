package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `BleSync`: what the phone needs to read a fountain's drink history over its own Bluetooth
 * (`GET /accessories/{id}/ble-sync`, petkit-local#141).
 *
 * [secret] is a CREDENTIAL: kept in memory for one sync only, never logged, cached or shown, which
 * is why [toString] leaves it out.
 */
@Serializable
data class BleSyncDto(
    @SerialName("id") val id: Long,
    /** The accessory's codename, as `DeviceSummary.type` (`ctw3`). */
    @SerialName("type") val type: String,
    /** `AA:BB:CC:DD:EE:FF`, as `BluetoothAdapter.getRemoteDevice` takes it. */
    @SerialName("mac") val mac: String,
    /** Whether its history can be synced (a CTW3 today); when false, [secret] and [historyCursor] are null. */
    @SerialName("sync_supported") val syncSupported: Boolean,
    /** 16 lowercase hex digits, already left-padded: the security check's (`86`) 8 bytes. */
    @SerialName("secret") val secret: String?,
    /** Unix seconds of the newest drink the server holds for it; null for none. */
    @SerialName("history_cursor") val historyCursor: Long?,
    /** The fountain's clock counts from 2000-01-01 UTC: that instant in Unix seconds (946684800). */
    @SerialName("clock_epoch") val clockEpoch: Long,
) {
    override fun toString(): String = "BleSyncDto(id=$id, type=$type, mac=$mac, syncSupported=$syncSupported, " +
        "secret=${if (secret == null) "null" else "<redacted>"}, historyCursor=$historyCursor, clockEpoch=$clockEpoch)"
}

/** `DrinkUploadRequest`: at most 5000 records per upload. */
@Serializable
data class DrinkUploadRequestDto(
    @SerialName("records") val records: List<DrinkRecordUploadDto>,
    /** `phone-ble`, the only value (and the server's default). */
    @SerialName("source") val source: String? = null,
) {
    companion object {
        const val SOURCE_PHONE_BLE = "phone-ble"
    }
}

/** `DrinkRecordUpload`: one record exactly as the fountain sent it. */
@Serializable
data class DrinkRecordUploadDto(
    /** The fountain's `workTime`, a big-endian u32 of seconds since 2000: the record's identity. */
    @SerialName("work_time") val workTime: Long,
    /** The fountain's `stayTime`, a signed 16-bit count of seconds. */
    @SerialName("stay_time") val stayTime: Int,
)

/** `DrinkUploadResult`. */
@Serializable
data class DrinkUploadResultDto(
    /** Records stored for the first time. */
    @SerialName("stored") val stored: Int,
    /** Records the server already held. */
    @SerialName("duplicates") val duplicates: Int,
    /** Records that were not plausible and were left out. */
    @SerialName("skipped") val skipped: Int,
    /** `BleSync.history_cursor` after this upload. */
    @SerialName("cursor") val cursor: Long?,
)

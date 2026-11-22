package org.petkitlocal.core.domain.drinksync

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily

/*
 * A CTW3's drink history, read by the phone over its own Bluetooth (issue #104). The fountain keeps
 * its drinks in its own memory and gives them only over a DIRECT link: through its parent relay it
 * never answers the history request (Gamso/petkit-local#140). So the app does what PetKit's app
 * 13.8.1 does (`PetkitBleClient`, `CTW3BleClient`): it gets the fountain's address and pairing
 * secret from petkit-local, runs PetKit's session with the fountain, and uploads the records,
 * which petkit-local stores as drinking cards on the accessory (petkit-local#141).
 */

/** Which devices the app offers to sync: a Bluetooth fountain the server can sync today. */
object DrinkSyncRules {
    /**
     * petkit-local answers `sync_supported: true` for a `ctw3` only (`app_api/handlers/accessories.py`,
     * `codes.BLE_HISTORY_SYNC_TYPES`): the other Bluetooth fountains keep no history to read. The
     * server still has the last word ([BleSyncInfo.syncSupported]).
     */
    private val SYNCED_TYPES = setOf("ctw3")

    fun canSync(device: Device): Boolean =
        device.isAccessory && device.family == DeviceFamily.Fountain && device.type in SYNCED_TYPES
}

/**
 * A fountain's pairing secret, the 8 bytes of its security check (`86`). A CREDENTIAL: held for
 * one sync only, never logged, persisted or shown, so [toString] says nothing of it, and [wipe]
 * zeroes it once the sync is over.
 */
class FountainSecret(bytes: ByteArray) {
    private val bytes = bytes.copyOf()

    /** A copy of the bytes, for the frame that carries them. */
    fun bytes(): ByteArray = bytes.copyOf()

    fun wipe() = bytes.fill(0)

    override fun toString(): String = "FountainSecret(<redacted>)"

    companion object {
        const val LENGTH = 8

        /** The server's 16 hex digits; null for anything else, which the fountain would refuse. */
        @Suppress("MagicNumber") // Hex digits.
        fun fromHex(hex: String?): FountainSecret? {
            if (hex == null || hex.length != LENGTH * 2) return null
            val bytes = ByteArray(LENGTH)
            for (i in 0 until LENGTH) {
                val high = Character.digit(hex[2 * i], 16)
                val low = Character.digit(hex[2 * i + 1], 16)
                if (high < 0 || low < 0) return null
                bytes[i] = ((high shl 4) or low).toByte()
            }
            return FountainSecret(bytes).also { bytes.fill(0) }
        }
    }
}

/** What petkit-local gives the phone to sync one accessory (`GET /accessories/{id}/ble-sync`). */
data class BleSyncInfo(
    val id: Long,
    /** `AA:BB:CC:DD:EE:FF`. */
    val mac: String,
    /** Whether the server can sync this accessory's history at all (a CTW3 today). */
    val syncSupported: Boolean,
    /** Null when [syncSupported] is false. */
    val secret: FountainSecret?,
    /** Unix seconds of the newest drink the server holds; null for none. */
    val historyCursor: Long?,
    /** The fountain's clock's zero, in Unix seconds: 2000-01-01 UTC. */
    val clockEpoch: Long,
)

/**
 * One drink as the fountain keeps it: [workTime] its `u32` seconds since its clock's zero, the
 * record's identity on the server, and [stayTime] the seconds the pet drank (a signed 16-bit value).
 */
data class DrinkRecord(val workTime: Long, val stayTime: Int)

/** What the server made of an upload. */
data class DrinkUploadResult(val stored: Int, val duplicates: Int, val skipped: Int, val cursor: Long?)

/** The two routes of a phone-side sync, and the last time one succeeded on this phone. */
interface DrinkSyncRepository {
    /**
     * The fountain's address and secret. A CTW3 whose secret the server does not hold fails with
     * [DomainError.Server] 409 `secret_unknown` (or `secret_invalid`), its `message` saying what to
     * do in petkit-local's panel.
     */
    suspend fun bleSync(accessoryId: Long): Outcome<BleSyncInfo>

    /** At most [MAX_RECORDS_PER_UPLOAD] records per call. */
    suspend fun uploadDrinks(accessoryId: Long, records: List<DrinkRecord>): Outcome<DrinkUploadResult>

    /** When a sync of [accessoryId] last succeeded on this phone; null if never. */
    fun observeLastSync(accessoryId: Long): Flow<Instant?>

    suspend fun recordSync(accessoryId: Long, at: Instant)

    companion object {
        /** The server's limit per upload (`DrinkUploadRequest.records.maxItems`). */
        const val MAX_RECORDS_PER_UPLOAD = 5000
    }
}

/** Whether the phone can use Bluetooth now. */
enum class BluetoothAvailability {
    Ready,

    /** The phone has no Bluetooth Low Energy (an emulator without it, say). */
    Unavailable,

    /** Bluetooth is off. */
    Off,
}

/** Where a session with the fountain stands, as it goes. */
sealed interface FountainLinkProgress {
    data object Searching : FountainLinkProgress

    data object Connected : FountainLinkProgress

    /** The fountain announced [drinks] records and is sending them. */
    data class Reading(val drinks: Int) : FountainLinkProgress
}

/** Why a session with the fountain did not give its history. */
enum class FountainLinkFailure {
    /** The phone has no Bluetooth Low Energy. */
    BluetoothUnavailable,

    BluetoothOff,

    /** The nearby-devices (or, before Android 12, location) permission is missing. */
    PermissionMissing,

    /** The fountain was not found: too far, asleep, or busy with another phone. */
    NotFound,

    /** The fountain refused the pairing secret (its `86` answered something other than `01`). */
    SecretRefused,

    /** The fountain refused the clock (`84`). */
    ClockRefused,

    /** The fountain refused to stream its history (`80`). */
    StreamRefused,

    /** The fountain stopped answering. */
    Timeout,

    /** The link broke, or the fountain is not the device the app expected (no PetKit service). */
    ConnectionLost,
}

/** The phone's Bluetooth side of a sync: PetKit's session with one fountain. */
interface FountainBluetooth {
    fun availability(): BluetoothAvailability

    /**
     * Connects to the fountain at [mac], proves the pairing [secret], gives it the time, and reads
     * every drink record it holds. [onProgress] hears each stage. Never throws but for
     * cancellation, which disconnects.
     */
    suspend fun readHistory(
        mac: String,
        secret: FountainSecret,
        onProgress: suspend (FountainLinkProgress) -> Unit,
    ): FountainReadResult
}

sealed interface FountainReadResult {
    data class Success(val records: List<DrinkRecord>) : FountainReadResult

    data class Failure(val reason: FountainLinkFailure) : FountainReadResult
}

/** Whether the phone holds the Bluetooth permissions a sync needs (Android's, by version). */
fun interface BluetoothAccess {
    fun granted(): Boolean
}

package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.api.BleSync
import io.github.gamso.petkitlocal.mock.api.DrinkUploadResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * A CTW3's drink history, synced by the phone over its own Bluetooth (petkit-local#141,
 * `app_api/handlers/accessories.py`, `devices/ble/history.py`): the phone asks for the fountain's
 * address and secret, reads the records itself, and uploads them, each stored as one drinking card
 * on the accessory, timed by its stay.
 */

/** The fountain's clock counts from 2000-01-01 UTC (`BleSync.clock_epoch`). */
const val CTW3_CLOCK_EPOCH = 946_684_800L

/** The accessories whose history the phone can sync (`codes.BLE_HISTORY_SYNC_TYPES`). */
val SYNCED_TYPES = setOf("ctw3")

/** At most this many records per upload (`DrinkUploadRequest.records.maxItems`). */
const val MAX_DRINK_RECORDS = 5000

/** 2020-01-01 UTC: a record before it is not plausible. */
private const val EARLIEST_RECORD = 1_577_836_800L
private const val DAY = 86_400L
private const val MAX_U32 = 0xFFFF_FFFFL
private const val MAX_S16 = 32_767L

/** `GET /accessories/{id}/ble-sync`. */
fun World.bleSync(device: MockDevice): BleSync {
    val supported = device.type in SYNCED_TYPES
    val secret = device.bleSecret
    if (supported && secret == null) {
        throw ApiException(
            409,
            "secret_unknown",
            "petkit-local holds no pairing secret for this fountain: press Import from PetKit in the panel, " +
                "on the device it is paired to, then sync again.",
        )
    }
    if (supported && secret?.matches(Regex("^[0-9a-f]{16}$")) != true) {
        throw ApiException(409, "secret_invalid", "the pairing secret held for this fountain is not 8 bytes of hex")
    }
    return BleSync(
        id = device.id,
        type = device.type,
        mac = device.bleMac ?: "00:00:00:00:00:00",
        syncSupported = supported,
        secret = if (supported) secret else null,
        historyCursor = if (supported) drinkCursor(device) else null,
        clockEpoch = CTW3_CLOCK_EPOCH,
    )
}

/** The newest record held, in Unix seconds. */
private fun drinkCursor(device: MockDevice): Long? =
    device.drinkRecords.keys
        .maxOrNull()
        ?.plus(CTW3_CLOCK_EPOCH)

/**
 * `POST /accessories/{id}/drinks`: each plausible record not held yet becomes a drinking card,
 * sent on `/ws` as the relay path's would be. A record held is a duplicate; one implausible is
 * skipped, never refused.
 */
fun World.uploadDrinks(
    device: MockDevice,
    body: JsonObject,
): DrinkUploadResult {
    if (device.type !in SYNCED_TYPES) throw ApiException(400, "sync_unsupported", "only a CTW3's drink history can be synced")
    val records = body["records"] as? JsonArray ?: throw ApiException(400, "bad_parameter", "records must be a list")
    val source = body["source"]
    if (source != null && source != JsonPrimitive("phone-ble")) throw ApiException(400, "bad_parameter", "source must be phone-ble")
    if (records.size > MAX_DRINK_RECORDS) {
        throw ApiException(413, "too_many_records", "at most $MAX_DRINK_RECORDS records per upload")
    }
    var stored = 0
    var duplicates = 0
    var skipped = 0
    val now = clock.now()
    records.forEach { element ->
        val record = element as? JsonObject
        val work = record?.get("work_time").strictLong()
        val stay = record?.get("stay_time").strictLong()
        when {
            work == null || stay == null || !plausible(work, stay, now) -> {
                skipped++
            }

            work in device.drinkRecords -> {
                duplicates++
            }

            else -> {
                val start = work + CTW3_CLOCK_EPOCH
                device.drinkRecords[work] = start.toDouble()
                val card = drinkCard(device, (start + stay).toDouble(), stay.toInt(), petId = null)
                publish(card)
                stored++
            }
        }
    }
    return DrinkUploadResult(stored = stored, duplicates = duplicates, skipped = skipped, cursor = drinkCursor(device))
}

/** Within its width, a stay not negative, and a time from 2020 to a day ahead (`history.py`). */
private fun plausible(
    work: Long,
    stay: Long,
    now: Double,
): Boolean {
    val start = work + CTW3_CLOCK_EPOCH
    val inWidth = work in 0..MAX_U32 && stay in 0..MAX_S16
    return inWidth && start >= EARLIEST_RECORD && start <= now + DAY
}

/** An integer and nothing else: `1.0`, `"1"` and `true` are not one. */
private fun JsonElement?.strictLong(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive is JsonNull || primitive.isString) return null
    return primitive.content.takeIf { text -> text.all { it.isDigit() || it == '-' } }?.toLongOrNull()
}

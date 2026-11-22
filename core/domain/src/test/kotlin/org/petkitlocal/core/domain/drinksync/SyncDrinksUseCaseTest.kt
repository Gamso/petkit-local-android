package org.petkitlocal.core.domain.drinksync

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryPage
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.TimelineRepository

class SyncDrinksUseCaseTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val now = Instant.parse("2026-11-15T09:00:00Z")
    private val clock = Clock.fixed(now, zone)

    /** 2026-11-14 20:00 UTC and 2026-11-15 08:00 UTC, in the fountain's clock (seconds since 2000). */
    private val older = DrinkRecord(Instant.parse("2026-11-14T20:00:00Z").epochSecond - EPOCH, 12)
    private val newer = DrinkRecord(Instant.parse("2026-11-15T08:00:00Z").epochSecond - EPOCH, 30)

    private val repository = FakeRepository()
    private val bluetooth = FakeBluetooth()
    private val timeline = RecordingTimeline()
    private val sync = SyncDrinksUseCase(repository, bluetooth, timeline, clock)

    @Test
    fun `reads the fountain, uploads what the server lacks, re-reads those days and records the success`() = runTest {
        repository.info = info(cursor = older.workTime + EPOCH)
        bluetooth.records = listOf(older, newer, newer)

        val steps = sync(ACCESSORY).toList()

        assertEquals(
            listOf(
                DrinkSyncStep.Searching,
                DrinkSyncStep.Searching,
                DrinkSyncStep.Connected,
                DrinkSyncStep.Reading(3),
                DrinkSyncStep.Uploading(1),
                DrinkSyncStep.Done(1),
            ),
            steps,
        )
        assertEquals(listOf(listOf(newer)), repository.uploads)
        assertEquals(listOf(LocalDate.of(2026, 11, 15)), timeline.refreshed)
        assertEquals(now, repository.lastSync.value)
        assertEquals("AA:BB:CC:DD:EE:FF", bluetooth.mac)
        // The secret the session got, then wiped once it was over.
        assertArrayEquals(byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9a.toByte(), 0xbc.toByte(), 0xde.toByte(), 0xf0.toByte()), bluetooth.secretSeen)
        assertArrayEquals(ByteArray(FountainSecret.LENGTH), bluetooth.secret!!.bytes())
    }

    @Test
    fun `nothing newer than the cursor is a success with no upload`() = runTest {
        repository.info = info(cursor = newer.workTime + EPOCH)
        bluetooth.records = listOf(older, newer)

        assertEquals(DrinkSyncStep.Done(0), sync(ACCESSORY).toList().last())
        assertTrue(repository.uploads.isEmpty())
        assertTrue(timeline.refreshed.isEmpty())
        assertEquals(now, repository.lastSync.value)
    }

    @Test
    fun `an empty history is a success`() = runTest {
        bluetooth.records = emptyList()
        assertEquals(DrinkSyncStep.Done(0), sync(ACCESSORY).toList().last())
    }

    @Test
    fun `a long history goes up in uploads of at most 5000`() = runTest {
        bluetooth.records = (1..7001).map { DrinkRecord(older.workTime + it, 5) }
        repository.info = info(cursor = null)

        assertEquals(DrinkSyncStep.Done(7001), sync(ACCESSORY).toList().last())
        assertEquals(listOf(5000, 2001), repository.uploads.map { it.size })
    }

    @Test
    fun `Bluetooth off or missing stops before asking the server`() = runTest {
        bluetooth.availability = BluetoothAvailability.Off
        assertEquals(failure(FountainLinkFailure.BluetoothOff), sync(ACCESSORY).toList().last())
        bluetooth.availability = BluetoothAvailability.Unavailable
        assertEquals(failure(FountainLinkFailure.BluetoothUnavailable), sync(ACCESSORY).toList().last())
        assertEquals(0, repository.bleSyncCalls)
    }

    @Test
    fun `the server's secret_unknown is said with its own message`() = runTest {
        repository.bleSyncResult = Outcome.Failure(DomainError.Server(409, "secret_unknown", "Import from PetKit in the panel"))

        val last = sync(ACCESSORY).toList().last()

        assertEquals(DrinkSyncStep.Failed(DrinkSyncFailure.SecretUnknown("Import from PetKit in the panel")), last)
        assertNull(bluetooth.mac)
    }

    @Test
    fun `secret_invalid, an unsupported accessory and an unreachable server each say so`() = runTest {
        repository.bleSyncResult = Outcome.Failure(DomainError.Server(409, "secret_invalid", ""))
        assertEquals(DrinkSyncStep.Failed(DrinkSyncFailure.SecretInvalid(null)), sync(ACCESSORY).toList().last())

        repository.bleSyncResult = null
        repository.info = info(cursor = null).copy(syncSupported = false, secret = null)
        assertEquals(DrinkSyncStep.Failed(DrinkSyncFailure.Unsupported), sync(ACCESSORY).toList().last())

        repository.bleSyncResult = Outcome.Failure(DomainError.Network())
        assertEquals(DrinkSyncStep.Failed(DrinkSyncFailure.Server(DomainError.Network())), sync(ACCESSORY).toList().last())
    }

    @Test
    fun `a refusal by the fountain is the failure, nothing uploaded or recorded, the secret wiped`() = runTest {
        bluetooth.failure = FountainLinkFailure.SecretRefused

        assertEquals(failure(FountainLinkFailure.SecretRefused), sync(ACCESSORY).toList().last())
        assertTrue(repository.uploads.isEmpty())
        assertNull(repository.lastSync.value)
        assertArrayEquals(ByteArray(FountainSecret.LENGTH), bluetooth.secret!!.bytes())
    }

    @Test
    fun `a failed upload is the server's failure, and no success is recorded`() = runTest {
        repository.info = info(cursor = null)
        bluetooth.records = listOf(newer)
        repository.uploadResult = Outcome.Failure(DomainError.Server(500, "drinks_not_stored", null))

        assertEquals(
            DrinkSyncStep.Failed(DrinkSyncFailure.Server(DomainError.Server(500, "drinks_not_stored", null))),
            sync(ACCESSORY).toList().last(),
        )
        assertNull(repository.lastSync.value)
    }

    @Test
    fun `only a Bluetooth CTW3 is offered the sync`() {
        val ctw3 = org.petkitlocal.core.domain.model.Device(
            id = 1, type = "ctw3", modelName = "EverSweet Max 2", family = DeviceFamily.Fountain, online = true,
            transport = org.petkitlocal.core.domain.model.DeviceTransport.Http, lastSeen = null, firmware = null,
            isCamera = false, connection = DeviceConnection.Ble,
        )
        assertTrue(DrinkSyncRules.canSync(ctw3))
        assertEquals(false, DrinkSyncRules.canSync(ctw3.copy(type = "w5")))
        assertEquals(false, DrinkSyncRules.canSync(ctw3.copy(connection = DeviceConnection.Wifi)))
    }

    @Test
    fun `a secret is 16 hex digits, and says nothing of itself`() {
        assertNull(FountainSecret.fromHex("123"))
        assertNull(FountainSecret.fromHex("zz3456789abcdef0"))
        val secret = FountainSecret.fromHex("123456789abcdef0")!!
        assertEquals("FountainSecret(<redacted>)", secret.toString())
    }

    private fun failure(reason: FountainLinkFailure) = DrinkSyncStep.Failed(DrinkSyncFailure.Link(reason))

    private fun info(cursor: Long?) = BleSyncInfo(
        id = ACCESSORY,
        mac = "AA:BB:CC:DD:EE:FF",
        syncSupported = true,
        secret = FountainSecret.fromHex("123456789abcdef0"),
        historyCursor = cursor,
        clockEpoch = EPOCH,
    )

    private inner class FakeRepository : DrinkSyncRepository {
        var info: BleSyncInfo = info(cursor = null)
        var bleSyncResult: Outcome<BleSyncInfo>? = null
        var bleSyncCalls = 0
        var uploadResult: Outcome<DrinkUploadResult>? = null
        val uploads = mutableListOf<List<DrinkRecord>>()
        val lastSync = MutableStateFlow<Instant?>(null)

        override suspend fun bleSync(accessoryId: Long): Outcome<BleSyncInfo> {
            bleSyncCalls++
            return bleSyncResult ?: Outcome.Success(info.copy(secret = FountainSecret.fromHex("123456789abcdef0")))
        }

        override suspend fun uploadDrinks(accessoryId: Long, records: List<DrinkRecord>): Outcome<DrinkUploadResult> {
            uploads += records
            return uploadResult ?: Outcome.Success(DrinkUploadResult(records.size, 0, 0, null))
        }

        override fun observeLastSync(accessoryId: Long): Flow<Instant?> = lastSync

        override suspend fun recordSync(accessoryId: Long, at: Instant) {
            lastSync.value = at
        }
    }

    private class FakeBluetooth : FountainBluetooth {
        var availability = BluetoothAvailability.Ready
        var records: List<DrinkRecord> = emptyList()
        var failure: FountainLinkFailure? = null
        var mac: String? = null
        var secret: FountainSecret? = null
        var secretSeen: ByteArray? = null

        override fun availability() = availability

        override suspend fun readHistory(
            mac: String,
            secret: FountainSecret,
            onProgress: suspend (FountainLinkProgress) -> Unit,
        ): FountainReadResult {
            this.mac = mac
            this.secret = secret
            secretSeen = secret.bytes()
            onProgress(FountainLinkProgress.Searching)
            onProgress(FountainLinkProgress.Connected)
            failure?.let { return FountainReadResult.Failure(it) }
            onProgress(FountainLinkProgress.Reading(records.size))
            return FountainReadResult.Success(records)
        }
    }

    private class RecordingTimeline : TimelineRepository {
        val refreshed = mutableListOf<LocalDate>()

        override suspend fun refreshDay(day: LocalDate): Outcome<Unit> {
            refreshed += day
            return Outcome.Success(Unit)
        }

        override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> = emptyFlow()

        override fun observeHistory(query: HistoryQuery): Flow<List<TimelineItem>> = emptyFlow()

        override suspend fun fetchHistoryPage(query: HistoryQuery, cursor: HistoryCursor?): Outcome<HistoryPage> =
            Outcome.Success(HistoryPage(null))

        override fun observeRecentClips(deviceId: Long, limit: Int): Flow<List<TimelineItem>> = emptyFlow()

        override fun observeNewestWithMedia(deviceId: Long, limit: Int): Flow<List<TimelineItem>> = emptyFlow()

        override suspend fun refreshRecentClips(deviceId: Long, count: Int): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun getEvent(id: Long): Outcome<EventDetail> = Outcome.Failure(DomainError.NotFound("x", ""))
    }

    private companion object {
        const val ACCESSORY = 3003L
        const val EPOCH = 946_684_800L
    }
}

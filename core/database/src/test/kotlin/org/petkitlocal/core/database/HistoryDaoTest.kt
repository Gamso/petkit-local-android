package org.petkitlocal.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.database.dao.TimelineFilter
import org.petkitlocal.core.database.dao.TimelineKey
import org.petkitlocal.core.database.model.TimelineItemRow
import org.robolectric.RobolectricTestRunner

/**
 * Integration: History's pages written into the timeline cache, on a real SQLite database. A page
 * is authoritative over the range it covers and nothing else, so Home's whole-day cache and the
 * live updates stay consistent with paged data.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryDaoTest {
    private lateinit var db: PetkitDatabase
    private val dao get() = db.timelineDao()
    private val day = "2026-11-15"
    private val all = TimelineFilter(day)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PetkitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `the filters select by device, pet and chip, the chip matched as a whole key`() = runTest {
        dao.replaceDay(
            day,
            listOf(
                item(1, ts = 10.0, filters = listOf("toileting", "health_alert"), device = 1, pet = 7),
                item(2, ts = 20.0, filters = listOf("toileting"), device = 2, pet = 8),
                item(3, ts = 30.0, filters = listOf("feeding"), device = 2),
                // A key that `_` would match as a LIKE wildcard: it must not pass for health_alert.
                item(4, ts = 40.0, filters = listOf("healthXalert"), device = 1),
            ),
        )
        dao.replaceDay("2026-11-14", listOf(item(5, ts = 5.0, filters = listOf("toileting"), day = "2026-11-14")))

        suspend fun ids(filter: TimelineFilter) = dao.observeHistory(filter).first().map { it.id }

        assertEquals(listOf(4L, 3L, 2L, 1L), ids(all))
        assertEquals(listOf(2L, 1L), ids(all.copy(filterKey = "toileting")))
        assertEquals(listOf(1L), ids(all.copy(filterKey = "health_alert")))
        assertEquals(listOf(3L, 2L), ids(all.copy(deviceId = 2)))
        assertEquals(listOf(2L), ids(all.copy(petId = 8)))
        assertEquals(listOf(2L), ids(all.copy(deviceId = 2, filterKey = "toileting")))
    }

    @Test
    fun `pages fill the cache in order, each dropping the stale cards of its own range only`() = runTest {
        // What Home cached earlier: 6, 5, 4, 3, 2, 1. The server has since dropped 5 and 2.
        dao.replaceDay(day, (1L..6L).map { item(it, ts = it * 10.0) })

        dao.writePage(
            all,
            listOf(item(7, ts = 70.0), item(6, ts = 60.0), item(4, ts = 40.0)),
            newerThan = null,
            lastPage = false,
        )
        // Page 1 covers 7..4: 5 is gone, 3..1 are past it and kept until a page covers them.
        assertEquals(listOf(7L, 6L, 4L, 3L, 2L, 1L), dao.observeHistory(all).first().map { it.id })

        dao.writePage(
            all,
            listOf(item(3, ts = 30.0), item(1, ts = 10.0)),
            newerThan = TimelineKey(4, 40.0),
            lastPage = true,
        )
        // The last page covers everything after 4, to the end of the day.
        assertEquals(listOf(7L, 6L, 4L, 3L, 1L), dao.observeHistory(all).first().map { it.id })
    }

    @Test
    fun `a filtered page leaves the cards of other filters and other days alone`() = runTest {
        dao.replaceDay(day, listOf(item(1, ts = 10.0, filters = listOf("feeding")), item(2, ts = 20.0)))
        dao.replaceDay("2026-11-14", listOf(item(3, ts = 5.0, day = "2026-11-14")))

        // The server has no toileting card left that day.
        dao.writePage(all.copy(filterKey = "toileting"), emptyList(), newerThan = null, lastPage = true)

        assertEquals(listOf(1L), dao.observeDay(day).first().map { it.id })
        assertEquals(listOf(3L), dao.observeDay("2026-11-14").first().map { it.id })
    }

    @Test
    fun `an empty page that is not the last covers nothing and drops nothing`() = runTest {
        dao.replaceDay(day, listOf(item(1, ts = 10.0)))

        dao.writePage(all, emptyList(), newerThan = null, lastPage = false)

        assertEquals(listOf(1L), dao.observeHistory(all).first().map { it.id })
    }

    @Test
    fun `cards with the same time are ordered and bounded by id, and cards with none sort last`() = runTest {
        dao.replaceDay(day, listOf(item(1, ts = null), item(2, ts = 50.0), item(3, ts = 50.0), item(4, ts = 50.0)))

        // Page 1 ended on (50, 4); page 2 covers (50, 3) and below, and the server lost 3 and 1.
        dao.writePage(all, listOf(item(2, ts = 50.0)), newerThan = TimelineKey(4, 50.0), lastPage = true)

        assertEquals(listOf(4L, 2L), dao.observeHistory(all).first().map { it.id })
    }

    @Test
    fun `a page is seen by every observer of the day, and a live card by the paged list`() = runTest {
        dao.observeHistory(all.copy(filterKey = "toileting")).test {
            assertEquals(emptyList<Long>(), awaitItem().map { it.id })

            dao.writePage(all, listOf(item(2, ts = 20.0), item(1, ts = 10.0)), newerThan = null, lastPage = true)
            assertEquals(listOf(2L, 1L), awaitItem().map { it.id })

            // A live card replacing 2 (a visit's in-progress card becoming its summary).
            dao.upsertLive(item(3, ts = 30.0), replaces = listOf(2))
            assertEquals(listOf(3L, 1L), awaitItem().map { it.id })
        }
        assertEquals(listOf(3L, 1L), dao.observeDay(day).first().map { it.id })
    }

    @Test
    fun `a device's recent clips are its cards holding one, newest first across days, bounded`() = runTest {
        dao.replaceDay(
            day,
            listOf(item(1, ts = 10.0, clip = true), item(2, ts = 20.0), item(3, ts = 30.0, clip = true)),
        )
        dao.replaceDay("2026-11-14", listOf(item(4, ts = 5.0, clip = true, day = "2026-11-14")))
        dao.upsertLive(item(5, ts = 40.0, clip = true, device = 2), replaces = emptyList())

        assertEquals(listOf(3L, 1L, 4L), dao.observeRecentClips(deviceId = 1, limit = 6).first().map { it.id })
        assertEquals(listOf(3L, 1L), dao.observeRecentClips(deviceId = 1, limit = 2).first().map { it.id })
        assertEquals(listOf(5L), dao.observeRecentClips(deviceId = 2, limit = 6).first().map { it.id })
    }

    @Test
    fun `a device's newest cards replace its cached ones over the range they cover, whatever the day`() = runTest {
        // Cached: 6..1 of device 1 over two days, and a card of device 2. The server dropped 5 and 2.
        dao.replaceDay(day, (4L..6L).map { item(it, ts = it * 10.0) } + item(9, ts = 55.0, device = 2))
        dao.replaceDay("2026-11-14", (1L..3L).map { item(it, ts = it * 10.0, day = "2026-11-14") })

        dao.writeDeviceNewest(1, listOf(item(7, ts = 70.0), item(6, ts = 60.0), item(4, ts = 40.0)), complete = false)
        // 7..4 covered: 5 is gone; 3..1 are past the range and kept, and so is the other device's card.
        assertEquals(listOf(7L, 6L, 9L, 4L), dao.observeDay(day).first().map { it.id })
        assertEquals(listOf(3L, 2L, 1L), dao.observeDay("2026-11-14").first().map { it.id })

        val whole = listOf(70.0 to 7L, 60.0 to 6L, 40.0 to 4L, 30.0 to 3L, 10.0 to 1L).map { (ts, id) ->
            item(id, ts = ts, day = if (id < 4) "2026-11-14" else day)
        }
        dao.writeDeviceNewest(1, whole, complete = true)
        // The device's whole history: 2 is gone too.
        assertEquals(listOf(3L, 1L), dao.observeDay("2026-11-14").first().map { it.id })
        assertEquals(listOf(7L, 6L, 9L, 4L), dao.observeDay(day).first().map { it.id })
    }

    @Test
    fun `no newest card and more to come drops nothing`() = runTest {
        dao.replaceDay(day, listOf(item(1, ts = 10.0)))

        dao.writeDeviceNewest(1, emptyList(), complete = false)
        assertEquals(listOf(1L), dao.observeDay(day).first().map { it.id })

        dao.writeDeviceNewest(1, emptyList(), complete = true)
        assertEquals(emptyList<Long>(), dao.observeDay(day).first().map { it.id })
    }

    private fun item(
        id: Long,
        ts: Double?,
        filters: List<String> = listOf("toileting"),
        device: Long = 1,
        pet: Long? = null,
        day: String = this.day,
        clip: Boolean = false,
    ) = TimelineItemRow(
        id = id,
        day = day,
        kind = "visit",
        filtersJson = filters.joinToString(",", "[", "]") { "\"$it\"" },
        deviceId = device,
        deviceType = "t4",
        ts = ts,
        displayTs = ts,
        eventType = null,
        eventKind = null,
        label = null,
        petId = pet,
        petName = null,
        durationSec = null,
        weightG = null,
        factsJson = "[]",
        hasClip = clip,
    )
}

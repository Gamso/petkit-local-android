package org.petkitlocal.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.database.dao.DeviceDetailRows
import org.petkitlocal.core.database.model.ActionRow
import org.petkitlocal.core.database.model.DeviceRow
import org.petkitlocal.core.database.model.EntityRow
import org.petkitlocal.core.database.model.PetRow
import org.petkitlocal.core.database.model.TimelineItemRow
import org.robolectric.RobolectricTestRunner

/** Integration: the DAOs on a real (in-memory) SQLite database, on Robolectric. */
@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: PetkitDatabase

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
    fun `replacing the devices keeps the server's order and drops what it no longer lists`() = runTest {
        val dao = db.deviceDao()
        dao.replaceAll(
            listOf(device(2, position = 0), device(1, position = 1), device(3, position = 2)),
            mapOf(
                1L to DeviceDetailRows(listOf(entity(1, "b", 1), entity(1, "a", 0)), listOf(action(1, "clean"))),
                3L to DeviceDetailRows(listOf(entity(3, "x", 0)), emptyList()),
            ),
        )

        dao.replaceAll(listOf(device(1, position = 0), device(2, position = 1)), emptyMap())

        val devices = dao.observeDevices().first()
        assertEquals(listOf(1L, 2L), devices.map { it.device.id })
        // No fresh detail: the last known entities and actions stay.
        assertEquals(setOf("a", "b"), devices[0].entities.map { it.key }.toSet())
        assertEquals(listOf("clean"), devices[0].actions.map { it.key })
        assertNull(dao.getDevice(3))
    }

    @Test
    fun `a fresh detail replaces the device's entities and actions, and the flow emits it`() = runTest {
        val dao = db.deviceDao()
        dao.replaceAll(listOf(device(1)), mapOf(1L to DeviceDetailRows(listOf(entity(1, "old", 0)), emptyList())))

        dao.observeDevice(1).test {
            assertEquals(listOf("old"), awaitItem()!!.entities.map { it.key })

            dao.replaceAll(
                listOf(device(1).copy(online = false)),
                mapOf(1L to DeviceDetailRows(listOf(entity(1, "new", 0)), listOf(action(1, "reset")))),
            )

            // Room re-reads on its own thread once the write commits; skip any stale emission.
            var updated = awaitItem()!!
            while (updated.entities.map { it.key } != listOf("new")) updated = awaitItem()!!
            assertEquals(false, updated.device.online)
            assertEquals(listOf("new"), updated.entities.map { it.key })
            assertEquals(listOf("reset"), updated.actions.map { it.key })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pets are replaced as a whole, in order`() = runTest {
        val dao = db.petDao()
        dao.replaceAll(listOf(pet(1, 0), pet(2, 1)))
        dao.replaceAll(listOf(pet(2, 0)))

        assertEquals(listOf(2L), dao.observePets().first().map { it.id })
    }

    @Test
    fun `a day is replaced as a whole and read newest first`() = runTest {
        val dao = db.timelineDao()
        dao.replaceDay("2026-11-14", listOf(item(1, "2026-11-14", ts = 10.0)))
        dao.replaceDay("2026-11-15", listOf(item(2, "2026-11-15", ts = 20.0), item(3, "2026-11-15", ts = 30.0)))
        dao.replaceDay("2026-11-15", listOf(item(3, "2026-11-15", ts = 30.0), item(4, "2026-11-15", ts = 40.0)))

        assertEquals(listOf(4L, 3L), dao.observeDay("2026-11-15").first().map { it.id })
        assertEquals(listOf(1L), dao.observeDay("2026-11-14").first().map { it.id })
    }

    @Test
    fun `clearing the database forgets everything`() = runTest {
        db.deviceDao().replaceAll(listOf(device(1)), emptyMap())
        db.petDao().replaceAll(listOf(pet(1, 0)))

        db.clearAllTables()

        assertEquals(0, db.deviceDao().observeDevices().first().size)
        assertEquals(0, db.petDao().observePets().first().size)
    }

    private fun device(id: Long, position: Int = 0) = DeviceRow(
        id = id,
        position = position,
        type = "t4",
        model = "Pura Max",
        modelName = "Pura Max",
        generation = null,
        kind = "litter_box",
        online = true,
        transport = "http",
        lastSeen = 1.0,
        firmware = "1.0",
        isCamera = false,
        serial = null,
    )

    private fun entity(deviceId: Long, key: String, position: Int) = EntityRow(
        deviceId = deviceId,
        key = key,
        position = position,
        name = key,
        component = "sensor",
        valueJson = "1",
        display = null,
        unit = null,
        deviceClass = null,
        category = null,
        optionsJson = null,
        settable = false,
        min = null,
        max = null,
        step = null,
    )

    private fun action(deviceId: Long, key: String) = ActionRow(deviceId, key, 0, key, destructive = false)

    private fun pet(id: Long, position: Int) = PetRow(id, position, "Pet $id", "[]", null, 4000.0, false, null)

    private fun item(id: Long, day: String, ts: Double) = TimelineItemRow(
        id = id,
        day = day,
        kind = "visit",
        filtersJson = "[\"toileting\"]",
        deviceId = 1,
        deviceType = "t4",
        ts = ts,
        displayTs = ts,
        eventType = null,
        eventKind = null,
        label = null,
        petId = null,
        petName = null,
        durationSec = null,
        weightG = null,
        factsJson = "[]",
    )
}

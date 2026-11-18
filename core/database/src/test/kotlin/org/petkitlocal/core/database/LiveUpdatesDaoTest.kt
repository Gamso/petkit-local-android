package org.petkitlocal.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.database.dao.DeviceDetailRows
import org.petkitlocal.core.database.dao.EntityValuePatch
import org.petkitlocal.core.database.model.ActionRow
import org.petkitlocal.core.database.model.DeviceRow
import org.petkitlocal.core.database.model.EntityRow
import org.petkitlocal.core.database.model.TimelineItemRow
import org.robolectric.RobolectricTestRunner

/** Integration: the cache writes of live updates and command answers, on a real SQLite database. */
@RunWith(RobolectricTestRunner::class)
class LiveUpdatesDaoTest {
    private lateinit var db: PetkitDatabase

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PetkitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.deviceDao().replaceAll(
            listOf(device(1, position = 0).copy(serial = "SN-1"), device(2, position = 1)),
            mapOf(
                1L to DeviceDetailRows(listOf(entity(1, "volume", "5"), entity(1, "mode", "1", "normal")), emptyList()),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a summary links and unlinks accessories, and keeps the device's place and serial`() = runTest {
        val dao = db.deviceDao()
        val accessories = """[{"id":2,"type":"k3","kind":"purifier"}]"""

        dao.upsertSummary(device(1, position = 9).copy(accessoriesJson = accessories))
        dao.upsertSummary(device(2, position = 9).copy(connection = "ble", parentId = 1))

        val linked = dao.observeDevices().first().map { it.device }
        assertEquals(listOf(accessories, "[]"), linked.map { it.accessoriesJson })
        assertEquals(listOf("wifi", "ble"), linked.map { it.connection })
        assertEquals(listOf(null, 1L), linked.map { it.parentId })
        assertEquals(listOf(0, 1), linked.map { it.position })
        assertEquals("SN-1", linked.first().serial)

        dao.upsertSummary(device(1, position = 0))
        assertEquals("[]", dao.getDevice(1)!!.accessoriesJson)
    }

    @Test
    fun `a value patch changes that entity's value and display, and nothing else`() = runTest {
        val dao = db.deviceDao()

        dao.patchEntityValue(1, "mode", "2", "smart")
        dao.patchEntityValue(1, "unknown", "true", null)

        val entities = dao.observeDevice(1).first()!!.entities.associateBy { it.key }
        assertEquals("2", entities.getValue("mode").valueJson)
        assertEquals("smart", entities.getValue("mode").display)
        assertEquals("mode", entities.getValue("mode").name)
        assertEquals("5", entities.getValue("volume").valueJson)
        assertEquals(setOf("volume", "mode"), entities.keys)
    }

    @Test
    fun `a state message's values land together`() = runTest {
        val dao = db.deviceDao()

        dao.patchEntityValues(1, listOf(EntityValuePatch("mode", "2", "smart"), EntityValuePatch("volume", "9", null)))

        val entities = dao.observeDevice(1).first()!!.entities.associateBy { it.key }
        assertEquals("2", entities.getValue("mode").valueJson)
        assertEquals("9", entities.getValue("volume").valueJson)
    }

    @Test
    fun `a summary keeps a known device's place and serial, and puts a new one last`() = runTest {
        val dao = db.deviceDao()

        dao.upsertSummary(device(1, position = 9).copy(online = false, serial = null))
        dao.upsertSummary(device(7, position = 0))

        val devices = dao.observeDevices().first()
        assertEquals(listOf(1L, 2L, 7L), devices.map { it.device.id })
        with(devices[0]) {
            assertEquals(false, device.online)
            assertEquals("SN-1", device.serial)
            assertEquals(0, device.position)
            assertEquals(2, entities.size)
        }
        assertEquals(2, devices[2].device.position)
    }

    @Test
    fun `a detail replaces one device's entities and actions, and skips a device not cached`() = runTest {
        val dao = db.deviceDao()

        dao.replaceDetail(2, "SN-2", DeviceDetailRows(listOf(entity(2, "level", "50")), listOf(action(2))))
        dao.replaceDetail(9, "SN-9", DeviceDetailRows(listOf(entity(9, "level", "50")), emptyList()))

        val two = dao.observeDevice(2).first()!!
        assertEquals("SN-2", two.device.serial)
        assertEquals(listOf("level"), two.entities.map { it.key })
        assertEquals(listOf("clean"), two.actions.map { it.key })
        assertNull(dao.getDevice(9))
    }

    @Test
    fun `removing a device takes its entities with it`() = runTest {
        db.deviceDao().deleteDevice(1)

        assertEquals(listOf(2L), db.deviceDao().observeDevices().first().map { it.device.id })
    }

    @Test
    fun `a live card is upserted by id and drops the cards it replaces`() = runTest {
        val dao = db.timelineDao()
        dao.replaceDay("2026-11-15", listOf(item(10, "2026-11-15", 100.0), item(11, "2026-11-15", 200.0)))
        dao.replaceDay("2026-11-14", listOf(item(5, "2026-11-14", 50.0)))

        dao.upsertLive(item(12, "2026-11-15", 300.0), replaces = listOf(11, 5, 12))
        dao.upsertLive(item(10, "2026-11-15", 150.0), replaces = emptyList())

        assertEquals(listOf(12L, 10L), dao.observeDay("2026-11-15").first().map { it.id })
        assertEquals(150.0, dao.observeDay("2026-11-15").first().last().ts)
        assertEquals(emptyList<Long>(), dao.observeDay("2026-11-14").first().map { it.id })
    }

    private fun device(id: Long, position: Int) = DeviceRow(
        id = id,
        position = position,
        type = "w5",
        model = "EverSweet 3 Pro",
        modelName = "EverSweet 3 Pro",
        generation = null,
        kind = "fountain",
        online = true,
        transport = "http",
        lastSeen = 1.0,
        firmware = "1.0",
        isCamera = false,
        serial = null,
    )

    private fun entity(deviceId: Long, key: String, valueJson: String, display: String? = null) = EntityRow(
        deviceId = deviceId,
        key = key,
        position = 0,
        name = key,
        component = "number",
        valueJson = valueJson,
        display = display,
        unit = null,
        deviceClass = null,
        category = null,
        optionsJson = null,
        settable = true,
        min = null,
        max = null,
        step = null,
    )

    private fun action(deviceId: Long) = ActionRow(deviceId, "clean", 0, "Clean", destructive = false)

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

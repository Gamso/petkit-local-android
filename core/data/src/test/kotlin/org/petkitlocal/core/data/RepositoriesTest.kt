package org.petkitlocal.core.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.AccessoryRef
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.EntityCategory
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineStep
import org.robolectric.RobolectricTestRunner

/**
 * Integration: each repository against a scripted server and a real Room database. What the
 * flows emit is what the cache holds, so these also prove the offline behaviour: a failed
 * refresh leaves the last known data in place.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoriesTest {
    @get:Rule
    val server = ScriptedServer()

    private val devices by lazy { DeviceRepositoryImpl(server.api, server.db.deviceDao()) }
    private val pets by lazy { PetRepositoryImpl(server.api, server.db.petDao(), Clock.systemUTC()) { server.base } }
    private val timeline by lazy {
        TimelineRepositoryImpl(server.api, server.db.timelineDao(), Clock.systemUTC()) { server.base }
    }

    @Test
    fun `a device refresh caches the list and each detail, typed`() = runTest {
        server.on("devices", """{"devices":[${summary(1002, "litter_box")},${summary(9001, "robot")}]}""")
        server.on("devices/1002", detail(1002, "litter_box"))
        server.on("devices/9001", detail(9001, "robot"))

        assertEquals(Outcome.Success(Unit), devices.refresh())

        val cached = devices.observeDevices().first()
        assertEquals(listOf(1002L, 9001L), cached.map { it.id })
        val box = cached[0]
        assertEquals(DeviceFamily.LitterBox, box.family)
        assertEquals(DeviceTransport.Http, box.transport)
        assertEquals(Instant.ofEpochMilli(1_794_732_641_452), box.lastSeen)
        assertEquals("SN-1002", box.serial)
        assertEquals(
            listOf("device_status", "error", "waste_bin", "options", "blob", "mystery"),
            box.entities.map { it.key },
        )
        with(box.entity("device_status")!!) {
            assertEquals(EntityValue.Number(-1.0), value)
            assertEquals("idle", display)
            assertTrue(isPrimary)
        }
        assertEquals(EntityValue.None, box.entity("error")!!.value)
        assertEquals(EntityValue.Bool(true), box.entity("waste_bin")!!.value)
        with(box.entity("options")!!) {
            assertEquals(EntityValue.Text("bentonite"), value)
            assertEquals(listOf("bentonite", "tofu"), options)
            assertEquals(EntityComponent.Select, component)
            assertEquals(EntityCategory.Config, category)
        }
        assertEquals(EntityValue.Other("""{"a":1}"""), box.entity("blob")!!.value)
        assertEquals(EntityComponent.Unknown, box.entity("mystery")!!.component)
        assertEquals(EntityCategory.Diagnostic, box.entity("mystery")!!.category)
        assertEquals(listOf("cleaning_start", "reset"), box.actions.map { it.key })
        assertEquals(true, box.actions[1].destructive)
        // A kind this app does not know is the generic family, not an error.
        assertEquals(DeviceFamily.Other, cached[1].family)
    }

    @Test
    fun `a device is named by its generation, and by its model from a server that does not send one`() = runTest {
        val named = """"model":"Pura Max","model_name":"Pura Max 2","generation":2,"""
        server.on(
            "devices",
            """{"devices":[${summary(1002, "litter_box").replace(""""model":"Model 1002",""", named)},""" +
                """${summary(1003, "litter_box")}]}""",
        )
        server.on("devices/1002", detail(1002, "litter_box").replace(""""model":"Model 1002",""", named))
        server.on("devices/1003", detail(1003, "litter_box"))

        devices.refresh()

        val (generation2, older) = devices.observeDevices().first()
        assertEquals("Pura Max 2", generation2.modelName)
        assertEquals(2, generation2.generation)
        assertEquals("Model 1003", older.modelName)
        assertNull(older.generation)
    }

    @Test
    fun `a device says whether it can be talked through, and a server that does not say is too old`() = runTest {
        fun withTalk(id: Long, talk: String) =
            summary(id, "litter_box").replace(""""is_camera":false,""", """"is_camera":true,"talk":"$talk",""")
        server.on(
            "devices",
            """{"devices":[${withTalk(1003, "available")},${withTalk(1004, "needs_patcher")},""" +
                """${withTalk(1005, "device_unreachable")},${withTalk(1006, "unsupported")},""" +
                """${withTalk(1007, "on_mars")},${summary(1008, "litter_box")}]}""",
        )
        (1003L..1008L).forEach { server.on("devices/$it", detail(it, "litter_box")) }

        devices.refresh()

        assertEquals(
            listOf(
                TalkAvailability.Available,
                TalkAvailability.NeedsPatcher,
                TalkAvailability.DeviceUnreachable,
                TalkAvailability.Unsupported,
                TalkAvailability.Unknown,
                TalkAvailability.ServerTooOld,
            ),
            devices.observeDevices().first().map { it.talk },
        )
    }

    @Test
    fun `a Bluetooth accessory names its parent, which lists it, and the first purifier is the K3`() = runTest {
        val k3 = """{"id":4001,"type":"k3","model":"Pura Air K3","kind":"purifier","online":true,
           "transport":"http","last_seen":null,"firmware":null,"is_camera":false,
           "connection":"ble","parent_id":1002,"accessories":[]}"""
        val box = summary(1002, "litter_box").replace(
            "\"accessories\":[]",
            """"accessories":[{"id":3003,"type":"ctw3","kind":"fountain"},{"id":4001,"type":"k3","kind":"purifier"},
               {"id":4002,"type":"k3","kind":"purifier"},{"id":5000,"type":"x9","kind":"lamp"}]""",
        )
        server.on("devices", """{"devices":[$box,$k3]}""")
        server.on("devices/1002", detail(1002, "litter_box"))
        server.on("devices/4001", detail(4001, "purifier"))

        devices.refresh()

        val (parent, accessory) = devices.observeDevices().first()
        assertEquals(DeviceConnection.Wifi, parent.connection)
        assertEquals(
            listOf(
                AccessoryRef(3003, "ctw3", DeviceFamily.Fountain),
                AccessoryRef(4001, "k3", DeviceFamily.Purifier),
                AccessoryRef(4002, "k3", DeviceFamily.Purifier),
                AccessoryRef(5000, "x9", DeviceFamily.Other),
            ),
            parent.accessories,
        )
        assertEquals(4001L, parent.puraAirId)
        assertEquals(DeviceConnection.Ble, accessory.connection)
        assertTrue(accessory.isAccessory)
        assertEquals(1002L, accessory.parentId)
        assertEquals(emptyList<AccessoryRef>(), accessory.accessories)
        assertNull(accessory.puraAirId)
    }

    @Test
    fun `a detail that fails keeps that device's last known entities, and a gone device leaves`() = runTest {
        server.on("devices", """{"devices":[${summary(1, "feeder")},${summary(2, "fountain")}]}""")
        server.on("devices/1", detail(1, "feeder"))
        server.on("devices/2", detail(2, "fountain"))
        devices.refresh()

        server.on("devices", """{"devices":[${summary(1, "feeder", online = false)}]}""")
        server.on("devices/1", """{"error":"boom","message":"boom"}""", code = 500)

        assertEquals(Outcome.Success(Unit), devices.refresh())
        val cached = devices.observeDevices().first()
        assertEquals(listOf(1L), cached.map { it.id })
        assertEquals(false, cached[0].online)
        assertEquals("SN-1", cached[0].serial)
        assertEquals(6, cached[0].entities.size)
        assertNull(devices.observeDevice(2).first())
    }

    @Test
    fun `an unreachable server is a network failure and the cache stays as it was`() = runTest {
        server.on("devices", """{"devices":[${summary(1, "feeder")}]}""")
        server.on("devices/1", detail(1, "feeder"))
        devices.refresh()

        server.goOffline()

        assertTrue((devices.refresh() as Outcome.Failure).error is DomainError.Network)
        assertTrue((pets.refresh() as Outcome.Failure).error is DomainError.Network)
        assertEquals(listOf(1L), devices.observeDevices().first().map { it.id })
    }

    @Test
    fun `pets are cached in order, with their photo on the configured server`() = runTest {
        server.on(
            "pets",
            """{"pets":[
                {"id":1,"name":"Mochi","device_ids":[1002],"entered_weight_g":4200,"weight_g":4333,
                 "weight_learned":true,"photo":"/app/v1/pets/1/photo"},
                {"id":2,"name":"Tigrou","device_ids":[],"entered_weight_g":6000,"weight_g":null,
                 "weight_learned":false,"photo":null}]}""",
        )

        assertEquals(Outcome.Success(Unit), pets.refresh())

        val cached = pets.observePets().first()
        assertEquals(listOf("Mochi", "Tigrou"), cached.map { it.name })
        assertEquals(server.base.resolve("pets/1/photo").toString(), cached[0].photoUrl)
        assertTrue(cached[0].photoUrl!!.contains("/petkit/app/v1/pets/1/photo"))
        assertEquals(listOf(1002L), cached[0].deviceIds)
        assertEquals(4333.0, cached[0].weightG)
        // No learned weight yet: the entered one is what a visit is compared with.
        assertEquals(6000.0, cached[1].weightG)
        assertNull(cached[1].photoUrl)
    }

    @Test
    fun `a day is read across every page and replaces that day only`() = runTest {
        val day = LocalDate.of(2026, 11, 15)
        server.on("timeline?day=2026-11-15&limit=100", """{"items":[${item(3)},${item(2)}],"next_cursor":"c1"}""")
        server.on("timeline?day=2026-11-15&limit=100&cursor=c1", """{"items":[${item(1)}],"next_cursor":null}""")

        assertEquals(Outcome.Success(Unit), timeline.refreshDay(day))

        val cached = timeline.observeDay(day).first()
        assertEquals(listOf(3L, 2L, 1L), cached.map { it.id })
        with(cached[0]) {
            assertEquals(setOf(TimelineCategory.Toileting, TimelineCategory.HealthAlert), categories)
            assertEquals(PetRef(1, "Mochi"), pet)
            assertEquals(186, durationSec)
            assertEquals(Instant.ofEpochSecond(1_794_719_773), displayTs)
            assertEquals(listOf("litter 91%"), facts)
            assertEquals(
                listOf(
                    TimelineStep(31, Instant.ofEpochSecond(1_794_719_780), "Weight check", detail = true),
                    TimelineStep(32, null, "Auto cleaning completed", detail = false),
                ),
                steps,
            )
        }
        assertEquals(emptyList<Any>(), timeline.observeDay(day.minusDays(1)).first())
    }

    @Test
    fun `a drink without a duration, as petkit-local sends it, is cached with none`() = runTest {
        // Gamso/petkit-local#97: a fountain's drink comes with `duration_sec: null` for now.
        val day = LocalDate.of(2026, 11, 15)
        server.on("timeline?day=2026-11-15&limit=100", """{"items":[${untimedDrink(4)}],"next_cursor":null}""")

        assertEquals(Outcome.Success(Unit), timeline.refreshDay(day))

        val drink = timeline.observeDay(day).first().single()
        assertEquals(setOf(TimelineCategory.Drinking), drink.categories)
        assertEquals(PetRef(2, "Tigrou"), drink.pet)
        assertNull(drink.durationSec)
    }

    @Test
    fun `what a feed served is cached from its card, per hopper on a Dual-Hopper`() = runTest {
        val day = LocalDate.of(2026, 11, 15)
        val cards = listOf(
            feed(5, """{"total":3,"hopper1":2,"hopper2":1}"""),
            // A hopper the report left out gave none.
            feed(6, """{"total":2,"hopper1":null,"hopper2":2}"""),
            // A single hopper: the total alone, in its own unit.
            feed(7, """{"total":10,"hopper1":null,"hopper2":null}"""),
            // A meal that put nothing out is 0; a card that says nothing is null.
            feed(8, """{"total":0,"hopper1":null,"hopper2":null}"""),
            feed(9, "null"),
            // A server older than `served` leaves the key out.
            untimedDrink(10),
        )
        server.on("timeline?day=2026-11-15&limit=100", """{"items":[${cards.joinToString(",")}],"next_cursor":null}""")

        assertEquals(Outcome.Success(Unit), timeline.refreshDay(day))

        val served = timeline.observeDay(day).first().associate { it.id to it.served }
        assertEquals(
            mapOf(
                5L to Portions.Dual(2, 1),
                6L to Portions.Dual(0, 2),
                7L to Portions.Single(10),
                8L to Portions.Single(0),
                9L to null,
                10L to null,
            ),
            served,
        )
    }

    @Test
    fun `a page that fails leaves the cached day as it was`() = runTest {
        val day = LocalDate.of(2026, 11, 15)
        server.on("timeline?day=2026-11-15&limit=100", """{"items":[${item(1)}],"next_cursor":null}""")
        timeline.refreshDay(day)

        server.on("timeline?day=2026-11-15&limit=100", """{"items":[${item(2)}],"next_cursor":"c1"}""")
        server.on("timeline?day=2026-11-15&limit=100&cursor=c1", """{"error":"x","message":"x"}""", code = 503)

        assertTrue((timeline.refreshDay(day) as Outcome.Failure).error is DomainError.Server)
        assertEquals(listOf(1L), timeline.observeDay(day).first().map { it.id })
    }

    @Test
    fun `clearing the session cache forgets every cached device, pet and card`() = runTest {
        server.on("devices", """{"devices":[${summary(1, "feeder")}]}""")
        server.on("devices/1", detail(1, "feeder"))
        devices.refresh()

        DeviceSessionCache(server.db).clear()

        assertEquals(emptyList<Any>(), devices.observeDevices().first())
    }

    private fun summary(id: Long, kind: String, online: Boolean = true) =
        """{"id":$id,"type":"t4","model":"Model $id","kind":"$kind","online":$online,"transport":"http",
           "last_seen":1794732641.452,"firmware":"1.652","is_camera":false,
           "connection":"wifi","parent_id":null,"accessories":[]}"""

    private fun detail(id: Long, kind: String) = """{"device":{"id":$id,"type":"t4","model":"Model $id",
        "kind":"$kind","online":true,"transport":"http","last_seen":1794732641.452,"firmware":"1.652",
        "is_camera":false,"connection":"wifi","parent_id":null,"accessories":[],"serial":"SN-$id",
        "entities":[
          ${entity("device_status", "sensor", "-1", display = "\"idle\"")},
          ${entity("error", "sensor", "null")},
          ${entity("waste_bin", "binary_sensor", "true", deviceClass = "\"problem\"")},
          ${entity(
        "options",
        "select",
        "\"bentonite\"",
        category = "\"config\"",
        options = """["bentonite","tofu"]""",
    )},
          ${entity("blob", "sensor", """{"a":1}""")},
          ${entity("mystery", "gauge", "1", category = "\"new_category\"")}],
        "actions":[{"key":"cleaning_start","name":"Clean","destructive":false},
                   {"key":"reset","name":"Reset","destructive":true}]}}"""

    @Suppress("LongParameterList")
    private fun entity(
        key: String,
        component: String,
        value: String,
        display: String = "null",
        deviceClass: String = "null",
        category: String = "null",
        options: String = "null",
    ) = """{"key":"$key","name":"$key","component":"$component","value":$value,"display":$display,"unit":null,
        "device_class":$deviceClass,"category":$category,"options":$options,"settable":false,"min":null,
        "max":null,"step":null}"""

    private fun untimedDrink(id: Long) = """{"id":$id,"kind":"event","filters":["drinking"],
        "device_id":3001,"device_type":"w7h","ts":${1_794_719_959 + id},"display_ts":${1_794_719_959 + id},
        "event_type":"drink","event_kind":"drink","label":"Drink","pet":{"id":2,"name":"Tigrou"},
        "duration_sec":null,"weight_g":null,"facts":[],"media":null,"steps":[]}"""

    private fun feed(id: Long, served: String) = """{"id":$id,"kind":"event","filters":["feeding"],
        "device_id":2004,"device_type":"d4sh","ts":${1_794_719_959 + id},"display_ts":${1_794_719_959 + id},
        "event_type":"4","event_kind":"feeding","label":"Feeding completed","pet":null,
        "duration_sec":null,"weight_g":null,"served":$served,"facts":[],"media":null,"steps":[]}"""

    private fun item(id: Long) = """{"id":$id,"kind":"visit","filters":["toileting","health_alert","later_chip"],
        "device_id":1002,"device_type":"t4","ts":${1_794_719_959 + id},"display_ts":1794719773,
        "event_type":"10","event_kind":"toilet_visit","label":null,"pet":{"id":1,"name":"Mochi"},
        "duration_sec":186,"weight_g":6065,"facts":["litter 91%"],"media":null,
        "steps":[{"id":${id}1,"ts":1794719780,"label":"Weight check","detail":true},
                 {"id":${id}2,"ts":null,"label":"Auto cleaning completed","detail":false}]}"""
}

package org.petkitlocal.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.database.PetkitDatabase
import org.petkitlocal.core.database.dao.DeviceDetailRows
import org.petkitlocal.core.database.model.DeviceRow
import org.petkitlocal.core.database.model.EntityRow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.network.PetkitHttpClient
import org.robolectric.RobolectricTestRunner

/**
 * Integration: actions and entity writes against a scripted server and a real Room database.
 * The requests are checked as the server reads them (method, path, body), and the cache only
 * ever holds what the server answered (ADR 0002).
 */
@RunWith(RobolectricTestRunner::class)
class DeviceCommandRepositoryImplTest {
    private val server = MockWebServer()
    private lateinit var base: HttpUrl
    private lateinit var db: PetkitDatabase
    private lateinit var repository: DeviceCommandRepositoryImpl

    @Before
    fun setUp() = runTest {
        server.start()
        base = server.url("/app/v1/")
        val api = PetkitHttpClient.api(PetkitHttpClient.okHttpClient({ base }, { "token" }))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PetkitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DeviceCommandRepositoryImpl(api, db.deviceDao())
        db.deviceDao().replaceAll(
            listOf(DeviceRow(3001, 0, "w7h", "EverSweet Ultra AI", "fountain", true, "mqtt", null, null, true, null)),
            mapOf(
                3001L to DeviceDetailRows(
                    entities = listOf(
                        entity("child_lock", "switch", "false"),
                        entity("volume", "number", "5"),
                        entity("flow_mode", "select", "1", display = "continuous"),
                    ),
                    actions = emptyList(),
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    @Test
    fun `an action is posted without confirmation, and says how it was delivered`() = runTest {
        server.enqueue(json("""{"ok":true,"action":"fountain_flush","delivered":"mqtt"}"""))

        assertEquals(Outcome.Success(CommandDelivery.Mqtt), repository.runAction(3001, "fountain_flush"))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/app/v1/devices/3001/actions/fountain_flush", request.url.encodedPath)
        assertEquals("{}", request.body?.utf8())
        assertEquals("Bearer token", request.headers["Authorization"])
    }

    @Test
    fun `a destructive action is posted with confirm true`() = runTest {
        server.enqueue(json("""{"ok":true,"action":"fountain_drain","delivered":"queued"}"""))

        assertEquals(Outcome.Success(CommandDelivery.Queued), repository.runAction(3001, "fountain_drain", true))

        assertEquals("""{"confirm":true}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun `a refusal is the server's own message`() = runTest {
        server.enqueue(
            json(
                """{"error":"confirmation_required","message":"Drain cannot be undone; send confirm"}""",
                code = 422,
            ),
        )

        val result = repository.runAction(3001, "fountain_drain")

        assertEquals(
            Outcome.Failure(
                DomainError.Validation(null, "confirmation_required", "Drain cannot be undone; send confirm"),
            ),
            result,
        )
    }

    @Test
    fun `a write sends the typed value and caches what the server now holds`() = runTest {
        val stored = entityJson("flow_mode", "select", "2", "intermittent")
        server.enqueue(json("""{"ok":true,"delivered":"mqtt","entity":$stored}"""))

        val result = repository.setEntity(3001, "flow_mode", EntityValue.Text("intermittent"))

        assertEquals(Outcome.Success(CommandDelivery.Mqtt), result)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/app/v1/devices/3001/entities/flow_mode", request.url.encodedPath)
        assertEquals("""{"value":"intermittent"}""", request.body?.utf8())
        val cached = db.deviceDao().observeDevice(3001).first()!!.entities.single { it.key == "flow_mode" }
        assertEquals("2", cached.valueJson)
        assertEquals("intermittent", cached.display)
    }

    @Test
    fun `numbers go out as integers when whole, and booleans as booleans`() = runTest {
        server.enqueue(json("""{"ok":true,"delivered":"queued","entity":${entityJson("volume", "number", "7")}}"""))
        val lock = entityJson("child_lock", "switch", "true")
        server.enqueue(json("""{"ok":true,"delivered":"local","entity":$lock}"""))
        server.enqueue(json("""{"ok":true,"delivered":"queued","entity":${entityJson("volume", "number", "7.5")}}"""))

        assertEquals(
            Outcome.Success(CommandDelivery.Queued),
            repository.setEntity(3001, "volume", EntityValue.Number(7.0)),
        )
        assertEquals(
            Outcome.Success(CommandDelivery.Local),
            repository.setEntity(3001, "child_lock", EntityValue.Bool(true)),
        )
        repository.setEntity(3001, "volume", EntityValue.Number(7.5))

        assertEquals("""{"value":7}""", server.takeRequest().body?.utf8())
        assertEquals("""{"value":true}""", server.takeRequest().body?.utf8())
        assertEquals("""{"value":7.5}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun `a refused write leaves the cache as it was`() = runTest {
        server.enqueue(json("""{"error":"refused","message":"Volume must be between 1 and 9"}""", code = 422))

        val result = repository.setEntity(3001, "volume", EntityValue.Number(12.0))

        assertEquals(
            Outcome.Failure(DomainError.Validation(null, "refused", "Volume must be between 1 and 9")),
            result,
        )
        val cached = db.deviceDao().observeDevice(3001).first()!!.entities.single { it.key == "volume" }
        assertEquals("5", cached.valueJson)
    }

    @Test
    fun `an unreachable server is a network failure`() = runTest {
        server.close()

        assertTrue(
            (repository.runAction(3001, "fountain_flush") as Outcome.Failure).error is DomainError.Network,
        )
    }

    @Test
    fun `a delivery this app does not know promises the least`() {
        assertEquals(CommandDelivery.Queued, commandDelivery("carrier_pigeon"))
    }

    private fun json(body: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(body)
        .build()

    private fun entity(key: String, component: String, valueJson: String, display: String? = null) = EntityRow(
        deviceId = 3001,
        key = key,
        position = 0,
        name = key,
        component = component,
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

    private fun entityJson(key: String, component: String, value: String, display: String? = null) =
        """{"key":"$key","name":"$key","component":"$component","value":$value,""" +
            """"display":${display?.let { "\"$it\"" } ?: "null"},"unit":null,"device_class":null,""" +
            """"category":null,"options":null,"settable":true,"min":null,"max":null,"step":null}"""
}

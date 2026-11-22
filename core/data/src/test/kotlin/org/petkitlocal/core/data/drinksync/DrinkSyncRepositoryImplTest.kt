package org.petkitlocal.core.data.drinksync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.petkitlocal.core.datastore.DrinkSyncStore
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.domain.drinksync.DrinkUploadResult
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient

/** Integration: the two sync routes with the real Retrofit service against a scripted server. */
class DrinkSyncRepositoryImplTest {
    @TempDir
    lateinit var dir: File

    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<String>()
    private var answer: Pair<Int, String> = 200 to "{}"
    private lateinit var api: PetkitApi

    @BeforeEach
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}".trim()
                return MockResponse.Builder()
                    .code(answer.first)
                    .addHeader("Content-Type", "application/json")
                    .body(answer.second)
                    .build()
            }
        }
        server.start()
        val base = server.url("/app/v1/")
        api = PetkitHttpClient.api(PetkitHttpClient.okHttpClient({ base }, { "token" }))
    }

    @AfterEach
    fun stop() = server.close()

    private fun kotlinx.coroutines.test.TestScope.repository() = DrinkSyncRepositoryImpl(
        api,
        DrinkSyncStore(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                File(dir, "drink_sync.preferences_pb")
            },
        ),
    )

    @Test
    fun `the sync details carry the secret as bytes`() = runTest {
        answer = 200 to """{"id":3003,"type":"ctw3","mac":"AA:BB:CC:DD:EE:FF","sync_supported":true,
            "secret":"00000000deadbeef","history_cursor":1763190000,"clock_epoch":946684800}"""

        val info = (repository().bleSync(3003) as Outcome.Success).value

        assertEquals("GET /app/v1/accessories/3003/ble-sync", requests.single())
        assertEquals("AA:BB:CC:DD:EE:FF", info.mac)
        assertEquals(1_763_190_000L, info.historyCursor)
        assertArrayEquals(
            byteArrayOf(0, 0, 0, 0, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()),
            info.secret!!.bytes(),
        )
        assertEquals("FountainSecret(<redacted>)", info.secret.toString())
    }

    @Test
    fun `secret_unknown is the server's refusal with its message`() = runTest {
        answer = 409 to """{"error":"secret_unknown","message":"Import from PetKit in the panel."}"""

        val failure = repository().bleSync(3003)

        assertEquals(
            Outcome.Failure(DomainError.Server(409, "secret_unknown", "Import from PetKit in the panel.")),
            failure,
        )
    }

    @Test
    fun `records go up as the fountain sent them, from the phone`() = runTest {
        answer = 200 to """{"stored":2,"duplicates":0,"skipped":0,"cursor":1763193600}"""

        val result = repository().uploadDrinks(3003, listOf(DrinkRecord(816_508_800, 12), DrinkRecord(816_512_400, 30)))

        assertEquals(Outcome.Success(DrinkUploadResult(2, 0, 0, 1_763_193_600L)), result)
        assertEquals(
            "POST /app/v1/accessories/3003/drinks " +
                """{"records":[{"work_time":816508800,"stay_time":12},""" +
                """{"work_time":816512400,"stay_time":30}],"source":"phone-ble"}""",
            requests.single(),
        )
    }

    @Test
    fun `the last sync is kept on the phone`() = runTest {
        val repository = repository()
        assertNull(repository.observeLastSync(3003).first())

        repository.recordSync(3003, Instant.parse("2026-11-15T09:00:00Z"))

        assertEquals(Instant.parse("2026-11-15T09:00:00Z"), repository.observeLastSync(3003).first())
    }
}

package org.petkitlocal.core.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.PetStats
import org.petkitlocal.core.domain.model.WeightSample
import org.robolectric.RobolectricTestRunner

/**
 * Integration: a pet's page data against a scripted server and a real Room database. Its
 * statistics and recent visits come from `GET /pets/{id}`; its weighed visits from the timeline,
 * page after page, back to the start of the range.
 */
@RunWith(RobolectricTestRunner::class)
class PetRepositoryTest {
    @get:Rule
    val server = ScriptedServer()

    private val now = Instant.ofEpochSecond(NOW)
    private val pets by lazy {
        PetRepositoryImpl(server.api, server.db.petDao(), Clock.fixed(now, ZoneOffset.UTC)) { server.base }
    }

    @Test
    fun `a pet's statistics and recent visits are cached with when they were read`() = runTest {
        server.on("pets/1", petDetail(name = "Mochi"))

        assertEquals(Outcome.Success(Unit), pets.refreshPet(1))

        val activity = pets.observeActivity(1).first()!!
        assertEquals(
            PetStats(
                medianWeightG = 4310.0,
                visitsToday = 3,
                lastVisitAt = Instant.ofEpochSecond(NOW - 600),
                lastVisitWeightG = 4290.0,
                lastVisitDurationSec = 95,
                lastDeviceId = 1002,
            ),
            activity.stats,
        )
        assertEquals(now, activity.fetchedAt)
        assertEquals(listOf(12L, 11L), activity.recentVisits.map { it.id })
        assertEquals(PetRef(1, "Mochi"), activity.recentVisits.first().pet)
        // The pet's row too, which the list had not read yet.
        assertEquals(listOf("Mochi"), pets.observePets().first().map { it.name })
    }

    @Test
    fun `reading one pet keeps its place in the list and renames it`() = runTest {
        server.on("pets", """{"pets":[${pet(1, "Mochi")},${pet(2, "Tigrou")}]}""")
        pets.refresh()
        server.on("pets/1", petDetail(name = "Mochi-chan"))

        pets.refreshPet(1)

        assertEquals(listOf("Mochi-chan", "Tigrou"), pets.observePets().first().map { it.name })
    }

    @Test
    fun `a pet the server no longer has leaves the cache, with its activity and weights`() = runTest {
        server.on("pets/1", petDetail(name = "Mochi"))
        pets.refreshPet(1)
        server.on(
            "timeline?kind=toileting&pet=1&limit=100",
            """{"items":[${visit(11, NOW - 60, 4300)}],"next_cursor":null}""",
        )
        pets.refreshWeights(1, now.minusSeconds(DAY))
        server.on("pets/1", """{"error":"pet_not_found","message":"no such pet"}""", code = 404)

        val result = pets.refreshPet(1)

        assertTrue((result as Outcome.Failure).error is DomainError.NotFound)
        assertEquals(emptyList<Any>(), pets.observePets().first())
        assertNull(pets.observeActivity(1).first())
        assertEquals(emptyList<WeightSample>(), pets.observeWeights(1, Instant.EPOCH).first())
    }

    @Test
    fun `a failed read leaves the cache as it was`() = runTest {
        server.on("pets/1", petDetail(name = "Mochi"))
        pets.refreshPet(1)
        server.on("pets/1", """{"error":"unavailable","message":"x"}""", code = 503)

        assertTrue((pets.refreshPet(1) as Outcome.Failure).error is DomainError.Server)
        assertEquals(3, pets.observeActivity(1).first()?.stats?.visitsToday)
    }

    @Test
    fun `weights follow the cursor back to the start of the range, weighed visits only`() = runTest {
        val since = now.minusSeconds(10 * DAY)
        val base = "timeline?kind=toileting&pet=1&limit=100"
        server.on(
            base,
            """{"items":[${visit(30, NOW - DAY, 4300)},${visit(29, NOW - 2 * DAY, null)},
               ${visit(28, NOW - 3 * DAY, 4280, pet = 2)}],"next_cursor":"c1"}""",
        )
        server.on(
            "$base&cursor=c1",
            """{"items":[${visit(27, NOW - 5 * DAY, 4250)},${visit(26, NOW - 11 * DAY, 4200)}],"next_cursor":"c2"}""",
        )

        assertEquals(Outcome.Success(Unit), pets.refreshWeights(1, since))

        // The page that reached past the start ends the reading: no third page is asked for.
        assertEquals(listOf(base, "$base&cursor=c1"), server.requests.filter { it.startsWith("timeline") })
        assertEquals(
            listOf(
                WeightSample(27, Instant.ofEpochSecond(NOW - 5 * DAY), 4250),
                WeightSample(30, Instant.ofEpochSecond(NOW - DAY), 4300),
            ),
            pets.observeWeights(1, since).first(),
        )
    }

    @Test
    fun `a visit the server no longer has leaves the weights it covered`() = runTest {
        val since = now.minusSeconds(10 * DAY)
        val base = "timeline?kind=toileting&pet=1&limit=100"
        server.on(
            base,
            """{"items":[${visit(30, NOW - DAY, 4300)},${visit(29, NOW - 2 * DAY, 4310)}],"next_cursor":null}""",
        )
        pets.refreshWeights(1, since)
        server.on(base, """{"items":[${visit(30, NOW - DAY, 4300)}],"next_cursor":null}""")

        pets.refreshWeights(1, since)

        assertEquals(listOf(30L), pets.observeWeights(1, since).first().map { it.visitId })
    }

    @Test
    fun `a failed page leaves the weights as they were`() = runTest {
        val since = now.minusSeconds(10 * DAY)
        val base = "timeline?kind=toileting&pet=1&limit=100"
        server.on(base, """{"items":[${visit(30, NOW - DAY, 4300)}],"next_cursor":null}""")
        pets.refreshWeights(1, since)
        server.on(base, """{"items":[],"next_cursor":"c1"}""")
        server.on("$base&cursor=c1", """{"error":"unavailable","message":"x"}""", code = 503)

        assertTrue(pets.refreshWeights(1, since) is Outcome.Failure)
        assertEquals(listOf(30L), pets.observeWeights(1, since).first().map { it.visitId })
    }

    private fun pet(id: Long, name: String) = """{"id":$id,"name":"$name","device_ids":[1002],"entered_weight_g":4200,
        "weight_g":4310,"weight_learned":true,"photo":null}"""

    private fun petDetail(name: String) = """{"pet":{"id":1,"name":"$name","device_ids":[1002],"entered_weight_g":4200,
        "weight_g":4310,"weight_learned":true,"photo":"/app/v1/pets/1/photo",
        "stats":{"median_weight_g":4310,"visits_today":3,"last_visit_ts":${NOW - 600},"last_visit_weight_g":4290,
                 "last_visit_duration_sec":95.4,"last_device_id":1002},
        "recent_visits":[${visit(12, NOW - 600, 4290)},${visit(11, NOW - 7200, 4330)}]}}"""

    private fun visit(id: Long, at: Long, weight: Int?, pet: Long = 1) = """{"id":$id,"kind":"visit",
        "filters":["toileting"],"device_id":1002,"device_type":"t5","ts":${at + 90},"display_ts":$at,
        "event_type":"10","event_kind":"toilet_visit","label":null,"pet":{"id":$pet,"name":"Mochi"},
        "duration_sec":90,"weight_g":${weight ?: "null"},"facts":[],"media":null,"steps":[]}"""

    private companion object {
        const val NOW = 1_794_719_959L
        const val DAY = 86_400L
    }
}

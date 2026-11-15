package org.petkitlocal.core.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.DeferredFeedRequest
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient

/**
 * Integration: the feeding repository with the real Retrofit service against a scripted server
 * that answers by method and path, with bodies shaped like the examples of api/openapi.yaml.
 */
class FeedingRepositoryImplTest {
    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, Pair<Int, String>>()
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    private lateinit var api: PetkitApi
    private lateinit var repository: FeedingRepositoryImpl
    private val store = FeedingStore()

    @BeforeEach
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = "${request.method} ${request.url.encodedPath.removePrefix("/app/v1/")}"
                requests += key to (request.body?.utf8() ?: "")
                val (code, body) = routes[key] ?: (404 to """{"error":"not_found","message":"$key"}""")
                return MockResponse.Builder()
                    .code(code)
                    .addHeader("Content-Type", "application/json")
                    .body(body)
                    .build()
            }
        }
        server.start()
        val base = server.url("/app/v1/")
        api = PetkitHttpClient.api(PetkitHttpClient.okHttpClient({ base }, { "token" }))
        repository = FeedingRepositoryImpl(api, store)
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun on(method: String, path: String, body: String, code: Int = 200) {
        routes["$method $path"] = code to body
    }

    @Test
    fun `a refresh reads the plan and the one-off feeds of a dual hopper, in the domain's terms`() = runBlocking {
        on("GET", "devices/2004/schedule", DUAL_PLAN)
        on(
            "GET",
            "devices/2004/deferred-feeds",
            """{"deferred_feeds":[{"id":"d_1","fire_at":1794828600,"amount":null,"amount1":2,"amount2":0}]}""",
        )

        assertEquals(Outcome.Success(Unit), repository.refresh(2004))

        val plan = repository.observePlan(2004).first()!!
        assertEquals(true, plan.dualHopper)
        // Device day numbers: 1 is Sunday, 7 Saturday.
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY), plan.groups[0].days)
        assertEquals(
            Meal("n_28800", LocalTime.of(8, 0), Portions.Dual(1, 1)),
            plan.groups[0].meals[0],
        )
        assertEquals(LocalTime.of(13, 0), plan.mealsOn(DayOfWeek.MONDAY)[1].time)
        assertEquals(
            listOf(DeferredFeed("d_1", Instant.ofEpochSecond(1_794_828_600), Portions.Dual(2, 0))),
            repository.observeDeferredFeeds(2004).first(),
        )
    }

    @Test
    fun `a refresh that fails keeps what was read before`() = runBlocking {
        on("GET", "devices/2001/schedule", SINGLE_PLAN)
        on("GET", "devices/2001/deferred-feeds", """{"deferred_feeds":[]}""")
        repository.refresh(2001)
        routes.clear()
        on("GET", "devices/2001/schedule", """{"error":"boom","message":"store down"}""", code = 503)
        on("GET", "devices/2001/deferred-feeds", """{"deferred_feeds":[]}""")

        val result = repository.refresh(2001)

        assertInstanceOf(DomainError.Server::class.java, (result as Outcome.Failure).error)
        assertEquals(Portions.Single(2), repository.observePlan(2001).first()!!.groups[0].meals[2].portions)
    }

    @Test
    fun `saving the plan sends the API's shape and keeps the plan the server answered`() = runBlocking {
        on("GET", "devices/2001/schedule", SINGLE_PLAN)
        on("GET", "devices/2001/deferred-feeds", """{"deferred_feeds":[]}""")
        repository.refresh(2001)
        on("PUT", "devices/2001/schedule", """{"ok":true,"delivered":"queued","schedule":$SAVED_SINGLE}""")

        val groups = listOf(
            FeedingGroup(
                setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
                listOf(
                    Meal(null, LocalTime.of(18, 30), Portions.Single(3)),
                    Meal("n_25200", LocalTime.of(7, 0), Portions.Single(1)),
                ),
            ),
        )
        assertEquals(Outcome.Success(CommandDelivery.Queued), repository.savePlan(2001, groups))

        val sent = json(requests.last { it.first == "PUT devices/2001/schedule" }.second)
        assertEquals(
            json(
                """{"groups":[{"days":[1,2],"meals":[
                    {"id":"n_25200","time":25200,"amount":1},{"id":"","time":66600,"amount":3}]}]}""",
            ),
            sent,
        )
        // The server's answer, not what was asked, is what the cache holds.
        val stored = repository.observePlan(2001).first()!!
        assertEquals(listOf("n_25200", "n_66600"), stored.groups.single().meals.map { it.id })
    }

    @Test
    fun `saving before the plan was read reads it first, to know how the feeder counts`() = runBlocking {
        on("GET", "devices/2004/schedule", DUAL_PLAN)
        on("PUT", "devices/2004/schedule", """{"ok":true,"delivered":"mqtt","schedule":$DUAL_PLAN_BODY}""")

        val result = repository.savePlan(
            2004,
            listOf(
                FeedingGroup(setOf(DayOfWeek.SATURDAY), listOf(Meal(null, LocalTime.of(9, 0), Portions.Dual(2, 1)))),
            ),
        )

        assertEquals(Outcome.Success(CommandDelivery.Mqtt), result)
        assertEquals(
            json("""{"groups":[{"days":[7],"meals":[{"id":"","time":32400,"amount1":2,"amount2":1}]}]}"""),
            json(requests.last().second),
        )
    }

    @Test
    fun `pausing sends active alone, and a refusal changes nothing`() = runBlocking {
        on("GET", "devices/2001/schedule", SINGLE_PLAN)
        on("GET", "devices/2001/deferred-feeds", """{"deferred_feeds":[]}""")
        repository.refresh(2001)
        on(
            "PUT",
            "devices/2001/schedule",
            """{"error":"invalid_schedule","message":"not a valid feeding schedule"}""",
            422,
        )

        val result = repository.setPlanActive(2001, false)

        assertEquals(json("""{"active":false}"""), json(requests.last().second))
        val error = (result as Outcome.Failure).error as DomainError.Validation
        assertEquals("invalid_schedule", error.code)
        assertEquals(true, repository.observePlan(2001).first()!!.active)
    }

    @Test
    fun `a one-off feed is added from the server's answer and removed once it confirms`() = runBlocking {
        on("GET", "devices/2001/schedule", SINGLE_PLAN)
        on("GET", "devices/2001/deferred-feeds", """{"deferred_feeds":[]}""")
        repository.refresh(2001)
        on(
            "POST",
            "devices/2001/deferred-feeds",
            """{"ok":true,"delivered":"queued","feed":""" +
                """{"id":"d_x","fire_at":1794828600.5,"amount":2,"amount1":null,"amount2":null}}""",
            code = 201,
        )
        on("DELETE", "devices/2001/deferred-feeds/d_x", """{"ok":true,"delivered":"queued"}""")

        val added = repository.addDeferredFeed(
            2001,
            DeferredFeedRequest(LocalDate.of(2026, 11, 16), LocalTime.of(12, 30), Portions.Single(2)),
        )

        assertEquals(Outcome.Success(CommandDelivery.Queued), added)
        assertEquals(json("""{"date":"2026-11-16","time":"12:30","amount":2}"""), json(requests.last().second))
        val feed = repository.observeDeferredFeeds(2001).first()!!.single()
        assertEquals(Instant.ofEpochMilli(1_794_828_600_500), feed.fireAt)

        assertEquals(Outcome.Success(CommandDelivery.Queued), repository.cancelDeferredFeed(2001, "d_x"))
        assertEquals(emptyList<DeferredFeed>(), repository.observeDeferredFeeds(2001).first())
    }

    @Test
    fun `a refused feed is not listed, and the session's end forgets every feeder`() = runBlocking {
        on("GET", "devices/2001/schedule", SINGLE_PLAN)
        on("GET", "devices/2001/deferred-feeds", """{"deferred_feeds":[]}""")
        repository.refresh(2001)
        on("POST", "devices/2001/deferred-feeds", """{"error":"invalid_feed","message":"time is in the past"}""", 422)

        val result = repository.addDeferredFeed(
            2001,
            DeferredFeedRequest(LocalDate.of(2020, 1, 1), LocalTime.NOON, Portions.Single(1)),
        )

        assertEquals("time is in the past", ((result as Outcome.Failure).error as DomainError.Validation).message)
        assertEquals(emptyList<DeferredFeed>(), repository.observeDeferredFeeds(2001).first())
        store.clear()
        assertNull(repository.observePlan(2001).first())
        assertNull(repository.observeDeferredFeeds(2001).first())
    }

    @Test
    fun `day numbers map both ways`() {
        DayOfWeek.entries.forEach { assertEquals(it, deviceDayOf(it.toDeviceDay())) }
        assertEquals(1, DayOfWeek.SUNDAY.toDeviceDay())
        assertEquals(7, DayOfWeek.SATURDAY.toDeviceDay())
        assertNull(deviceDayOf(0))
        assertNull(deviceDayOf(8))
    }

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private companion object {
        const val DUAL_PLAN_BODY =
            """{"device_id":2004,"active":true,"dual_hopper":true,"groups":[{"days":[1,2,7],"meals":[""" +
                """{"id":"n_28800","time":28800,"amount":null,"amount1":1,"amount2":1},""" +
                """{"id":"n_46800","time":46800,"amount":null,"amount1":0,"amount2":1}]}]}"""
        const val DUAL_PLAN = """{"schedule":$DUAL_PLAN_BODY}"""
        const val SINGLE_PLAN =
            """{"schedule":{"device_id":2001,"active":true,"dual_hopper":false,"groups":[{"days":[1,2,3,4,5,6,7],""" +
                """"meals":[{"id":"n_25200","time":25200,"amount":1,"amount1":null,"amount2":null},""" +
                """{"id":"n_43200","time":43200,"amount":1,"amount1":null,"amount2":null},""" +
                """{"id":"n_68400","time":68400,"amount":2,"amount1":null,"amount2":null}]}]}}"""
        const val SAVED_SINGLE =
            """{"device_id":2001,"active":true,"dual_hopper":false,"groups":[{"days":[1,2],"meals":[""" +
                """{"id":"n_25200","time":25200,"amount":1,"amount1":null,"amount2":null},""" +
                """{"id":"n_66600","time":66600,"amount":3,"amount1":null,"amount2":null}]}]}"""
    }
}

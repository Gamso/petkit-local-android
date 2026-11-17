package io.github.gamso.petkitlocal.mock

import io.github.gamso.petkitlocal.mock.seed.Seed
import io.github.gamso.petkitlocal.mock.state.Card
import io.github.gamso.petkitlocal.mock.state.Timeline
import io.ktor.http.HttpMethod
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class TimelineTest {
    private suspend fun Api.walk(query: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val sep = if (query.isEmpty()) "" else "&"
            val page = get("/timeline", "/timeline?$query${cursor?.let { "${sep}cursor=$it" } ?: ""}")
            assertEquals(200, page.status, page.text)
            all += page["items"].jsonArray.map { it.jsonObject }
            cursor = (page["next_cursor"] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        } while (cursor != null)
        return all
    }

    private val JsonObject.key get() = this["ts"]!!.jsonPrimitive.double to this["id"]!!.jsonPrimitive.int

    @Test
    fun `paging walks the whole history newest first, each card once`() =
        mockTest { world, api ->
            api.login()
            val all = api.walk("limit=7")
            assertEquals(world.timeline.cards.size, all.size)
            assertEquals(all.size, all.map { it["id"] }.toSet().size)
            val keys = all.map { it.key }
            assertEquals(keys.sortedWith(compareByDescending<Pair<Double, Int>> { it.first }.thenByDescending { it.second }), keys)
            // Seven days of history, the oldest card within the seventh day back.
            val oldest = keys.last().first
            assertTrue(world.clock.now() - oldest < 7 * 86400, "history reaches back more than 7 days")
            assertTrue(world.clock.now() - oldest > 5 * 86400, "history does not reach back 6 days")
        }

    @Test
    fun `the last page has no cursor, and limit is clamped to 1-100`() =
        mockTest { world, api ->
            api.login()
            val big = api.get("/timeline", "/timeline?limit=1000")
            assertEquals(100, big["items"].jsonArray.size)
            val small = api.get("/timeline", "/timeline?limit=0")
            assertEquals(1, small["items"].jsonArray.size)
            val total = world.timeline.cards.size
            assertTrue(total in 101..300, "seed has $total cards")
            val one = api.get("/timeline", "/timeline?device=${Seed.T3}&kind=fault")
            assertEquals(2, one["items"].jsonArray.size)
            assertEquals(JsonNull, one["next_cursor"])
            assertEquals(400, api.get("/timeline", "/timeline?limit=ten").status)
        }

    @Test
    fun `a cursor is stable while new cards arrive on top`() =
        mockTest { _, api ->
            api.login()
            val first = api.get("/timeline", "/timeline?limit=10")
            val cursor = first["next_cursor"].jsonPrimitive.content
            val expected = api.get("/timeline", "/timeline?limit=10&cursor=$cursor")["items"]
            api.client.control(HttpMethod.Post, "/devices/${Seed.T4}/visit", """{"pet_id":1}""")
            api.client.control(HttpMethod.Post, "/devices/${Seed.W7H}/drink", "{}")
            assertEquals(expected, api.get("/timeline", "/timeline?limit=10&cursor=$cursor")["items"])
        }

    @Test
    fun `filters - device, pet, kind and local day`() =
        mockTest { world, api ->
            api.login()
            val visits = api.walk("kind=toileting&limit=100")
            assertTrue(visits.isNotEmpty())
            assertTrue(visits.all { "toileting" in it["filters"]!!.jsonArray.map { f -> f.jsonPrimitive.content } })
            val feeding = api.walk("kind=feeding&limit=100")
            assertTrue(feeding.all { it["event_kind"]!!.jsonPrimitive.content == "feeding" })
            val drinks = api.walk("kind=drinking")
            assertTrue(drinks.isNotEmpty() && drinks.all { it["device_id"]!!.jsonPrimitive.int == Seed.W7H })
            assertTrue(api.walk("kind=cleaning").isNotEmpty())
            assertTrue(api.walk("kind=pet").isNotEmpty())
            assertEquals(1, api.walk("kind=health_alert").size)

            val mochi = api.walk("pet=1&limit=100")
            assertTrue(mochi.all { it["pet"]!!.jsonObject["id"]!!.jsonPrimitive.int == 1 })
            val t5 = api.walk("device=${Seed.T5}")
            assertTrue(t5.all { it["device_id"]!!.jsonPrimitive.int == Seed.T5 && it["device_type"]!!.jsonPrimitive.content == "t5" })

            val yesterday = world.clock.today().minusDays(1)
            val (start, end) = world.clock.dayBounds(yesterday)
            val day = api.walk("day=$yesterday")
            assertTrue(day.isNotEmpty())
            assertTrue(day.all { it["ts"]!!.jsonPrimitive.double in start..<end })
            assertEquals(
                world.timeline.cards.values
                    .count { it.ts >= start && it.ts < end },
                day.size,
            )

            assertEquals(400, api.get("/timeline", "/timeline?kind=walks").status)
            assertEquals(400, api.get("/timeline", "/timeline?day=yesterday").status)
            assertEquals(400, api.get("/timeline", "/timeline?device=x").status)
            assertEquals("bad_cursor", api.get("/timeline", "/timeline?cursor=${Timeline.encodeCursor(1.0, 1).drop(2)}").error)
        }

    @Test
    fun `a visit card carries its steps, its pet and its media`() =
        mockTest { _, api ->
            api.login()
            val visit = api.walk("device=${Seed.T5}&kind=toileting").first()
            assertEquals("visit", visit["kind"]!!.jsonPrimitive.content)
            assertEquals(JsonNull, visit["label"])
            assertTrue(visit["weight_g"]!!.jsonPrimitive.int > 3000)
            assertTrue(visit["display_ts"]!!.jsonPrimitive.double < visit["ts"]!!.jsonPrimitive.double)
            val steps = visit["steps"]!!.jsonArray
            assertEquals(
                "Weight check",
                steps
                    .first()
                    .jsonObject["label"]!!
                    .jsonPrimitive.content,
            )
            val media = visit["media"]!!.jsonObject
            assertTrue(media["snapshot"]!!.jsonPrimitive.content.startsWith("/app/v1/media/"))
            assertTrue(media["thumbnail"]!!.jsonPrimitive.content.startsWith("/app/v1/media/thumb/"))
            // The T4 has no camera: no media at all.
            assertEquals(JsonNull, api.walk("device=${Seed.T4}&kind=toileting").first()["media"])

            val detail = api.get("/timeline/{event_id}", "/timeline/${visit["id"]}")["event"].jsonObject
            assertEquals("Toilet visit", detail["label"]!!.jsonPrimitive.content)
            assertTrue(detail["fields"]!!.jsonArray.any { it.jsonObject["key"]!!.jsonPrimitive.content == "petWeight" })
            val step = api.get("/timeline/{event_id}", "/timeline/${steps.first().jsonObject["id"]}")["event"].jsonObject
            assertEquals("9", step["event_type"]!!.jsonPrimitive.content)
            assertEquals(JsonNull, step["media"])
        }

    @Test
    fun `a box that deodorizes lists the spray after the cleaning, and some sprays fail`() =
        mockTest { _, api ->
            api.login()
            val labels =
                api.walk("device=${Seed.T5}&kind=toileting&limit=100").map { visit ->
                    visit["steps"]!!.jsonArray.map { it.jsonObject["label"]!!.jsonPrimitive.content }
                }
            val cleaned = labels.filter { "Auto cleaning completed" in it }
            assertTrue(cleaned.isNotEmpty())
            // Each spray follows its cleaning, as a step the official app shows.
            assertTrue(cleaned.all { it.last().startsWith("Auto deodorizing ") && it.indexOf("Auto cleaning completed") < it.size - 1 })
            val sprays = cleaned.map { it.last() }
            assertTrue("Auto deodorizing completed" in sprays)
            assertTrue("Auto deodorizing failed" in sprays)
            // The Pura Max has no deodorizer of its own: its visits end with the cleaning.
            val t4 = api.walk("device=${Seed.T4}&kind=toileting&limit=100").flatMap { it["steps"]!!.jsonArray }
            assertTrue(
                t4.none {
                    it.jsonObject["label"]!!
                        .jsonPrimitive.content
                        .contains("deodorizing")
                },
            )
        }

    @Test
    fun `media serve ranges, thumbnails are jpegs`() =
        mockTest { _, api ->
            api.login()
            val media = api.walk("device=${Seed.T6}&kind=toileting").first()["media"]!!.jsonObject
            val clip = media["playback"]!!.jsonPrimitive.content.removePrefix("/app/v1")
            val full = api.get("/media/{path}", clip)
            assertEquals("video/mp4", full.headers["Content-Type"])
            assertEquals("bytes", full.headers["Accept-Ranges"])
            val tail = api.call(HttpMethod.Get, "/media/{path}", clip, headers = mapOf("Range" to "bytes=-10"))
            assertEquals(206, tail.status)
            assertEquals("bytes ${full.bytes.size - 10}-${full.bytes.size - 1}/${full.bytes.size}", tail.headers["Content-Range"])
            assertTrue(tail.bytes.contentEquals(full.bytes.copyOfRange(full.bytes.size - 10, full.bytes.size)))
            val open = api.call(HttpMethod.Get, "/media/{path}", clip, headers = mapOf("Range" to "bytes=100-"))
            assertEquals(full.bytes.size - 100, open.bytes.size)
            val thumb = api.get("/media/thumb/{path}", media["thumbnail"]!!.jsonPrimitive.content.removePrefix("/app/v1"))
            assertEquals("image/jpeg", thumb.headers["Content-Type"])
            assertEquals(0xFF.toByte(), thumb.bytes[0])
            assertEquals(0xD8.toByte(), thumb.bytes[1])
            assertEquals(1, media["waste"]!!.jsonArray.size)
            assertNull(media["health"]!!.jsonArray.firstOrNull())
        }

    @Test
    fun `today the feeders served a meal, failed one, and saw a cat, and a meal says what each hopper gave`() =
        mockTest { world, api ->
            api.login()
            val today = world.clock.today()
            val (start, _) = world.clock.dayBounds(today)
            assumeTrue(world.clock.now() - start > 15 * 60, "the day is too young for its feeders' events")

            fun labels(cards: List<JsonObject>) = cards.map { it["label"]!!.jsonPrimitive.content }
            val dual = api.walk("device=${Seed.D4SH}&day=$today&limit=100")
            assertTrue("Feeding completed" in labels(dual))
            assertTrue("Feeding failed (nothing dispensed)" in labels(dual))
            val seen = dual.first { it["label"]!!.jsonPrimitive.content == "Pet detected" }
            assertEquals(listOf("pet"), seen["filters"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertTrue(seen["media"]!!.jsonObject["highlight"]!!.jsonPrimitive.isString)
            assertTrue("Pet detected" in labels(api.walk("device=${Seed.D4H}&day=$today&limit=100")))
            val single = labels(api.walk("device=${Seed.D4}&day=$today&limit=100"))
            assertTrue("Feeding completed" in single && "Feeding failed (nothing dispensed)" in single)

            // The meal's own event carries what each hopper put out, as petkit-local passes it through.
            val meal = dual.first { it["label"]!!.jsonPrimitive.content == "Feeding completed" }
            assertEquals(setOf("real_amount1", "real_amount2"), served(api, meal).keys)
            val solo =
                api.walk("device=${Seed.D4}&day=$today&limit=100").first {
                    it["label"]!!.jsonPrimitive.content == "Feeding completed"
                }
            assertEquals(setOf("real_amount"), served(api, solo).keys)
        }

    @Test
    fun `a meal served from the control API gives each hopper's count`() =
        mockTest { world, api ->
            api.login()
            val before =
                synchronized(world.lock) {
                    world.timeline.cards.keys
                        .toSet()
                }

            api.client.control(HttpMethod.Post, "/devices/${Seed.D4SH}/meal", """{"amount1":2,"amount2":1}""")

            var card: Card? = null
            repeat(100) {
                card =
                    synchronized(world.lock) {
                        world.timeline.cards.values
                            .firstOrNull { it.id !in before && it.deviceId == Seed.D4SH }
                    }
                if (card == null) delay(20)
            }
            val fields =
                synchronized(world.lock) {
                    world.timeline.rows
                        .getValue(card!!.id)
                        .fields
                }.associate { it.key to it.text }
            assertEquals("2", fields["real_amount1"])
            assertEquals("1", fields["real_amount2"])
        }

    /** The `real_amount*` fields of [card]'s event. */
    private suspend fun served(
        api: Api,
        card: JsonObject,
    ): Map<String, String> =
        api
            .get("/timeline/{event_id}", "/timeline/${card["id"]!!.jsonPrimitive.int}")["event"]
            .jsonObject["fields"]!!
            .jsonArray
            .map { it.jsonObject }
            .filter { it["key"]!!.jsonPrimitive.content.startsWith("real_amount") }
            .associate { it["key"]!!.jsonPrimitive.content to it["text"]!!.jsonPrimitive.content }
}

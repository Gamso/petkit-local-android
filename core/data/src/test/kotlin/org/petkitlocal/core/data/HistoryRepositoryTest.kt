package org.petkitlocal.core.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryPage
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.model.EventField
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineMedia
import org.robolectric.RobolectricTestRunner

/**
 * Integration: History's cursor paging and event detail against a scripted server and a real Room
 * database. Pages are written into the same cache Home reads, and media paths come out as
 * absolute URLs on the server, below its path prefix, so the bearer header goes with them.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {
    @get:Rule
    val server = ScriptedServer()

    private val zone = ZoneId.of("Europe/Paris")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 15, 10, 0, 0, 0, zone).toInstant(), zone)
    private val timeline by lazy { TimelineRepositoryImpl(server.api, server.db.timelineDao(), clock) { server.base } }
    private val day = LocalDate.of(2026, 11, 15)

    @Test
    fun `pages follow the cursor with the query's filters, and each is written into the cache`() = runTest {
        val query = HistoryQuery(day, TimelineCategory.Toileting, deviceId = 1002, petId = 1)
        val base = "timeline?device=1002&day=2026-11-15&kind=toileting&pet=1&limit=25"
        server.on(base, """{"items":[${item(3)},${item(2)}],"next_cursor":"c1"}""")
        server.on("$base&cursor=c1", """{"items":[${item(1)}],"next_cursor":null}""")

        val first = timeline.fetchHistoryPage(query, cursor = null)

        val cursor = HistoryCursor("c1", lastTs = TS + 2.0, lastId = 2)
        assertEquals(Outcome.Success(HistoryPage(next = cursor)), first)
        assertEquals(listOf(3L, 2L), timeline.observeHistory(query).first().map { it.id })

        assertEquals(Outcome.Success(HistoryPage(next = null)), timeline.fetchHistoryPage(query, cursor))
        assertEquals(listOf(3L, 2L, 1L), timeline.observeHistory(query).first().map { it.id })
        // The same rows Home reads for the day.
        assertEquals(listOf(3L, 2L, 1L), timeline.observeDay(day).first().map { it.id })
    }

    @Test
    fun `a card's media are absolute URLs on the server, photos first, each with its thumbnail`() = runTest {
        server.on("timeline?day=2026-11-15&limit=25", """{"items":[${item(1, media = MEDIA)}],"next_cursor":null}""")

        timeline.fetchHistoryPage(HistoryQuery(day), cursor = null)

        val api = server.base.toString() // .../petkit/app/v1/
        val media = timeline.observeHistory(HistoryQuery(day)).first().single().media
        assertEquals(
            TimelineMedia(
                files = listOf(
                    MediaFile(MediaKind.Snapshot, "${api}media/a/snap.jpg", "${api}media/thumb/a/snap.jpg"),
                    MediaFile(MediaKind.Waste, "${api}media/a/waste.jpg", "${api}media/thumb/a/waste.jpg"),
                    MediaFile(MediaKind.Highlight, "${api}media/a/clip.mp4", "${api}media/thumb/a/clip.mp4"),
                ),
                videoPending = true,
            ),
            media,
        )
    }

    @Test
    fun `a page that fails leaves the cache as it was`() = runTest {
        server.on("timeline?day=2026-11-15&limit=25", """{"items":[${item(1)}],"next_cursor":null}""")
        timeline.fetchHistoryPage(HistoryQuery(day), cursor = null)
        server.on("timeline?day=2026-11-15&limit=25", """{"error":"unavailable","message":"x"}""", code = 503)

        val result = timeline.fetchHistoryPage(HistoryQuery(day), cursor = null)

        assertTrue((result as Outcome.Failure).error is DomainError.Server)
        assertEquals(listOf(1L), timeline.observeHistory(HistoryQuery(day)).first().map { it.id })
    }

    @Test
    fun `an event's decoded fields and media come from its own route`() = runTest {
        server.on(
            "timeline/42",
            """{"event":{"id":42,"device_id":1002,"device_type":"t5","ts":1794719959,"event_type":"10",
               "event_kind":"toilet_visit","label":"Toilet visit","pet":{"id":1,"name":null},"facts":[],
               "fields":[{"key":"petWeight","label":"Pet weight","text":"4.21 kg"}],"media":$MEDIA}}""",
        )

        val event = (timeline.getEvent(42) as Outcome.Success).value

        assertEquals(Instant.ofEpochSecond(1_794_719_959), event.ts)
        assertEquals(PetRef(1, null), event.pet)
        assertEquals(listOf(EventField("petWeight", "Pet weight", "4.21 kg")), event.fields)
        assertEquals(3, event.media?.files?.size)
    }

    @Test
    fun `an unknown event is not found, and media outside the API are left out`() = runTest {
        server.on("timeline/7", """{"error":"event_not_found","message":"no such event"}""", code = 404)
        assertTrue((timeline.getEvent(7) as Outcome.Failure).error is DomainError.NotFound)

        val outside = """{"thumbnail":null,"snapshot":"https://elsewhere.example/a.jpg","highlight":null,
            "playback":null,"timelapse":null,"waste":[],"health":[],"video_pending":false}"""
        server.on("timeline?day=2026-11-15&limit=25", """{"items":[${item(1, media = outside)}],"next_cursor":null}""")
        timeline.fetchHistoryPage(HistoryQuery(day), cursor = null)
        assertNull(timeline.observeHistory(HistoryQuery(day)).first().single().media)
    }

    @Test
    fun `a camera's recent clips are its newest cards with a clip, each filed under its own day`() = runTest {
        // 1794719959 is 2026-11-15 06:19 in Paris; a day earlier is 2026-11-14.
        val yesterday = TS - DAY
        server.on(
            "timeline?device=1003&limit=30",
            """{"items":[${item(5, media = MEDIA, device = 1003)},${item(4, device = 1003)},
               ${item(3, media = MEDIA, device = 1003, ts = yesterday)}],"next_cursor":"c1"}""",
        )
        server.on(
            "timeline?device=1003&limit=30&cursor=c1",
            """{"items":[${item(2, media = PHOTO_ONLY, device = 1003, ts = yesterday - 60)},
               ${item(1, media = MEDIA, device = 1003, ts = yesterday - 120)}],"next_cursor":null}""",
        )

        assertEquals(Outcome.Success(Unit), timeline.refreshRecentClips(1003, count = 6))

        assertEquals(listOf(5L, 3L, 1L), timeline.observeRecentClips(1003, limit = 6).first().map { it.id })
        assertEquals(listOf(5L, 3L), timeline.observeRecentClips(1003, limit = 2).first().map { it.id })
        // Filed under their own local day, where Home and History read them.
        assertEquals(listOf(5L, 4L), timeline.observeDay(day).first().map { it.id })
        assertEquals(listOf(3L, 2L, 1L), timeline.observeDay(day.minusDays(1)).first().map { it.id })
        // Every card with a picture, the photo-only one too, for the camera's latest still.
        val withMedia = timeline.observeNewestWithMedia(1003, limit = 5).first()
        assertEquals(listOf(5L, 3L, 2L, 1L), withMedia.map { it.id })
        assertEquals("${server.base}media/thumb/a/snap.jpg", withMedia.first().media?.files?.first()?.thumbnailUrl)
    }

    @Test
    fun `a refresh stops reading pages once it has enough clips`() = runTest {
        server.on(
            "timeline?device=1003&limit=30",
            """{"items":[${item(5, media = MEDIA, device = 1003)},${item(4, media = MEDIA, device = 1003)}],
               "next_cursor":"c1"}""",
        )

        assertEquals(Outcome.Success(Unit), timeline.refreshRecentClips(1003, count = 2))

        assertEquals(listOf("timeline?device=1003&limit=30"), server.requests.filter { it.startsWith("timeline") })
        assertEquals(listOf(5L, 4L), timeline.observeRecentClips(1003, limit = 6).first().map { it.id })
    }

    @Test
    fun `a failed refresh of recent clips leaves the cache as it was`() = runTest {
        val page = """{"items":[${item(1, media = MEDIA, device = 1003)}],"next_cursor":null}"""
        server.on("timeline?device=1003&limit=30", page)
        timeline.refreshRecentClips(1003, count = 6)
        server.on("timeline?device=1003&limit=30", """{"error":"unavailable","message":"x"}""", code = 503)

        val result = timeline.refreshRecentClips(1003, count = 6)

        assertTrue((result as Outcome.Failure).error is DomainError.Server)
        assertEquals(listOf(1L), timeline.observeRecentClips(1003, limit = 6).first().map { it.id })
    }

    private fun item(id: Long, media: String = "null", device: Long = 1002, ts: Long = TS + id) = """{"id":$id,
        "kind":"visit","filters":["toileting"],
        "device_id":$device,"device_type":"t5","ts":$ts,"display_ts":1794719773,
        "event_type":"10","event_kind":"toilet_visit","label":null,"pet":{"id":1,"name":"Mochi"},
        "duration_sec":186,"weight_g":6065,"facts":[],"media":$media,"steps":[]}"""

    private companion object {
        const val TS = 1_794_719_959L

        const val MEDIA = """{"thumbnail":"/app/v1/media/thumb/a/snap.jpg","snapshot":"/app/v1/media/a/snap.jpg",
            "highlight":"/app/v1/media/a/clip.mp4","playback":null,"timelapse":null,
            "waste":["/app/v1/media/a/waste.jpg"],"health":[],"video_pending":true}"""

        const val PHOTO_ONLY = """{"thumbnail":null,"snapshot":"/app/v1/media/b/snap.jpg","highlight":null,
            "playback":null,"timelapse":null,"waste":[],"health":[],"video_pending":false}"""

        const val DAY = 86_400L
    }
}

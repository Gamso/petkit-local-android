package org.petkitlocal.core.domain.video

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DayOnlyTimeline
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.TimelineMedia

class RecentClipsTest {
    @Test
    fun `an episode is shown by its short clip, else its full recording, else its timelapse`() {
        val snapshot = file(MediaKind.Snapshot)
        val highlight = file(MediaKind.Highlight)
        val playback = file(MediaKind.Playback)
        val timelapse = file(MediaKind.Timelapse)

        assertEquals(highlight, TimelineMedia(listOf(snapshot, highlight, playback, timelapse)).featuredClip)
        assertEquals(playback, TimelineMedia(listOf(snapshot, playback, timelapse)).featuredClip)
        assertEquals(timelapse, TimelineMedia(listOf(timelapse)).featuredClip)
        assertNull(TimelineMedia(listOf(snapshot), videoPending = true).featuredClip)
    }

    @Test
    fun `each cached card with a clip becomes one recent clip, in the cache's order`() = runTest {
        val timeline = ClipTimeline()
        val visit = item(3, file(MediaKind.Playback), file(MediaKind.Highlight))
        val seen = item(2, file(MediaKind.Timelapse))
        // A card the cache files as having a clip, whose clip lies outside the API: nothing to play.
        val unplayable = item(1)
        timeline.cards.value = listOf(visit, seen, unplayable)

        val clips = ObserveRecentClipsUseCase(timeline)(deviceId = 1003).first()

        assertEquals(
            listOf(RecentClip(visit, file(MediaKind.Highlight)), RecentClip(seen, file(MediaKind.Timelapse))),
            clips,
        )
        assertEquals(listOf(1003L to RECENT_CLIPS), timeline.observed)
    }

    @Test
    fun `a refresh asks for as many clips as are shown`() = runTest {
        val timeline = ClipTimeline()

        assertEquals(Outcome.Success(Unit), RefreshRecentClipsUseCase(timeline)(deviceId = 3001))

        assertEquals(listOf(3001L to RECENT_CLIPS), timeline.refreshed)
    }

    private fun file(kind: MediaKind) = MediaFile(kind, "https://pets.example/app/v1/media/${kind.name}", null)

    private fun item(id: Long, vararg files: MediaFile) = TimelineItem(
        id = id,
        kind = "visit",
        categories = emptySet(),
        deviceId = 1003,
        deviceType = "t5",
        ts = Instant.ofEpochSecond(1_794_719_959 + id),
        displayTs = null,
        eventType = null,
        eventKind = null,
        label = null,
        pet = null,
        durationSec = null,
        weightG = null,
        facts = emptyList(),
        media = if (files.isEmpty()) null else TimelineMedia(files.sortedBy { it.kind.ordinal }),
    )

    private class ClipTimeline : DayOnlyTimeline() {
        val cards = MutableStateFlow<List<TimelineItem>>(emptyList())
        val observed = mutableListOf<Pair<Long, Int>>()
        val refreshed = mutableListOf<Pair<Long, Int>>()

        override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> = error("not used")

        override suspend fun refreshDay(day: LocalDate): Outcome<Unit> = error("not used")

        override fun observeRecentClips(deviceId: Long, limit: Int): Flow<List<TimelineItem>> {
            observed += deviceId to limit
            return cards.map { it.take(limit) }
        }

        override suspend fun refreshRecentClips(deviceId: Long, count: Int): Outcome<Unit> {
            refreshed += deviceId to count
            return Outcome.Success(Unit)
        }
    }
}

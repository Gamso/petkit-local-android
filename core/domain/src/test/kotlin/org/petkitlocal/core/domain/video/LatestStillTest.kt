package org.petkitlocal.core.domain.video

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
class LatestStillTest {
    @Test
    fun `an episode is pictured by its poster, else its first file with a thumbnail`() {
        val snapshot = file(MediaKind.Snapshot)
        val highlight = file(MediaKind.Highlight)
        val bare = MediaFile(MediaKind.Waste, "https://pets.example/a.jpg", thumbnailUrl = null)

        assertEquals(snapshot, TimelineMedia(listOf(snapshot, highlight)).still)
        assertEquals(highlight, TimelineMedia(listOf(bare, highlight)).still)
        assertNull(TimelineMedia(listOf(bare)).still)
        assertNull(TimelineMedia(emptyList(), videoPending = true).still)
    }

    @Test
    fun `the still is the newest cached picture's, with when its episode happened`() = runTest {
        val timeline = MediaTimeline()
        // The newest card's recording is still being assembled: nothing to show of it yet.
        val pending = item(3, media = TimelineMedia(emptyList(), videoPending = true))
        val visit = item(2, file(MediaKind.Snapshot), file(MediaKind.Highlight))
        timeline.cards.value = listOf(pending, visit, item(1, file(MediaKind.Snapshot)))

        val still = ObserveLatestStillUseCase(timeline)(deviceId = 1003).first()

        assertEquals(LatestStill(file(MediaKind.Snapshot).thumbnailUrl!!, visit.shownAt), still)
        assertEquals(1003L, timeline.observed.single().first)
    }

    @Test
    fun `a camera that has produced no picture has no still`() = runTest {
        val timeline = MediaTimeline()
        timeline.cards.value = listOf(item(1, media = TimelineMedia(emptyList(), videoPending = true)))

        assertNull(ObserveLatestStillUseCase(timeline)(deviceId = 1003).first())
    }

    @Test
    fun `a refresh that caches a newer picture moves the still to it, once`() = runTest {
        val timeline = MediaTimeline()
        val older = item(1, file(MediaKind.Snapshot))
        timeline.cards.value = listOf(older)
        val seen = mutableListOf<LatestStill?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            ObserveLatestStillUseCase(timeline)(deviceId = 1003).take(2).toList(seen)
        }

        // A card without a picture changes nothing; a newer one with one does.
        timeline.cards.value = listOf(item(2), older)
        val newer = item(3, file(MediaKind.Health))
        timeline.cards.value = listOf(newer, item(2), older)

        assertEquals(
            listOf(
                LatestStill(file(MediaKind.Snapshot).thumbnailUrl!!, older.shownAt),
                LatestStill(file(MediaKind.Health).thumbnailUrl!!, newer.shownAt),
            ),
            seen,
        )
    }

    private fun file(kind: MediaKind) = MediaFile(
        kind,
        "https://pets.example/app/v1/media/${kind.name}",
        "https://pets.example/app/v1/media/thumb/${kind.name}",
    )

    private fun item(id: Long, vararg files: MediaFile) =
        item(id, media = if (files.isEmpty()) null else TimelineMedia(files.toList()))

    private fun item(id: Long, media: TimelineMedia?) = TimelineItem(
        id = id,
        kind = "visit",
        categories = emptySet(),
        deviceId = 1003,
        deviceType = "t5",
        ts = Instant.ofEpochSecond(1_794_719_959 + id),
        displayTs = Instant.ofEpochSecond(1_794_719_900 + id),
        eventType = null,
        eventKind = null,
        label = null,
        pet = null,
        durationSec = null,
        weightG = null,
        facts = emptyList(),
        media = media,
    )

    /** The cache's cards with media of device 1003; the stub keeps those with none, as a test sets them. */
    private class MediaTimeline : DayOnlyTimeline() {
        val cards = MutableStateFlow<List<TimelineItem>>(emptyList())
        val observed = mutableListOf<Pair<Long, Int>>()

        override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> = error("not used")

        override suspend fun refreshDay(day: LocalDate): Outcome<Unit> = error("not used")

        override fun observeNewestWithMedia(deviceId: Long, limit: Int): Flow<List<TimelineItem>> {
            observed += deviceId to limit
            return cards.map { it.take(limit) }
        }
    }
}

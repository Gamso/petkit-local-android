package org.petkitlocal.core.domain.feeder

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.activity.ActivityRules
import org.petkitlocal.core.domain.devicepage.PageFixtures.at
import org.petkitlocal.core.domain.devicepage.PageFixtures.device
import org.petkitlocal.core.domain.devicepage.PageFixtures.item
import org.petkitlocal.core.domain.devicepage.PageFixtures.paris
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.TimelineMedia

class FeederDayRulesTest {
    private val highlight = MediaFile(MediaKind.Highlight, "clip.mp4", "thumb.jpg")
    private val timelapse = MediaFile(MediaKind.Timelapse, "fast.mp4", null)
    private val snapshot = MediaFile(MediaKind.Snapshot, "snap.jpg", "snap.jpg")

    private val served = feed(5, at(17, 0), "Feeding completed").copy(
        media = TimelineMedia(listOf(snapshot, timelapse, highlight)),
    )
    private val seen = item(4, 2004, at(14, 22), TimelineCategory.Pet, pet = 1).copy(
        label = "Pet detected",
        media = TimelineMedia(listOf(snapshot)),
    )
    private val eaten = feed(3, at(12, 10), "Eating done")
    private val failed = feed(2, at(12, 0), "Feeding failed (nothing dispensed)")
    private val fault = item(1, 2004, at(8, 0), TimelineCategory.Fault).copy(label = "Error - door")

    private val cards = listOf(served, seen, eaten, failed, fault)

    @Test
    fun `meals and what the camera saw are counted apart, a fault under All only`() {
        val day = FeederDayRules.day(ActivityRules.entries(cards, emptyList()), camera = true)

        assertEquals(listOf(FeederDayFilter.All, FeederDayFilter.Meals, FeederDayFilter.Camera), day.filters)
        assertEquals(listOf(5, 2, 2), day.filters.map(day::count))
        assertEquals(listOf(5L, 2L), day.rows(FeederDayFilter.Meals).map { it.id })
        // An eating episode shares the feeding chip on the server, but it is the camera's.
        assertEquals(listOf(4L, 3L), day.rows(FeederDayFilter.Camera).map { it.id })
    }

    @Test
    fun `a feeder without a camera has no chip for it`() {
        val day = FeederDayRules.day(
            ActivityRules.entries(listOf(served, failed), emptyList()),
            camera = false,
        )

        assertEquals(listOf(FeederDayFilter.All, FeederDayFilter.Meals), day.filters)
    }

    @Test
    fun `a meal carries what its card says it put out, a row its short clip, and nothing when it has none`() {
        // A card that is not a meal says nothing served, whatever it carries.
        val said = cards.map {
            when (it.id) {
                5L -> it.copy(served = Portions.Dual(1, 1))
                4L -> it.copy(served = Portions.Single(9))
                else -> it
            }
        }

        val rows = FeederDayRules.day(ActivityRules.entries(said, emptyList()), camera = true).rows

        assertEquals(listOf(Portions.Dual(1, 1), null, null, null, null), rows.map { it.portions })
        assertEquals(highlight, rows[0].clip)
        assertNull(rows[1].clip) // a snapshot is not a clip
        assertNull(rows[2].clip)
    }

    @Test
    fun `the page's served meals leave out a failed one and an eating episode`() {
        val d4sh = device(2004, DeviceFamily.Feeder)

        val page = FeederRules.page(d4sh, null, null, cards, at(18, 0), paris)

        assertEquals(1, page.mealsToday)
        assertEquals(5, page.day.rows.size)
    }

    private fun feed(id: Long, at: Instant, label: String): TimelineItem =
        item(id, 2004, at, TimelineCategory.Feeding).copy(kind = "feeding", eventKind = "feeding", label = label)
}

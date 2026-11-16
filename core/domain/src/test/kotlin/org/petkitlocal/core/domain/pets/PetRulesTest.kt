package org.petkitlocal.core.domain.pets

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.PetStats
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.WeightSample

class PetRulesTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `the median is the middle value, or the mean of the two middle ones`() {
        assertEquals(4300.0, PetRules.median(listOf(4400, 4300, 4200)))
        assertEquals(4250.0, PetRules.median(listOf(4300, 4200)))
        assertEquals(4100.0, PetRules.median(listOf(4100)))
    }

    @Test
    fun `thirty days are one point a day, the day's median, with no point on a day without a weighed visit`() {
        val samples = listOf(
            sample(1, today, 9, 4300),
            sample(2, today, 18, 4400),
            sample(3, today, 20, 4350),
            sample(4, today.minusDays(2), 8, 4200),
            // Before the range: left out.
            sample(5, today.minusDays(30), 8, 9999),
        )

        val trend = PetRules.weightTrend(samples, WeightRange.Month, today, paris)

        assertEquals(LocalDate.of(2026, 10, 17), trend.firstDay)
        assertEquals(today, trend.lastDay)
        assertEquals(
            listOf(
                WeightPoint(27, today.minusDays(2), today.minusDays(2), 4200.0, visits = 1),
                WeightPoint(29, today, today, 4350.0, visits = 3),
            ),
            trend.points,
        )
        assertEquals(4, trend.visits)
        assertEquals(today.minusDays(2), trend.dataSince)
    }

    @Test
    fun `six months are 26 weeks ending today, one point a week`() {
        val samples = listOf(
            sample(1, today, 9, 4300),
            sample(2, today.minusDays(6), 9, 4500),
            sample(3, today.minusDays(7), 9, 4100),
            sample(4, today.minusDays(181), 9, 4000),
        )

        val trend = PetRules.weightTrend(samples, WeightRange.HalfYear, today, paris)

        assertEquals(26, WeightRange.HalfYear.buckets)
        assertEquals(today.minusDays(181), trend.firstDay)
        assertEquals(listOf(0, 24, 25), trend.points.map { it.index })
        assertEquals(WeightPoint(25, today.minusDays(6), today, 4400.0, visits = 2), trend.points.last())
        assertEquals(today.minusDays(13), trend.points[1].firstDay)
        // The range's first bucket has data: nothing to say about where it starts.
        assertNull(trend.dataSince)
    }

    @Test
    fun `a visit belongs to its local day, whatever the clocks did`() {
        // 00:30 in Paris on the 15th is still the 14th in UTC.
        val samples = listOf(sample(1, today, 0, 4300, minute = 30))

        val trend = PetRules.weightTrend(samples, WeightRange.Month, today, paris)

        assertEquals(today, trend.points.single().firstDay)
        assertEquals(
            ZonedDateTime.of(LocalDate.of(2026, 10, 17).atStartOfDay(), paris).toInstant(),
            PetRules.since(WeightRange.Month, today, paris),
        )
    }

    @Test
    fun `today's visits count only when read today`() {
        val readThisMorning = activity(fetchedAt = at(today, 8))
        val readYesterday = activity(fetchedAt = at(today.minusDays(1), 23))

        assertEquals(3, PetRules.visitsToday(readThisMorning, today, paris))
        assertNull(PetRules.visitsToday(readYesterday, today, paris))
        assertNull(PetRules.visitsToday(null, today, paris))
    }

    @Test
    fun `a page takes the pet's colour from its place, and is null for a pet the cache does not have`() {
        val pets = listOf(pet(1, "Mochi"), pet(2, "Tigrou"))
        val visit = visit(12, petId = 2)
        val activity = activity(at(today, 8), recent = listOf(visit))

        val page = PetRules.page(2, pets, activity, emptyList(), emptyList(), WeightRange.Month, today, paris)!!

        assertEquals("Tigrou", page.pet.name)
        assertEquals(1, page.colorIndex)
        assertEquals(3, page.visitsToday)
        assertEquals(listOf(PetVisit(visit, device = null)), page.recentVisits)
        assertNull(PetRules.page(3, pets, null, emptyList(), emptyList(), WeightRange.Month, today, paris))
    }

    @Test
    fun `a pet's visits of the day are its toileting cards`() {
        val items = listOf(visit(1, petId = 1), visit(2, petId = 2), visit(3, petId = 1, toileting = false))

        assertEquals(setOf(1L), PetRules.visitIds(1, items))
        val overview = PetRules.overview(listOf(pet(1, "Mochi"), pet(2, "Tigrou")), items)
        assertEquals(listOf(1, 1), overview.pets.map { it.visitsToday })
    }

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Instant =
        ZonedDateTime.of(day.atTime(hour, minute), paris).toInstant()

    private fun sample(id: Long, day: LocalDate, hour: Int, weight: Int, minute: Int = 0) =
        WeightSample(id, at(day, hour, minute), weight)

    private fun pet(id: Long, name: String) = Pet(id, name, emptyList(), 4200.0, 4300.0, false, null)

    private fun activity(fetchedAt: Instant, recent: List<TimelineItem> = emptyList()) = PetActivity(
        petId = 2,
        stats = PetStats(4300.0, 3, fetchedAt, 4300.0, 90, null),
        recentVisits = recent,
        fetchedAt = fetchedAt,
    )

    private fun visit(id: Long, petId: Long, toileting: Boolean = true) = TimelineItem(
        id = id,
        kind = if (toileting) "visit" else "event",
        categories = if (toileting) setOf(TimelineCategory.Toileting) else setOf(TimelineCategory.Pet),
        deviceId = 1002,
        deviceType = "t4",
        ts = at(today, 9),
        displayTs = null,
        eventType = null,
        eventKind = null,
        label = null,
        pet = PetRef(petId, "Mochi"),
        durationSec = 90,
        weightG = 4300,
        facts = emptyList(),
    )
}

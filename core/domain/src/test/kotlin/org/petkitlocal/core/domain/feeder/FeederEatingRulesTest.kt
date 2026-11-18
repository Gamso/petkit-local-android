package org.petkitlocal.core.domain.feeder

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.devicepage.PageFixtures.at
import org.petkitlocal.core.domain.devicepage.PageFixtures.device
import org.petkitlocal.core.domain.devicepage.PageFixtures.item
import org.petkitlocal.core.domain.devicepage.PageFixtures.paris
import org.petkitlocal.core.domain.devicepage.PageFixtures.pet
import org.petkitlocal.core.domain.devicepage.PageFixtures.today
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem

class FeederEatingRulesTest {
    private val pets = listOf(pet(1), pet(2), pet(3))
    private val yesterday = today.minusDays(1)

    private val cards = listOf(
        eaten(6, at(19, 30), pet = 2, duration = 50),
        // Seen by the camera, not eating: not a meal eaten.
        item(5, D4SH, at(18, 0), TimelineCategory.Pet, pet = 1).copy(label = "Pet detected"),
        // Served by the feeder: not one either, whatever it carries.
        feed(4, at(17, 0), "Feeding completed").copy(served = Portions.Dual(1, 1), durationSec = 12),
        eaten(3, at(12, 10), pet = null, duration = 130),
        // Its end never arrived: a meal eaten all the same, with no duration.
        feed(2, at(7, 1), "Eating started").copy(pet = PetRef(1, "Pet 1")),
        // Another feeder's.
        eaten(1, at(8, 0), pet = 1, duration = 70).copy(deviceId = 2003),
    )

    @Test
    fun `every eating episode counts, its pet known or not, and the average is of those timed`() {
        val eating = FeederEatingRules.eating(cards.filter { it.deviceId == D4SH }, emptyList(), paris, pets)

        assertEquals(EatingStats(meals = 3, averageDurationSec = 90), eating.today)
        assertEquals(EatingStats(meals = 0, averageDurationSec = null), eating.yesterday)
        assertTrue(eating.durationsKnown)
        // Oldest first, by the minute of the day.
        assertEquals(
            listOf(
                EatingEpisode(2, 7 * 60 + 1, null, 1),
                EatingEpisode(3, 12 * 60 + 10, 130, null),
                EatingEpisode(6, 19 * 60 + 30, 50, 2),
            ),
            eating.episodes,
        )
        // The pets seen eating, in the household's order; the cat it could not tell has no chip.
        assertEquals(listOf(1L, 2L), eating.chartPets.map { it.id })
        assertEquals(1, eating.colorIndexOf(2))
        assertNull(eating.colorIndexOf(null))
    }

    @Test
    fun `the page compares a camera feeder's day with its own yesterday only`() {
        val before = listOf(
            eaten(11, at(9, 0, yesterday), pet = 1, duration = 60),
            eaten(10, at(8, 0, yesterday), pet = 1, duration = 70).copy(deviceId = 2003),
        )
        val camera = device(D4SH, DeviceFamily.Feeder).copy(isCamera = true)

        val page = FeederRules.page(camera, null, null, cards, at(20, 0), paris, pets, yesterdayItems = before)

        assertEquals(3, page.eating?.today?.meals)
        assertEquals(EatingStats(meals = 1, averageDurationSec = 60), page.eating?.yesterday)
    }

    @Test
    fun `a feeder without a camera has no eating, whatever its cards say`() {
        val page = FeederRules.page(device(D4SH, DeviceFamily.Feeder), null, null, cards, at(20, 0), paris, pets)

        assertNull(page.eating)
    }

    @Test
    fun `durations are unknown only when the episodes of both days have none`() {
        val untimed = cards.map { it.copy(durationSec = null) }
        val timedYesterday = listOf(eaten(11, at(9, 0, yesterday), pet = 1, duration = 60))

        assertFalse(FeederEatingRules.eating(untimed, emptyList(), paris, pets).durationsKnown)
        assertTrue(FeederEatingRules.eating(untimed, timedYesterday, paris, pets).durationsKnown)
        assertTrue(FeederEatingRules.eating(emptyList(), emptyList(), paris, pets).durationsKnown)
    }

    private fun eaten(id: Long, at: Instant, pet: Long?, duration: Int?): TimelineItem =
        item(id, D4SH, at, TimelineCategory.Feeding, pet = pet, duration = duration)
            .copy(kind = "feeding", eventKind = "feeding", label = "Eating done")

    private fun feed(id: Long, at: Instant, label: String): TimelineItem =
        item(id, D4SH, at, TimelineCategory.Feeding).copy(kind = "feeding", eventKind = "feeding", label = label)

    private companion object {
        const val D4SH = 2004L
    }
}

package org.petkitlocal.core.domain.activity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.devicepage.PageFixtures.at
import org.petkitlocal.core.domain.devicepage.PageFixtures.item
import org.petkitlocal.core.domain.devicepage.PageFixtures.pet
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineStep

class ActivityRulesTest {
    private val mochi = pet(1).copy(name = "Mochi", photoUrl = "https://petkit.invalid/app/v1/pets/1/photo")
    private val tigrou = pet(2).copy(name = "Tigrou")
    private val household = listOf(mochi, tigrou)

    private fun step(id: Long, label: String, detail: Boolean = false) =
        TimelineStep(id, at(9, id.toInt() % 60), label, detail)

    @Test
    fun `a visit is one row with the steps the app shows, in the server's order`() {
        val visit = item(10, 1002, at(9, 45), TimelineCategory.Toileting, pet = 1, duration = 95).copy(
            steps = listOf(
                step(11, "Weight check", detail = true),
                step(12, "Mechanism started", detail = true),
                step(13, "Auto cleaning completed"),
                step(14, "Auto deodorizing completed"),
            ),
        )

        val entry = ActivityRules.entries(listOf(visit), household).single()

        assertEquals(ActivityKind.Visit, entry.kind)
        assertEquals("Mochi", entry.petName)
        assertEquals(listOf(13L, 14L), entry.steps.map { it.id })
        assertEquals(listOf("cleaning", "deodorizing"), entry.steps.map { it.parsed?.operation })
        assertTrue(entry.steps.none { it.failed })
    }

    @Test
    fun `a failed step says so with its reason, and one the app cannot read is kept as it is`() {
        val visit = item(20, 1002, at(6, 19), TimelineCategory.Toileting, pet = 2).copy(
            steps = listOf(
                step(21, "Auto cleaning completed"),
                step(22, "Auto deodorizing stopped (bin full)"),
                step(23, "Error cleared"),
            ),
        )

        val steps = ActivityRules.entry(visit, household).steps

        assertEquals(listOf(false, true, false), steps.map { it.failed })
        assertEquals("bin full", steps[1].parsed?.reason)
        assertNull(steps[2].parsed)
        assertEquals("Error cleared", steps[2].label)
    }

    @Test
    fun `a pet's picture is its photo, else its initial on its colour, else a paw`() {
        val withPhoto = item(1, 1002, at(8, 0), TimelineCategory.Toileting, pet = 1)
        val withoutPhoto = item(2, 1002, at(8, 0), TimelineCategory.Toileting, pet = 2)
        val unrecognised = item(3, 1002, at(8, 0), TimelineCategory.Toileting)
        // A pet deleted since: the card still names it, but it has no colour any more.
        val deleted = item(4, 1002, at(8, 0), TimelineCategory.Toileting).copy(pet = PetRef(9, "Filou"))

        val entries = ActivityRules.entries(listOf(withPhoto, withoutPhoto, unrecognised, deleted), household)

        assertEquals(ActivityAvatar.Pet("Mochi", mochi.photoUrl, colorIndex = 0), entries[0].avatar)
        assertEquals(ActivityAvatar.Pet("Tigrou", photoUrl = null, colorIndex = 1), entries[1].avatar)
        assertEquals(ActivityAvatar.Unknown, entries[2].avatar)
        assertNull(entries[2].petName)
        assertEquals(ActivityAvatar.Unknown, entries[3].avatar)
        assertEquals("Filou", entries[3].petName)
    }

    @Test
    fun `drinks and recognised meals show their pet, other rows keep their kind's symbol`() {
        val drink = item(1, 3001, at(7, 0), TimelineCategory.Drinking)
        val meal = item(2, 2001, at(7, 0), TimelineCategory.Feeding)
        val eaten = item(3, 2003, at(7, 0), TimelineCategory.Feeding, pet = 1)
        val seen = item(4, 1004, at(5, 10), TimelineCategory.Pet, pet = 2)
        val cleaning = item(5, 1002, at(3, 0), TimelineCategory.Cleaning).copy(label = "Periodic cleaning completed")

        val entries = ActivityRules.entries(listOf(drink, meal, eaten, seen, cleaning), household)

        assertEquals(
            listOf(ActivityKind.Drink, ActivityKind.Meal, ActivityKind.Meal, ActivityKind.PetSeen, ActivityKind.Event),
            entries.map { it.kind },
        )
        assertEquals(ActivityAvatar.Unknown, entries[0].avatar)
        assertNull(entries[1].avatar)
        assertEquals(ActivityAvatar.Pet("Mochi", mochi.photoUrl, 0), entries[2].avatar)
        assertNull(entries[3].avatar)
        assertEquals("Tigrou", entries[3].petName)
        assertNull(entries[4].avatar)
        assertEquals(StepLabel("cleaning", "Periodic", done = true, reason = null), entries[4].outcome)
        assertFalse(entries.any { it.steps.isNotEmpty() })
    }
}

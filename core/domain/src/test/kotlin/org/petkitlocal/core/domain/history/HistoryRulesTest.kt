package org.petkitlocal.core.domain.history

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.ListOnlyPets
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.devicepage.PageFixtures
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetRef
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

class HistoryRulesTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `a week is the seven calendar days ending on its last day`() {
        assertEquals((9..15).map { LocalDate.of(2026, 11, it) }, HistoryDays.week(today))
    }

    @Test
    fun `a week across a clock change is still seven distinct days, each one date apart`() {
        // Paris leaves summer time on 25 October 2026: that day lasts 25 hours.
        val week = HistoryDays.week(LocalDate.of(2026, 10, 28))
        assertEquals((22..28).map { LocalDate.of(2026, 10, it) }, week)
        val paris = ZoneId.of("Europe/Paris")
        val hours = week.zipWithNext { a, b ->
            Duration.between(a.atStartOfDay(paris), b.atStartOfDay(paris)).toHours()
        }
        // Local midnights are 23, 24 or 25 hours apart: only dates are counted, never hours.
        assertEquals(listOf(24L, 24L, 24L, 25L, 24L, 24L), hours)
    }

    @Test
    fun `moving a week back keeps the weekday, and forward never passes today`() {
        assertEquals(
            LocalDate.of(2026, 11, 8) to LocalDate.of(2026, 11, 5),
            HistoryDays.shift(selected = LocalDate.of(2026, 11, 12), end = today, weeks = -1, today = today),
        )
        // Forward from the week ending on the 8th, the 7th selected: the 14th, in the week ending today.
        assertEquals(
            today to LocalDate.of(2026, 11, 14),
            HistoryDays.shift(
                selected = LocalDate.of(2026, 11, 7),
                end = LocalDate.of(2026, 11, 8),
                weeks = 1,
                today = today,
            ),
        )
        // The same weekday would be tomorrow: today instead.
        assertEquals(
            today to today,
            HistoryDays.shift(
                selected = LocalDate.of(2026, 11, 10),
                end = LocalDate.of(2026, 11, 11),
                weeks = 1,
                today = today,
            ),
        )
    }

    @Test
    fun `a query matches by chip, device and pet`() {
        val visit = item(1, setOf(TimelineCategory.Toileting), deviceId = 1002, petId = 1)
        assertTrue(HistoryQuery(today).matches(visit))
        assertTrue(HistoryQuery(today, TimelineCategory.Toileting, 1002, 1).matches(visit))
        assertFalse(HistoryQuery(today, TimelineCategory.Feeding).matches(visit))
        assertFalse(HistoryQuery(today, deviceId = 1003).matches(visit))
        assertFalse(HistoryQuery(today, petId = 2).matches(visit))
    }

    @Test
    fun `history names each card's device and gives its pet the household's colour`() = runTest {
        val devices = listOf(PageFixtures.device(1002, DeviceFamily.LitterBox))
        val pets = listOf(pet(1, "Mochi"), pet(2, "Tigrou"))
        val items = listOf(
            item(1, setOf(TimelineCategory.Toileting), deviceId = 1002, petId = 2),
            item(2, setOf(TimelineCategory.Feeding), deviceId = 9999, petId = null),
        )
        val useCase = ObserveHistoryUseCase(
            timeline = StaticTimeline(MutableStateFlow(items)),
            devices = StaticDevices(devices),
            pets = StaticPets(pets),
        )

        val content = useCase(HistoryQuery(today)).first()

        assertEquals(1002L, content.entries[0].device?.id)
        assertEquals(1, content.entries[0].petColorIndex)
        assertNull(content.entries[1].device)
        assertNull(content.entries[1].petColorIndex)
        assertEquals(devices, content.devices)
        assertEquals(pets, content.pets)
    }

    private fun item(id: Long, categories: Set<TimelineCategory>, deviceId: Long?, petId: Long?) = TimelineItem(
        id = id,
        kind = "visit",
        categories = categories,
        deviceId = deviceId,
        deviceType = "t4",
        ts = null,
        displayTs = null,
        eventType = null,
        eventKind = null,
        label = null,
        pet = petId?.let { PetRef(it, "Pet $it") },
        durationSec = null,
        weightG = null,
        facts = emptyList(),
    )

    private fun pet(id: Long, name: String) =
        Pet(id, name, emptyList(), null, null, weightLearned = false, photoUrl = null)

    private class StaticTimeline(private val items: Flow<List<TimelineItem>>) : TimelineRepository {
        override fun observeDay(day: LocalDate) = items

        override suspend fun refreshDay(day: LocalDate): Outcome<Unit> = Outcome.Success(Unit)

        override fun observeHistory(query: HistoryQuery) = items.map { list -> list.filter(query::matches) }

        override suspend fun fetchHistoryPage(query: HistoryQuery, cursor: HistoryCursor?) =
            Outcome.Success(HistoryPage(null))

        override suspend fun getEvent(id: Long): Outcome<EventDetail> = error("not used")
    }

    private class StaticDevices(private val devices: List<Device>) : DeviceRepository {
        override fun observeDevices() = flowOf(devices)

        override fun observeDevice(id: Long) = flowOf(devices.firstOrNull { it.id == id })

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private class StaticPets(private val pets: List<Pet>) : ListOnlyPets() {
        override fun observePets() = flowOf(pets)

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
    }
}

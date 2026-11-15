package org.petkitlocal.core.domain.home

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DayOnlyTimeline
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository

class HomeUseCasesTest {
    private val zone = ZoneId.of("Europe/Paris")

    // 23:30 UTC on 14 November is already the 15th in Paris.
    private val clock = Clock.fixed(Instant.parse("2026-11-14T23:30:00Z"), zone)

    private val devices = StubDevices()
    private val pets = StubPets()
    private val timeline = StubTimeline()

    @Test
    fun `today is the local day of the phone's zone`() {
        assertEquals(LocalDate.of(2026, 11, 15), TodayUseCase(clock)())
    }

    @Test
    fun `the overview is built from the cache of the requested day`() = runTest {
        devices.state.value = listOf(device(1))
        pets.state.value = listOf(Pet(1, "Mochi", listOf(1), null, 4200.0, false, null))

        val overview = ObserveHomeUseCase(devices, pets, timeline, clock)(LocalDate.of(2026, 11, 15)).first()

        assertEquals(listOf(1L), overview.devices.map { it.device.id })
        assertEquals(listOf("Mochi"), overview.pets.map { it.pet.name })
        assertEquals(listOf(LocalDate.of(2026, 11, 15)), timeline.observed)
    }

    @Test
    fun `a refresh asks every part and reports the devices' failure first`() = runTest {
        val refresh = RefreshHomeUseCase(devices, pets, timeline)
        val day = LocalDate.of(2026, 11, 15)

        assertEquals(Outcome.Success(Unit), refresh(day))

        timeline.result = Outcome.Failure(DomainError.Server(500, null, null))
        devices.result = Outcome.Failure(DomainError.Network())
        assertEquals(Outcome.Failure(DomainError.Network()), refresh(day))

        devices.result = Outcome.Success(Unit)
        assertEquals(Outcome.Failure(DomainError.Server(500, null, null)), refresh(day))
        assertEquals(3, devices.refreshes)
        assertEquals(listOf(day, day, day), timeline.refreshed)
    }

    private fun device(id: Long) = Device(
        id = id,
        type = "t4",
        model = "Pura Max",
        family = DeviceFamily.LitterBox,
        online = true,
        transport = DeviceTransport.Http,
        lastSeen = null,
        firmware = null,
        isCamera = false,
    )

    private class StubDevices : DeviceRepository {
        val state = MutableStateFlow<List<Device>>(emptyList())
        var result: Outcome<Unit> = Outcome.Success(Unit)
        var refreshes = 0

        override fun observeDevices(): Flow<List<Device>> = state

        override fun observeDevice(id: Long): Flow<Device?> = state.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun refresh(): Outcome<Unit> = result.also { refreshes++ }
    }

    private class StubPets : PetRepository {
        val state = MutableStateFlow<List<Pet>>(emptyList())

        override fun observePets(): Flow<List<Pet>> = state

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private class StubTimeline : DayOnlyTimeline() {
        var result: Outcome<Unit> = Outcome.Success(Unit)
        val observed = mutableListOf<LocalDate>()
        val refreshed = mutableListOf<LocalDate>()

        override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> {
            observed += day
            return MutableStateFlow(emptyList())
        }

        override suspend fun refreshDay(day: LocalDate): Outcome<Unit> = result.also { refreshed += day }
    }
}

package org.petkitlocal.core.testing

import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/*
 * Fakes of the cache-backed repositories (ARCHITECTURE.md §6: fakes, not mocks). Each holds its
 * "cache" in a StateFlow a test sets directly, and answers refreshes with [refreshResult]; a test
 * that wants a refresh to stay in flight sets [gate] and completes it when it is ready.
 */

/** What a fake refresh answers, and optionally what it waits for first. */
open class FakeRefresh {
    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var gate: CompletableDeferred<Unit>? = null
    var refreshCount = 0
        private set

    protected suspend fun refreshed(): Outcome<Unit> {
        refreshCount++
        gate?.await()
        return refreshResult
    }
}

class FakeDeviceRepository(initial: List<Device> = emptyList()) :
    FakeRefresh(),
    DeviceRepository {
    val devices = MutableStateFlow(initial)

    override fun observeDevices(): Flow<List<Device>> = devices

    override fun observeDevice(id: Long): Flow<Device?> = devices.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refresh(): Outcome<Unit> = refreshed()
}

class FakePetRepository(initial: List<Pet> = emptyList()) :
    FakeRefresh(),
    PetRepository {
    val pets = MutableStateFlow(initial)

    override fun observePets(): Flow<List<Pet>> = pets

    override suspend fun refresh(): Outcome<Unit> = refreshed()
}

class FakeTimelineRepository :
    FakeRefresh(),
    TimelineRepository {
    /** The cached cards, by day. */
    val days = MutableStateFlow<Map<LocalDate, List<TimelineItem>>>(emptyMap())
    val refreshedDays = mutableListOf<LocalDate>()

    override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> = days.map { it[day].orEmpty() }

    override suspend fun refreshDay(day: LocalDate): Outcome<Unit> {
        refreshedDays += day
        return refreshed()
    }
}

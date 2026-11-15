package org.petkitlocal.core.testing

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryPage
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.WeightSample
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

    /** The cached activity, by pet. */
    val activity = MutableStateFlow<Map<Long, PetActivity>>(emptyMap())

    /** The cached weighed visits, by pet. */
    val weights = MutableStateFlow<Map<Long, List<WeightSample>>>(emptyMap())

    /** What [refreshPet] answers; [refreshPetGate], when set, is waited for first. */
    var refreshPetResult: Outcome<Unit> = Outcome.Success(Unit)
    var refreshPetGate: CompletableDeferred<Unit>? = null
    val refreshedPets = mutableListOf<Long>()

    /** What [refreshWeights] answers; [weightsGate], when set, is waited for first. */
    var weightsResult: Outcome<Unit> = Outcome.Success(Unit)
    var weightsGate: CompletableDeferred<Unit>? = null
    val weightRequests = mutableListOf<Pair<Long, Instant>>()

    override fun observePets(): Flow<List<Pet>> = pets

    override suspend fun refresh(): Outcome<Unit> = refreshed()

    override fun observeActivity(petId: Long): Flow<PetActivity?> = activity.map { it[petId] }

    override suspend fun refreshPet(petId: Long): Outcome<Unit> {
        refreshedPets += petId
        refreshPetGate?.await()
        return refreshPetResult
    }

    override fun observeWeights(petId: Long, since: Instant): Flow<List<WeightSample>> =
        weights.map { cache -> cache[petId].orEmpty().filter { it.at >= since }.sortedBy { it.at } }

    override suspend fun refreshWeights(petId: Long, since: Instant): Outcome<Unit> {
        weightRequests += petId to since
        weightsGate?.await()
        return weightsResult
    }
}

class FakeTimelineRepository :
    FakeRefresh(),
    TimelineRepository {
    /** The cached cards, by day. */
    val days = MutableStateFlow<Map<LocalDate, List<TimelineItem>>>(emptyMap())
    val refreshedDays = mutableListOf<LocalDate>()

    /** Every History page asked for, in order. */
    val pageRequests = mutableListOf<Pair<HistoryQuery, HistoryCursor?>>()

    /** What a page answers; by default the day's last page. A test may write [days] in it. */
    var pageResult: (
        HistoryQuery,
        HistoryCursor?,
    ) -> Outcome<HistoryPage> = { _, _ -> Outcome.Success(HistoryPage(null)) }

    /** When set, every page waits for it before answering. */
    var pageGate: CompletableDeferred<Unit>? = null

    /** The events `getEvent` finds; any other id is not found. */
    val events = mutableMapOf<Long, EventDetail>()
    var eventGate: CompletableDeferred<Unit>? = null

    /** When set, `getEvent` fails with it. */
    var eventError: DomainError? = null

    override fun observeDay(day: LocalDate): Flow<List<TimelineItem>> = days.map { it[day].orEmpty() }

    override suspend fun refreshDay(day: LocalDate): Outcome<Unit> {
        refreshedDays += day
        return refreshed()
    }

    override fun observeHistory(query: HistoryQuery): Flow<List<TimelineItem>> =
        days.map { cache -> cache[query.day].orEmpty().filter(query::matches) }

    override suspend fun fetchHistoryPage(query: HistoryQuery, cursor: HistoryCursor?): Outcome<HistoryPage> {
        pageRequests += query to cursor
        pageGate?.await()
        return pageResult(query, cursor)
    }

    override suspend fun getEvent(id: Long): Outcome<EventDetail> {
        eventGate?.await()
        eventError?.let { return Outcome.Failure(it) }
        return events[id]?.let { Outcome.Success(it) }
            ?: Outcome.Failure(DomainError.NotFound("event_not_found", "no such event"))
    }
}

package org.petkitlocal.core.testing

import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryPage
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.PetFace
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.model.WeightSample
import org.petkitlocal.core.domain.pets.FaceUpload
import org.petkitlocal.core.domain.pets.FaceWarning
import org.petkitlocal.core.domain.pets.PetChanges
import org.petkitlocal.core.domain.pets.PetDraft
import org.petkitlocal.core.domain.pets.PetFaces
import org.petkitlocal.core.domain.pets.PetsSource
import org.petkitlocal.core.domain.pets.PetsSourceSetting
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

    // --- writes: a small server of its own. A failure set below is answered instead, once each
    // gate (when set) opens; nothing is written then.

    /** Every write asked for, in order, as text: `create Moka`, `update 1 PetChanges(...)`, `delete 1`... */
    val writes = mutableListOf<String>()
    var writeGate: CompletableDeferred<Unit>? = null
    var writeFailure: DomainError? = null
    var maxFaces = 6

    /** The warnings the next upload answers with. */
    var uploadWarnings: List<FaceWarning> = emptyList()
    var uploadFailure: DomainError? = null
    var facesFailure: DomainError? = null
    private var nextFaceId = 100L

    /** The setting [petsSource] answers; null answers [petsSourceFailure]. */
    var petsSourceSetting: PetsSourceSetting? =
        PetsSourceSetting(PetsSource.PetKit, proxyMode = false, PetsSource.Local, PetsSource.entries)
    var petsSourceFailure: DomainError = DomainError.Network()

    override suspend fun createPet(draft: PetDraft): Outcome<Pet> = write("create ${draft.name}") {
        val pet = Pet(
            id = (pets.value.maxOfOrNull { it.id } ?: 0) + 1,
            name = draft.name,
            deviceIds = draft.deviceIds,
            enteredWeightG = draft.weightG,
            weightG = draft.weightG,
            weightLearned = false,
            photoUrl = null,
        )
        pets.update { it + pet }
        pet
    }

    override suspend fun updatePet(petId: Long, changes: PetChanges): Outcome<Pet> = write("update $petId $changes") {
        val pet = pets.value.first { it.id == petId }
        val weight = if (changes.clearWeight) null else changes.weightG ?: pet.enteredWeightG
        val changed = pet.copy(
            name = changes.name ?: pet.name,
            deviceIds = changes.deviceIds ?: pet.deviceIds,
            enteredWeightG = weight,
            weightG = if (weight != pet.enteredWeightG) weight else pet.weightG,
        )
        pets.update { list -> list.map { if (it.id == petId) changed else it } }
        changed
    }

    override suspend fun deletePet(petId: Long): Outcome<Unit> = write("delete $petId") {
        pets.update { list -> list.filterNot { it.id == petId } }
    }

    override suspend fun refreshFaces(petId: Long): Outcome<PetFaces> {
        writes += "faces $petId"
        facesFailure?.let { return Outcome.Failure(it) }
        return Outcome.Success(PetFaces(pets.value.first { it.id == petId }.faces, maxFaces))
    }

    override suspend fun addFace(petId: Long, jpeg: ByteArray): Outcome<FaceUpload> {
        writes += "upload $petId ${jpeg.size}"
        writeGate?.await()
        uploadFailure?.let { return Outcome.Failure(it) }
        val id = nextFaceId++
        val face = PetFace(id, "https://petkit.invalid/app/v1/pets/$petId/faces/$id/photo")
        pets.update { list -> list.map { if (it.id == petId) it.copy(faces = it.faces + face) else it } }
        return Outcome.Success(FaceUpload(face, uploadWarnings))
    }

    override suspend fun deleteFace(petId: Long, faceId: Long): Outcome<Unit> = write("delete face $faceId") {
        pets.update { list ->
            list.map { pet -> if (pet.id == petId) pet.copy(faces = pet.faces.filterNot { it.id == faceId }) else pet }
        }
    }

    override suspend fun petsSource(): Outcome<PetsSourceSetting> =
        petsSourceSetting?.let { Outcome.Success(it) } ?: Outcome.Failure(petsSourceFailure)

    override suspend fun setPetsSource(source: PetsSource): Outcome<PetsSourceSetting> = write("source $source") {
        val current = checkNotNull(petsSourceSetting)
        current.copy(source = source, effective = if (current.proxyMode) source else PetsSource.Local)
            .also { petsSourceSetting = it }
    }

    private suspend fun <T> write(label: String, apply: () -> T): Outcome<T> {
        writes += label
        writeGate?.await()
        writeFailure?.let { return Outcome.Failure(it) }
        return Outcome.Success(apply())
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

    /** The cached cards holding a clip, by device, newest first. */
    val clips = MutableStateFlow<Map<Long, List<TimelineItem>>>(emptyMap())

    /** The cached cards holding a photo or a video, by device, newest first. */
    val withMedia = MutableStateFlow<Map<Long, List<TimelineItem>>>(emptyMap())

    /** Every device whose recent clips were refreshed, in order. */
    val refreshedClips = mutableListOf<Long>()

    /** What a refresh of recent clips answers; [clipsGate], when set, is waited for first. */
    var clipsResult: Outcome<Unit> = Outcome.Success(Unit)
    var clipsGate: CompletableDeferred<Unit>? = null

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

    override fun observeRecentClips(deviceId: Long, limit: Int): Flow<List<TimelineItem>> =
        clips.map { it[deviceId].orEmpty().take(limit) }

    override fun observeNewestWithMedia(deviceId: Long, limit: Int): Flow<List<TimelineItem>> =
        withMedia.map { it[deviceId].orEmpty().take(limit) }

    override suspend fun refreshRecentClips(deviceId: Long, count: Int): Outcome<Unit> {
        refreshedClips += deviceId
        clipsGate?.await()
        return clipsResult
    }

    override suspend fun getEvent(id: Long): Outcome<EventDetail> {
        eventGate?.await()
        eventError?.let { return Outcome.Failure(it) }
        return events[id]?.let { Outcome.Success(it) }
            ?: Outcome.Failure(DomainError.NotFound("event_not_found", "no such event"))
    }
}

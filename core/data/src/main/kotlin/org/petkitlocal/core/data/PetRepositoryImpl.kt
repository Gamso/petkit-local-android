package org.petkitlocal.core.data

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl
import org.petkitlocal.core.database.dao.PetDao
import org.petkitlocal.core.database.model.PetActivityRow
import org.petkitlocal.core.database.model.PetRow
import org.petkitlocal.core.database.model.PetWeightRow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.map
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.PetStats
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.WeightSample
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.ServerAddressProvider
import org.petkitlocal.core.network.dto.PetDetailDto
import org.petkitlocal.core.network.dto.PetDto
import org.petkitlocal.core.network.dto.TimelineItemDto
import org.petkitlocal.core.network.error.apiCall

/**
 * Pets from Room (ADR 0002). [refresh] replaces them with `GET /pets`; [refreshPet] reads one
 * pet's statistics and recent visits (`GET /pets/{id}`); [refreshWeights] reads its weighed
 * visits from the timeline, which is the only place the API serves a pet's weights over time.
 */
class PetRepositoryImpl @Inject constructor(
    private val api: PetkitApi,
    private val dao: PetDao,
    private val clock: Clock,
    private val serverAddress: ServerAddressProvider,
) : PetRepository {
    override fun observePets(): Flow<List<Pet>> = dao.observePets().map { rows ->
        val base = serverAddress.apiBaseUrl()
        rows.map { it.toDomain(base) }
    }

    override suspend fun refresh(): Outcome<Unit> {
        val result = apiCall { api.listPets() }
        if (result is Outcome.Success) {
            dao.replaceAll(result.value.pets.mapIndexed { index, pet -> pet.toRow(index) })
        }
        return result.map { }
    }

    override fun observeActivity(petId: Long): Flow<PetActivity?> = dao.observeActivity(petId).map { row ->
        row?.toDomain(serverAddress.apiBaseUrl())
    }

    override suspend fun refreshPet(petId: Long): Outcome<Unit> {
        val result = apiCall { api.getPet(petId) }
        when (result) {
            is Outcome.Success -> {
                val pet = result.value.pet
                // The position is the list's; the DAO keeps the one the pet already has.
                dao.writePet(pet.toRow(), pet.toActivityRow(clock.millis()))
            }

            // Only the server's own word that the pet is gone; any other 404 says nothing of it.
            is Outcome.Failure -> if ((result.error as? DomainError.NotFound)?.code == PET_NOT_FOUND) {
                dao.deletePet(petId)
            }
        }
        return result.map { }
    }

    override fun observeWeights(petId: Long, since: Instant): Flow<List<WeightSample>> =
        dao.observeWeights(petId, since.epochSecond.toDouble()).map { rows ->
            rows.map { WeightSample(it.visitId, epochSecondsToInstant(it.ts), it.weightG) }
        }

    override suspend fun refreshWeights(petId: Long, since: Instant): Outcome<Unit> {
        val sinceTs = since.epochSecond.toDouble()
        val weights = mutableListOf<PetWeightRow>()
        var cursor: String? = null
        var oldest = Double.POSITIVE_INFINITY
        repeat(MAX_WEIGHT_PAGES) {
            val page = when (
                val result = apiCall {
                    api.getTimeline(
                        kind = TimelineCategory.Toileting.key,
                        pet = petId,
                        limit = WEIGHT_PAGE_SIZE,
                        cursor = cursor,
                    )
                }
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
            page.items.forEach { item ->
                item.ts?.let { oldest = minOf(oldest, it) }
                item.toWeightRow(petId)?.let(weights::add)
            }
            cursor = page.nextCursor
            // The server has nothing older, or the pages reached back to the start of the range.
            if (cursor == null || oldest < sinceTs) {
                dao.replaceWeights(petId, sinceTs, weights.filter { it.ts >= sinceTs })
                return Outcome.Success(Unit)
            }
        }
        // A pet with more visits than MAX_WEIGHT_PAGES pages hold: the cache matches the server
        // over the time those pages covered, and keeps what it had before that.
        dao.replaceWeights(petId, oldest, weights.filter { it.ts >= oldest })
        return Outcome.Success(Unit)
    }

    private companion object {
        const val PET_NOT_FOUND = "pet_not_found"

        /** The most `GET /timeline` allows. */
        const val WEIGHT_PAGE_SIZE = 100

        /** Enough for six months of a pet visiting a dozen times a day. */
        const val MAX_WEIGHT_PAGES = 24
    }
}

private val longList = ListSerializer(Long.serializer())
private val timelineItems = ListSerializer(TimelineItemDto.serializer())

internal fun PetDto.toRow(position: Int) = PetRow(
    id = id,
    position = position,
    name = name,
    deviceIdsJson = PetkitJson.encodeToString(longList, deviceIds),
    enteredWeightG = enteredWeightG,
    weightG = weightG,
    weightLearned = weightLearned,
    photo = photo,
)

internal fun PetDetailDto.toRow() =
    PetDto(id, name, deviceIds, enteredWeightG, weightG, weightLearned, photo).toRow(position = 0)

internal fun PetDetailDto.toActivityRow(fetchedAtMillis: Long) = PetActivityRow(
    petId = id,
    medianWeightG = stats.medianWeightG,
    visitsToday = stats.visitsToday,
    lastVisitTs = stats.lastVisitTs,
    lastVisitWeightG = stats.lastVisitWeightG,
    lastVisitDurationSec = stats.lastVisitDurationSec,
    lastDeviceId = stats.lastDeviceId,
    recentVisitsJson = PetkitJson.encodeToString(timelineItems, recentVisits),
    fetchedAt = fetchedAtMillis,
)

internal fun PetActivityRow.toDomain(apiBase: HttpUrl?) = PetActivity(
    petId = petId,
    stats = PetStats(
        medianWeightG = medianWeightG,
        visitsToday = visitsToday,
        lastVisitAt = lastVisitTs?.let(::epochSecondsToInstant),
        lastVisitWeightG = lastVisitWeightG,
        lastVisitDurationSec = lastVisitDurationSec?.toInt(),
        lastDeviceId = lastDeviceId,
    ),
    // A card here is mapped as the timeline's own are; it is filed under no day.
    recentVisits = decodeVisits(recentVisitsJson).map { it.toRow(day = "").toDomain(apiBase) },
    fetchedAt = Instant.ofEpochMilli(fetchedAt),
)

private fun decodeVisits(json: String): List<TimelineItemDto> = try {
    PetkitJson.decodeFromString(timelineItems, json)
} catch (_: SerializationException) {
    emptyList()
} catch (_: IllegalArgumentException) {
    emptyList()
}

/** A visit of [petId] the scale weighed, placed when it happened; null for any other card. */
internal fun TimelineItemDto.toWeightRow(petId: Long): PetWeightRow? {
    val weight = weightG?.takeIf { it > 0 && kind == VISIT && pet?.id == petId }
    val at = displayTs ?: ts
    if (weight == null || at == null) return null
    return PetWeightRow(visitId = id, petId = petId, ts = at, weightG = weight)
}

/** The card kind of a toilet visit (`TimelineItem.kind`). */
private const val VISIT = "visit"

internal fun PetRow.toDomain(apiBase: HttpUrl?) = Pet(
    id = id,
    name = name,
    deviceIds = try {
        PetkitJson.decodeFromString(longList, deviceIdsJson)
    } catch (_: IllegalArgumentException) {
        emptyList()
    },
    enteredWeightG = enteredWeightG,
    weightG = weightG ?: enteredWeightG,
    weightLearned = weightLearned,
    photoUrl = photo?.let { apiUrl(apiBase, it) },
)

private const val API_PREFIX = "/app/v1/"

/**
 * An API path the server sent (`/app/v1/pets/1/photo`) as an absolute URL on [apiBase], keeping
 * the base's own path prefix, so a server behind `https://example.com/petkit/` still works. Null
 * when no server is configured, or for a path outside the API, which is never fetched with the
 * bearer token.
 */
internal fun apiUrl(apiBase: HttpUrl?, path: String): String? {
    if (apiBase == null || !path.startsWith(API_PREFIX)) return null
    return apiBase.resolve(path.removePrefix(API_PREFIX))?.toString()
}

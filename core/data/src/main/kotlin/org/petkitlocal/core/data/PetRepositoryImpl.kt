package org.petkitlocal.core.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl
import org.petkitlocal.core.database.dao.PetDao
import org.petkitlocal.core.database.model.PetRow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.map
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.ServerAddressProvider
import org.petkitlocal.core.network.dto.PetDto
import org.petkitlocal.core.network.error.apiCall

/** Pets from Room; [refresh] replaces them with `GET /pets`. */
class PetRepositoryImpl @Inject constructor(
    private val api: PetkitApi,
    private val dao: PetDao,
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
}

private val longList = ListSerializer(Long.serializer())

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

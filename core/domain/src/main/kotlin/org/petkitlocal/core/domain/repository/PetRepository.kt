package org.petkitlocal.core.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.WeightSample
import org.petkitlocal.core.domain.pets.FaceUpload
import org.petkitlocal.core.domain.pets.PetChanges
import org.petkitlocal.core.domain.pets.PetDraft
import org.petkitlocal.core.domain.pets.PetFaces
import org.petkitlocal.core.domain.pets.PetsSource
import org.petkitlocal.core.domain.pets.PetsSourceSetting

/**
 * The household's pets, from the local cache (ADR 0002); the refreshes replace it from the
 * server. A pet's page adds what the list does not carry: its statistics and recent visits
 * ([refreshPet]) and its weighed visits over a range of days ([refreshWeights]). The writes go to
 * the server first; the cache changes only with what it answered (ADR 0002).
 */
interface PetRepository {
    /** Every pet, in the server's order, which is also the order of the pet colours. */
    fun observePets(): Flow<List<Pet>>

    suspend fun refresh(): Outcome<Unit>

    /** The pet's statistics and recent visits as last read; null until read once. */
    fun observeActivity(petId: Long): Flow<PetActivity?>

    /**
     * Reads the pet (`GET /pets/{id}`): its row and its activity are replaced. A pet the server
     * no longer has is dropped from the cache, and the result is that [Outcome.Failure].
     */
    suspend fun refreshPet(petId: Long): Outcome<Unit>

    /** The pet's cached weighed visits from [since] on, oldest first. */
    fun observeWeights(petId: Long, since: Instant): Flow<List<WeightSample>>

    /**
     * Reads the pet's visits back to [since] from the timeline (`GET /timeline?pet=&kind=toileting`,
     * page after page) and makes the cache's weighed visits match the server over the time the
     * pages covered. The server only serves what it still keeps: older visits may be gone.
     */
    suspend fun refreshWeights(petId: Long, since: Instant): Outcome<Unit>

    /** Creates a pet; the cache gets it as the server answered. */
    suspend fun createPet(draft: PetDraft): Outcome<Pet>

    /** Changes only what [changes] names; the cache gets the pet as the server answered. */
    suspend fun updatePet(petId: Long, changes: PetChanges): Outcome<Pet>

    /** Deletes the pet and its photos; its visits stay in the history, unnamed. */
    suspend fun deletePet(petId: Long): Outcome<Unit>

    /** Reads the pet's photos into the cache, and how many it may have. */
    suspend fun refreshFaces(petId: Long): Outcome<PetFaces>

    /** Sends one photo, the bytes of a JPEG; the cache gets the pet's photos as they now are. */
    suspend fun addFace(petId: Long, jpeg: ByteArray): Outcome<FaceUpload>

    suspend fun deleteFace(petId: Long, faceId: Long): Outcome<Unit>

    /** Whose pets the devices recognise in proxy mode; a server setting, read each time. */
    suspend fun petsSource(): Outcome<PetsSourceSetting>

    suspend fun setPetsSource(source: PetsSource): Outcome<PetsSourceSetting>
}

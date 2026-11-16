package org.petkitlocal.core.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.WeightSample

/**
 * The household's pets, from the local cache (ADR 0002); the refreshes replace it from the
 * server. A pet's page adds what the list does not carry: its statistics and recent visits
 * ([refreshPet]) and its weighed visits over a range of days ([refreshWeights]).
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
}

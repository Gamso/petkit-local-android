package org.petkitlocal.core.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.WeightSample
import org.petkitlocal.core.domain.repository.PetRepository

/** A stub of the pets for tests that read the list only: a pet's page is not expected. */
abstract class ListOnlyPets : PetRepository {
    override fun observeActivity(petId: Long): Flow<PetActivity?> = error("A pet's page is not read here")

    override suspend fun refreshPet(petId: Long): Outcome<Unit> = error("A pet's page is not read here")

    override fun observeWeights(petId: Long, since: Instant): Flow<List<WeightSample>> =
        error("A pet's page is not read here")

    override suspend fun refreshWeights(petId: Long, since: Instant): Outcome<Unit> =
        error("A pet's page is not read here")
}

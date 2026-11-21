package org.petkitlocal.core.domain

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetActivity
import org.petkitlocal.core.domain.model.WeightSample
import org.petkitlocal.core.domain.pets.FaceUpload
import org.petkitlocal.core.domain.pets.PetChanges
import org.petkitlocal.core.domain.pets.PetDraft
import org.petkitlocal.core.domain.pets.PetFaces
import org.petkitlocal.core.domain.pets.PetsSource
import org.petkitlocal.core.domain.pets.PetsSourceSetting
import org.petkitlocal.core.domain.repository.PetRepository

/** A stub of the pets for tests that read the list only: a pet's page is not expected. */
abstract class ListOnlyPets : PetRepository {
    override fun observeActivity(petId: Long): Flow<PetActivity?> = error("A pet's page is not read here")

    override suspend fun refreshPet(petId: Long): Outcome<Unit> = error("A pet's page is not read here")

    override fun observeWeights(petId: Long, since: Instant): Flow<List<WeightSample>> =
        error("A pet's page is not read here")

    override suspend fun refreshWeights(petId: Long, since: Instant): Outcome<Unit> =
        error("A pet's page is not read here")

    override suspend fun createPet(draft: PetDraft): Outcome<Pet> = error(NO_WRITES)

    override suspend fun updatePet(petId: Long, changes: PetChanges): Outcome<Pet> = error(NO_WRITES)

    override suspend fun deletePet(petId: Long): Outcome<Unit> = error(NO_WRITES)

    override suspend fun refreshFaces(petId: Long): Outcome<PetFaces> = error(NO_WRITES)

    override suspend fun addFace(petId: Long, jpeg: ByteArray): Outcome<FaceUpload> = error(NO_WRITES)

    override suspend fun deleteFace(petId: Long, faceId: Long): Outcome<Unit> = error(NO_WRITES)

    override suspend fun petsSource(): Outcome<PetsSourceSetting> = error(NO_WRITES)

    override suspend fun setPetsSource(source: PetsSource): Outcome<PetsSourceSetting> = error(NO_WRITES)

    private companion object {
        const val NO_WRITES = "Pets are not managed here"
    }
}

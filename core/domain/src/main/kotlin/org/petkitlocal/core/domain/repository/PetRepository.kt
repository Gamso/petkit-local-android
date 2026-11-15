package org.petkitlocal.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Pet

/** The household's pets, from the local cache; [refresh] replaces it from the server. */
interface PetRepository {
    /** Every pet, in the server's order, which is also the order of the pet colours. */
    fun observePets(): Flow<List<Pet>>

    suspend fun refresh(): Outcome<Unit>
}

package org.petkitlocal.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.database.model.PetActivityRow
import org.petkitlocal.core.database.model.PetRow
import org.petkitlocal.core.database.model.PetWeightRow

@Dao
@Suppress("TooManyFunctions") // One per statement Room runs, and the transactions made of them.
abstract class PetDao {
    @Query("SELECT * FROM pets ORDER BY position, id")
    abstract fun observePets(): Flow<List<PetRow>>

    /**
     * Makes the cache the server's list of pets. What the cache holds of a pet that is not in it
     * any more (its activity and weighed visits) goes with it.
     */
    @Transaction
    open suspend fun replaceAll(pets: List<PetRow>) {
        deleteAll()
        insert(pets)
        val ids = pets.map { it.id }
        deleteActivityExcept(ids)
        deleteWeightsExcept(ids)
    }

    @Query("SELECT * FROM pet_activity WHERE pet_id = :petId")
    abstract fun observeActivity(petId: Long): Flow<PetActivityRow?>

    /**
     * One pet as `GET /pets/{id}` read it: its row (in its place in the list, or after the others
     * when the list does not have it yet) and its activity.
     */
    @Transaction
    open suspend fun writePet(pet: PetRow, activity: PetActivityRow) {
        val position = positionOf(pet.id) ?: ((lastPosition() ?: -1) + 1)
        upsertPetRow(pet.copy(position = position))
        upsertActivity(activity)
    }

    /**
     * One pet as the server answered a write or announced it (`/ws` `pet`): its row only, in its
     * place in the list, or after the others when it is new. Its activity stays as it was read.
     */
    @Transaction
    open suspend fun upsertPet(pet: PetRow) {
        val position = positionOf(pet.id) ?: ((lastPosition() ?: -1) + 1)
        upsertPetRow(pet.copy(position = position))
    }

    /** A pet's face photos as the server listed them, and its photo (the first of them). */
    @Query("UPDATE pets SET faces_json = :facesJson, photo = :photo WHERE id = :petId")
    abstract suspend fun updateFaces(petId: Long, facesJson: String, photo: String?)

    /** A pet the server no longer has, and everything the cache holds of it. */
    @Transaction
    open suspend fun deletePet(petId: Long) {
        deletePetRow(petId)
        deleteActivity(petId)
        deleteWeights(petId)
    }

    /** The weighed visits of [petId] from [sinceTs] (epoch seconds) on, oldest first. */
    @Query("SELECT * FROM pet_weights WHERE pet_id = :petId AND ts >= :sinceTs ORDER BY ts, visit_id")
    abstract fun observeWeights(petId: Long, sinceTs: Double): Flow<List<PetWeightRow>>

    /**
     * Makes the weighed visits of [petId] from [fromTs] (epoch seconds) on exactly [weights]: the
     * timeline pages read covered that time, so a cached visit they did not have is gone.
     */
    @Transaction
    open suspend fun replaceWeights(petId: Long, fromTs: Double, weights: List<PetWeightRow>) {
        deleteWeightsFrom(petId, fromTs)
        upsertWeights(weights)
    }

    @Query("DELETE FROM pets")
    protected abstract suspend fun deleteAll()

    @Insert
    protected abstract suspend fun insert(pets: List<PetRow>)

    @Query("SELECT position FROM pets WHERE id = :id")
    protected abstract suspend fun positionOf(id: Long): Int?

    @Query("SELECT MAX(position) FROM pets")
    protected abstract suspend fun lastPosition(): Int?

    @Upsert
    protected abstract suspend fun upsertPetRow(pet: PetRow)

    @Upsert
    protected abstract suspend fun upsertActivity(activity: PetActivityRow)

    @Upsert
    protected abstract suspend fun upsertWeights(weights: List<PetWeightRow>)

    @Query("DELETE FROM pets WHERE id = :id")
    protected abstract suspend fun deletePetRow(id: Long)

    @Query("DELETE FROM pet_activity WHERE pet_id = :petId")
    protected abstract suspend fun deleteActivity(petId: Long)

    @Query("DELETE FROM pet_activity WHERE pet_id NOT IN (:ids)")
    protected abstract suspend fun deleteActivityExcept(ids: List<Long>)

    @Query("DELETE FROM pet_weights WHERE pet_id = :petId")
    protected abstract suspend fun deleteWeights(petId: Long)

    @Query("DELETE FROM pet_weights WHERE pet_id NOT IN (:ids)")
    protected abstract suspend fun deleteWeightsExcept(ids: List<Long>)

    @Query("DELETE FROM pet_weights WHERE pet_id = :petId AND ts >= :fromTs")
    protected abstract suspend fun deleteWeightsFrom(petId: Long, fromTs: Double)
}

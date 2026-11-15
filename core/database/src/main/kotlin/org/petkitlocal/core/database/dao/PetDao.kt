package org.petkitlocal.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.database.model.PetRow

@Dao
abstract class PetDao {
    @Query("SELECT * FROM pets ORDER BY position, id")
    abstract fun observePets(): Flow<List<PetRow>>

    /** Makes the cache the server's list of pets. */
    @Transaction
    open suspend fun replaceAll(pets: List<PetRow>) {
        deleteAll()
        insert(pets)
    }

    @Query("DELETE FROM pets")
    protected abstract suspend fun deleteAll()

    @Insert
    protected abstract suspend fun insert(pets: List<PetRow>)
}

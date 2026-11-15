package org.petkitlocal.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.database.model.TimelineItemRow

@Dao
abstract class TimelineDao {
    /** The cards of [day] (`YYYY-MM-DD`), newest first, as the server orders them. */
    @Query("SELECT * FROM timeline_items WHERE day = :day ORDER BY ts DESC, id DESC")
    abstract fun observeDay(day: String): Flow<List<TimelineItemRow>>

    /** Makes the cache's [day] exactly [items]. */
    @Transaction
    open suspend fun replaceDay(day: String, items: List<TimelineItemRow>) {
        deleteDay(day)
        upsert(items)
    }

    @Query("DELETE FROM timeline_items WHERE day = :day")
    protected abstract suspend fun deleteDay(day: String)

    // A card's id is unique; one fetched under another day before moves to this one.
    @Upsert
    protected abstract suspend fun upsert(items: List<TimelineItemRow>)
}

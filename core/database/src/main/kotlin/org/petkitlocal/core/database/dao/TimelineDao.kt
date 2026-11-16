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

    /** The cards [filter] selects, newest first, as the server orders them. */
    fun observeHistory(filter: TimelineFilter): Flow<List<TimelineItemRow>> =
        observeFiltered(filter.day, filter.deviceId, filter.petId, filter.likePattern)

    /** Makes the cache's [day] exactly [items]. */
    @Transaction
    open suspend fun replaceDay(day: String, items: List<TimelineItemRow>) {
        deleteDay(day)
        upsert(items)
    }

    /**
     * One page of `GET /timeline` for [filter]: [items] are written, and the cached cards [filter]
     * selects in the range the page covers, but absent from it, are dropped — the server no longer
     * has them, or they moved out of this query. The range runs from just after [newerThan] (the
     * last card of the page before; null for the first page) down to [items]' last card, or to the
     * end of the day when [lastPage] says there is nothing after it.
     */
    @Transaction
    open suspend fun writePage(
        filter: TimelineFilter,
        items: List<TimelineItemRow>,
        newerThan: TimelineKey?,
        lastPage: Boolean,
    ) {
        val oldest = if (lastPage) null else items.lastOrNull()?.key
        // A page with no card that is not the last one covers nothing: there is nothing to drop.
        if (lastPage || oldest != null) {
            val kept = items.mapTo(HashSet()) { it.id }
            val gone = keys(filter.day, filter.deviceId, filter.petId, filter.likePattern)
                .filter { it.id !in kept && it.isWithin(newerThan, oldest) }
                .map { it.id }
            if (gone.isNotEmpty()) deleteIds(gone)
        }
        upsert(items)
    }

    /**
     * A card from a live `timeline` message: written (or rewritten, by id) under [item]'s day, and
     * the cards it [replaces] are dropped, whatever day they were filed under.
     */
    @Transaction
    open suspend fun upsertLive(item: TimelineItemRow, replaces: List<Long>) {
        val gone = replaces.filter { it != item.id }
        if (gone.isNotEmpty()) deleteIds(gone)
        upsert(listOf(item))
    }

    @Query(
        """
        SELECT * FROM timeline_items
        WHERE day = :day
          AND (:deviceId IS NULL OR device_id = :deviceId)
          AND (:petId IS NULL OR pet_id = :petId)
          AND (:filterPattern IS NULL OR filters_json LIKE :filterPattern ESCAPE '\')
        ORDER BY ts DESC, id DESC
        """,
    )
    protected abstract fun observeFiltered(
        day: String,
        deviceId: Long?,
        petId: Long?,
        filterPattern: String?,
    ): Flow<List<TimelineItemRow>>

    @Query(
        """
        SELECT id, ts FROM timeline_items
        WHERE day = :day
          AND (:deviceId IS NULL OR device_id = :deviceId)
          AND (:petId IS NULL OR pet_id = :petId)
          AND (:filterPattern IS NULL OR filters_json LIKE :filterPattern ESCAPE '\')
        """,
    )
    protected abstract suspend fun keys(
        day: String,
        deviceId: Long?,
        petId: Long?,
        filterPattern: String?,
    ): List<TimelineKey>

    @Query("DELETE FROM timeline_items WHERE id IN (:ids)")
    protected abstract suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM timeline_items WHERE day = :day")
    protected abstract suspend fun deleteDay(day: String)

    // A card's id is unique; one fetched under another day before moves to this one.
    @Upsert
    protected abstract suspend fun upsert(items: List<TimelineItemRow>)
}

/**
 * The `GET /timeline` filters a History query uses, as the cache applies them: the cards filed
 * under [day] (`YYYY-MM-DD`), of [deviceId] and [petId] when set, under the chip [filterKey] when
 * set (a key of the card's `filters`).
 */
data class TimelineFilter(
    val day: String,
    val deviceId: Long? = null,
    val petId: Long? = null,
    val filterKey: String? = null,
) {
    /** `filters_json` is a JSON array of plain keys: the key, quoted, appears in it verbatim. */
    internal val likePattern: String?
        get() = filterKey?.let { key ->
            val escaped = key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            "%\"$escaped\"%"
        }
}

/** A card's place in the server's order, newest first by `ts` then `id`; no `ts` sorts last. */
data class TimelineKey(val id: Long, val ts: Double?) {
    private fun isNewerThan(other: TimelineKey): Boolean {
        val mine = ts ?: Double.NEGATIVE_INFINITY
        val theirs = other.ts ?: Double.NEGATIVE_INFINITY
        return mine > theirs || (mine == theirs && id > other.id)
    }

    /** Strictly after [newerBound] in the order (when set), and not past [oldestIncluded] (when set). */
    internal fun isWithin(newerBound: TimelineKey?, oldestIncluded: TimelineKey?): Boolean =
        (newerBound == null || newerBound.isNewerThan(this)) &&
            (oldestIncluded == null || !oldestIncluded.isNewerThan(this))
}

private val TimelineItemRow.key get() = TimelineKey(id, ts)

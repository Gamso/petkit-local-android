package org.petkitlocal.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Portions

/**
 * What each meal a feeder served put out, per hopper on a Dual-Hopper. A timeline card does not
 * say it: only the meal's own event does (`GET /timeline/{event_id}`, its `real_amount` fields),
 * so it is read once per meal and kept in the cache (ADR 0002), a served meal never changing.
 */
interface ServedPortionsRepository {
    /**
     * The portions of the meals [ids] read so far. A meal read but whose event says nothing of
     * them maps to null; a meal not read yet is absent.
     */
    fun observe(ids: Set<Long>): Flow<Map<Long, Portions?>>

    /** Reads the meals of [ids] not read yet, one after the other; stops at the first failure. */
    suspend fun fetch(ids: Set<Long>): Outcome<Unit>
}

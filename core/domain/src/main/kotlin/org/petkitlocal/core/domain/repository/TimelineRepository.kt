package org.petkitlocal.core.domain.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.TimelineItem

/**
 * The timeline, cached one local day at a time. Home reads today's cards; History's cursor
 * paging (M3) builds on the same cache.
 */
interface TimelineRepository {
    /** The cached cards of [day], newest first. */
    fun observeDay(day: LocalDate): Flow<List<TimelineItem>>

    /** Fetches every card of [day] (all pages) and replaces that day in the cache. */
    suspend fun refreshDay(day: LocalDate): Outcome<Unit>
}

package org.petkitlocal.core.domain.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryPage
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.TimelineItem

/**
 * The timeline, cached one local day at a time (ADR 0002). Home reads today's cards whole;
 * History pages through a day with the server's cursor, each page written into the same cache.
 */
interface TimelineRepository {
    /** The cached cards of [day], newest first. */
    fun observeDay(day: LocalDate): Flow<List<TimelineItem>>

    /** Fetches every card of [day] (all pages) and replaces that day in the cache. */
    suspend fun refreshDay(day: LocalDate): Outcome<Unit>

    /** The cached cards [query] selects, newest first: what History shows, online or not. */
    fun observeHistory(query: HistoryQuery): Flow<List<TimelineItem>>

    /**
     * Fetches the page of [query] after [cursor] (the first one without), and makes the cache
     * match the server over the range that page covers: its cards are written, and cached cards of
     * the same query in that range that the server no longer has are dropped.
     */
    suspend fun fetchHistoryPage(query: HistoryQuery, cursor: HistoryCursor?): Outcome<HistoryPage>

    /** One stored event with its decoded fields and media (`GET /timeline/{event_id}`). */
    suspend fun getEvent(id: Long): Outcome<EventDetail>
}

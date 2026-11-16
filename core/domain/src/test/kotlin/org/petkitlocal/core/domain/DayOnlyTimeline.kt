package org.petkitlocal.core.domain

import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.history.HistoryCursor
import org.petkitlocal.core.domain.history.HistoryPage
import org.petkitlocal.core.domain.history.HistoryQuery
import org.petkitlocal.core.domain.model.EventDetail
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.TimelineRepository

/** A stub of the timeline for tests that read whole days only: History's calls are not expected. */
abstract class DayOnlyTimeline : TimelineRepository {
    override fun observeHistory(query: HistoryQuery): Flow<List<TimelineItem>> = error("History is not read here")

    override suspend fun fetchHistoryPage(query: HistoryQuery, cursor: HistoryCursor?): Outcome<HistoryPage> =
        error("History is not read here")

    override suspend fun getEvent(id: Long): Outcome<EventDetail> = error("History is not read here")

    override fun observeRecentClips(deviceId: Long, limit: Int): Flow<List<TimelineItem>> =
        error("Recent clips are not read here")

    override suspend fun refreshRecentClips(deviceId: Long, count: Int): Outcome<Unit> =
        error("Recent clips are not read here")
}

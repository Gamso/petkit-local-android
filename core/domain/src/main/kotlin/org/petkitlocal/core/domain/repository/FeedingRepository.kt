package org.petkitlocal.core.domain.repository

import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.DeferredFeedRequest
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.FeedingPlan

/**
 * A feeder's plan and one-off feeds. The flows emit what was last read or written, null until
 * read once; a write changes them only with what the server answered (ADR 0002), never with what
 * was asked.
 */
interface FeedingRepository {
    fun observePlan(deviceId: Long): Flow<FeedingPlan?>

    /** Pending one-off feeds, soonest first. */
    fun observeDeferredFeeds(deviceId: Long): Flow<List<DeferredFeed>?>

    /** Reads the plan and the one-off feeds of [deviceId]; a part that fails keeps its last value. */
    suspend fun refresh(deviceId: Long): Outcome<Unit>

    /** Replaces every recurring meal; pending one-off feeds are kept. */
    suspend fun savePlan(deviceId: Long, groups: List<FeedingGroup>): Outcome<CommandDelivery>

    /** Pauses ([active] false) or resumes the plan; its meals stay stored either way. */
    suspend fun setPlanActive(deviceId: Long, active: Boolean): Outcome<CommandDelivery>

    /** A pause of the plan does not hold a one-off feed back. */
    suspend fun addDeferredFeed(deviceId: Long, feed: DeferredFeedRequest): Outcome<CommandDelivery>

    suspend fun cancelDeferredFeed(deviceId: Long, feedId: String): Outcome<CommandDelivery>
}

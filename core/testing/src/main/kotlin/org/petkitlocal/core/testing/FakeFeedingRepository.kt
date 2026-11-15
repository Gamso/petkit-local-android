package org.petkitlocal.core.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.DeferredFeedRequest
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.FeedingPlan
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.FeedingRepository

/**
 * A [FeedingRepository] whose "server" is [serverPlans] and [serverFeeds]: a refresh copies them
 * into the cache, a write that succeeds stores what it asked (as the server answers with it), and
 * [writeResult] set to a failure refuses every write, changing nothing. [gate] holds writes in
 * flight.
 */
class FakeFeedingRepository : FeedingRepository {
    val serverPlans = mutableMapOf<Long, FeedingPlan>()
    val serverFeeds = mutableMapOf<Long, List<DeferredFeed>>()
    private val plans = MutableStateFlow<Map<Long, FeedingPlan>>(emptyMap())
    private val feeds = MutableStateFlow<Map<Long, List<DeferredFeed>>>(emptyMap())

    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var writeResult: Outcome<CommandDelivery> = Outcome.Success(CommandDelivery.Mqtt)
    var gate: CompletableDeferred<Unit>? = null
    val refreshed = mutableListOf<Long>()
    val added = mutableListOf<DeferredFeedRequest>()
    val cancelled = mutableListOf<String>()
    val saved = mutableListOf<List<FeedingGroup>>()
    val activeSet = mutableListOf<Boolean>()

    override fun observePlan(deviceId: Long): Flow<FeedingPlan?> = plans.map { it[deviceId] }

    override fun observeDeferredFeeds(deviceId: Long): Flow<List<DeferredFeed>?> = feeds.map { it[deviceId] }

    override suspend fun refresh(deviceId: Long): Outcome<Unit> {
        refreshed += deviceId
        if (refreshResult is Outcome.Success) {
            serverPlans[deviceId]?.let { plan -> plans.value += deviceId to plan }
            serverFeeds[deviceId]?.let { list -> feeds.value += deviceId to list }
        }
        return refreshResult
    }

    override suspend fun savePlan(deviceId: Long, groups: List<FeedingGroup>): Outcome<CommandDelivery> {
        saved += groups
        return write { plans.value[deviceId]?.let { plans.value += deviceId to it.copy(groups = groups) } }
    }

    override suspend fun setPlanActive(deviceId: Long, active: Boolean): Outcome<CommandDelivery> {
        activeSet += active
        return write { plans.value[deviceId]?.let { plans.value += deviceId to it.copy(active = active) } }
    }

    override suspend fun addDeferredFeed(deviceId: Long, feed: DeferredFeedRequest): Outcome<CommandDelivery> {
        val new = DeferredFeed("d_${added.size}", null, feed.portions)
        added += feed
        return write { feeds.value += deviceId to (feeds.value[deviceId].orEmpty() + new) }
    }

    override suspend fun cancelDeferredFeed(deviceId: Long, feedId: String): Outcome<CommandDelivery> {
        cancelled += feedId
        return write { feeds.value += deviceId to feeds.value[deviceId].orEmpty().filter { it.id != feedId } }
    }

    private suspend fun write(apply: () -> Unit): Outcome<CommandDelivery> {
        gate?.await()
        if (writeResult is Outcome.Success) apply()
        return writeResult
    }
}

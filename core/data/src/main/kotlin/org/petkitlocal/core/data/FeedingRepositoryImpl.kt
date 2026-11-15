package org.petkitlocal.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.auth.SessionScopedCache
import org.petkitlocal.core.domain.map
import org.petkitlocal.core.domain.model.DeferredFeed
import org.petkitlocal.core.domain.model.DeferredFeedRequest
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.FeedingPlan
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.FeedingRepository
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.dto.FeedingPlanWriteDto
import org.petkitlocal.core.network.error.apiCall

/**
 * Feeding plans and one-off feeds, kept in memory per feeder for the session. They are not in
 * Room: no live message carries them (a device page re-reads them when the feeder's
 * `feeding_schedule` entity changes), and only a feeder's own page shows them. A write changes
 * them only with what the server answered: the plan it returns, the feed it created, the feed it
 * confirmed gone.
 */
class FeedingRepositoryImpl @Inject constructor(private val api: PetkitApi, private val store: FeedingStore) :
    FeedingRepository {
    private val plans get() = store.plans
    private val feeds get() = store.feeds

    override fun observePlan(deviceId: Long): Flow<FeedingPlan?> = plans.map { it[deviceId] }.distinctUntilChanged()

    override fun observeDeferredFeeds(deviceId: Long): Flow<List<DeferredFeed>?> =
        feeds.map { it[deviceId] }.distinctUntilChanged()

    override suspend fun refresh(deviceId: Long): Outcome<Unit> = coroutineScope {
        val plan = async { apiCall { api.getFeedingPlan(deviceId) } }
        val pending = async { apiCall { api.listDeferredFeeds(deviceId) } }
        val planResult = plan.await()
        if (planResult is Outcome.Success) {
            val read = planResult.value.schedule.toDomain()
            plans.update { it + (deviceId to read) }
        }
        val pendingResult = pending.await()
        // A feed's amounts are read the way the plan says the feeder counts them.
        val dual = plans.value[deviceId]?.dualHopper
        if (pendingResult is Outcome.Success && dual != null) {
            val list = pendingResult.value.deferredFeeds.map { it.toDomain(dual) }
            feeds.update { it + (deviceId to list) }
        }
        listOf(planResult, pendingResult).filterIsInstance<Outcome.Failure>().firstOrNull() ?: Outcome.Success(Unit)
    }

    override suspend fun savePlan(deviceId: Long, groups: List<FeedingGroup>): Outcome<CommandDelivery> {
        val dual = plans.value[deviceId]?.dualHopper
            ?: return refreshFirst(deviceId) { savePlan(deviceId, groups) }
        return writePlan(deviceId, FeedingPlanWriteDto(groups = groups.map { it.toDto(dual) }))
    }

    override suspend fun setPlanActive(deviceId: Long, active: Boolean): Outcome<CommandDelivery> =
        writePlan(deviceId, FeedingPlanWriteDto(active = active))

    override suspend fun addDeferredFeed(deviceId: Long, feed: DeferredFeedRequest): Outcome<CommandDelivery> =
        apiCall { api.addDeferredFeed(deviceId, feed.toDto()) }.map { result ->
            val dual = plans.value[deviceId]?.dualHopper ?: (result.feed.amount == null)
            val added = result.feed.toDomain(dual)
            feeds.update { all ->
                val list = (all[deviceId].orEmpty().filter { it.id != added.id } + added).sortedBy { it.fireAt }
                all + (deviceId to list)
            }
            deliveryOf(result.delivered)
        }

    override suspend fun cancelDeferredFeed(deviceId: Long, feedId: String): Outcome<CommandDelivery> =
        apiCall { api.deleteDeferredFeed(deviceId, feedId) }.map { result ->
            feeds.update { all ->
                val list = all[deviceId] ?: return@update all
                all + (deviceId to list.filter { it.id != feedId })
            }
            deliveryOf(result.delivered)
        }

    private suspend fun writePlan(deviceId: Long, body: FeedingPlanWriteDto): Outcome<CommandDelivery> =
        apiCall { api.setFeedingPlan(deviceId, body) }.map { result ->
            plans.update { it + (deviceId to result.schedule.toDomain()) }
            deliveryOf(result.delivered)
        }

    /** Reads the plan, which says how the feeder counts portions, then runs [write]. */
    private suspend fun refreshFirst(
        deviceId: Long,
        write: suspend () -> Outcome<CommandDelivery>,
    ): Outcome<CommandDelivery> {
        val read = apiCall { api.getFeedingPlan(deviceId) }
        return when (read) {
            is Outcome.Failure -> read

            is Outcome.Success -> {
                plans.update { it + (deviceId to read.value.schedule.toDomain()) }
                write()
            }
        }
    }
}

/**
 * The feeders' plans and one-off feeds for the session, cleared when it ends. Apart from the
 * repository so that clearing it needs nothing of the network, which itself needs the session.
 */
@Singleton
class FeedingStore @Inject constructor() : SessionScopedCache {
    val plans = MutableStateFlow<Map<Long, FeedingPlan>>(emptyMap())
    val feeds = MutableStateFlow<Map<Long, List<DeferredFeed>>>(emptyMap())

    override suspend fun clear() {
        plans.value = emptyMap()
        feeds.value = emptyMap()
    }
}

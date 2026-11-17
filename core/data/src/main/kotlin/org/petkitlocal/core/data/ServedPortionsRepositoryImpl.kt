package org.petkitlocal.core.data

import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.database.dao.TimelineDao
import org.petkitlocal.core.database.model.ServedPortionsRow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.repository.ServedPortionsRepository
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.dto.EventFieldDto
import org.petkitlocal.core.network.error.apiCall

/**
 * A meal's portions, read from its event's decoded fields (`GET /timeline/{event_id}`) and kept
 * in the cache (ADR 0002). The fields are the device's own report, which petkit-local passes
 * through under their device names: `real_amount1` and `real_amount2` on a Dual-Hopper,
 * `real_amount` on a single hopper (`events/codes.py`, feeder code 4 and `feed_over`).
 */
class ServedPortionsRepositoryImpl @Inject constructor(private val api: PetkitApi, private val dao: TimelineDao) :
    ServedPortionsRepository {
    override fun observe(ids: Set<Long>): Flow<Map<Long, Portions?>> {
        if (ids.isEmpty()) return flowOf(emptyMap())
        return dao.observeServedPortions(ids.toList()).map { rows -> rows.associate { it.id to it.toDomain() } }
    }

    override suspend fun fetch(ids: Set<Long>): Outcome<Unit> {
        if (ids.isEmpty()) return Outcome.Success(Unit)
        val known = dao.servedPortionIds(ids.toList()).toSet()
        for (id in ids - known) {
            val row = when (val result = apiCall { api.getTimelineEvent(id) }) {
                is Outcome.Success -> servedPortions(id, result.value.event.fields)

                // Gone from the server: nothing to read, now or later.
                is Outcome.Failure -> if (result.error is DomainError.NotFound) {
                    ServedPortionsRow(id, null, null, null)
                } else {
                    return result
                }
            }
            dao.upsertServedPortions(row)
        }
        return Outcome.Success(Unit)
    }
}

/** The portions an event's [fields] say were put out; a negative count (a skipped run) is none. */
internal fun servedPortions(id: Long, fields: List<EventFieldDto>): ServedPortionsRow {
    fun count(key: String): Int? = fields.firstOrNull { it.key == key }
        ?.text?.trim()?.toDoubleOrNull()
        ?.takeIf { it.isFinite() }
        ?.roundToInt()?.coerceAtLeast(0)
    return ServedPortionsRow(id, count("real_amount"), count("real_amount1"), count("real_amount2"))
}

internal fun ServedPortionsRow.toDomain(): Portions? = when {
    amount1 != null || amount2 != null -> Portions.Dual(amount1 ?: 0, amount2 ?: 0)
    else -> amount?.let(Portions::Single)
}

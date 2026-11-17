package org.petkitlocal.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.repository.ServedPortionsRepository

/**
 * A [ServedPortionsRepository] whose "server" is [server]: a fetch copies the meals it knows into
 * the cache ([cached]), a meal it does not know as one whose event says nothing. [result] set to
 * a failure reads nothing.
 */
class FakeServedPortionsRepository(val server: Map<Long, Portions> = emptyMap()) : ServedPortionsRepository {
    val cached = MutableStateFlow<Map<Long, Portions?>>(emptyMap())
    var result: Outcome<Unit> = Outcome.Success(Unit)

    /** Every set of meals a fetch was asked for, in order. */
    val fetched = mutableListOf<Set<Long>>()

    override fun observe(ids: Set<Long>): Flow<Map<Long, Portions?>> = cached.map { it.filterKeys(ids::contains) }

    override suspend fun fetch(ids: Set<Long>): Outcome<Unit> {
        fetched += ids
        if (result is Outcome.Success) cached.value += (ids - cached.value.keys).associateWith { server[it] }
        return result
    }
}

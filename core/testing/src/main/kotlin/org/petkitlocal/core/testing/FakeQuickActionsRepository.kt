package org.petkitlocal.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.petkitlocal.core.domain.repository.QuickActionsRepository

/** The phone's quick actions in memory: [stored] by device id; [failing] makes every write fail. */
class FakeQuickActionsRepository(initial: Map<Long, List<String>> = emptyMap()) : QuickActionsRepository {
    val stored = MutableStateFlow(initial)
    var failing = false

    override fun observe(deviceId: Long): Flow<List<String>?> = stored.map { it[deviceId] }

    override suspend fun update(deviceId: Long, transform: (List<String>?) -> List<String>?): Boolean {
        if (failing) return false
        val next = transform(stored.value[deviceId])
        stored.value = if (next == null) stored.value - deviceId else stored.value + (deviceId to next)
        return true
    }
}

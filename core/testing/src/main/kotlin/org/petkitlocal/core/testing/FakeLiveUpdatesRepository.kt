package org.petkitlocal.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import org.petkitlocal.core.domain.repository.LiveUpdatesRepository
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus

/**
 * A [LiveUpdatesRepository] whose status a test sets, and which counts its collectors: the real
 * one keeps its socket open exactly while that count is above zero.
 */
class FakeLiveUpdatesRepository(initial: LiveUpdatesStatus = LiveUpdatesStatus.Live) : LiveUpdatesRepository {
    val current = MutableStateFlow(initial)

    /** How many collect [status] right now. */
    val collectors = MutableStateFlow(0)

    override val status: Flow<LiveUpdatesStatus> = current
        .onStart { collectors.value += 1 }
        .onCompletion { collectors.value -= 1 }
}

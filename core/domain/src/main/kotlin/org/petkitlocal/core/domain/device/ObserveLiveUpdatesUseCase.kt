package org.petkitlocal.core.domain.device

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.repository.LiveUpdatesRepository
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus

/**
 * Keeps the cache live while collected (the socket is open for as long as someone collects), and
 * says whether it is.
 */
class ObserveLiveUpdatesUseCase @Inject constructor(private val repository: LiveUpdatesRepository) {
    operator fun invoke(): Flow<LiveUpdatesStatus> = repository.status
}

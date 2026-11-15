package org.petkitlocal.core.domain.repository

import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.DomainError

/**
 * The `/ws` live-updates socket (SPEC.md §6, ARCHITECTURE.md §4). It writes what it receives
 * into the cache the other repositories read (`hello` replaces the devices; `state`, `device`,
 * `device_removed` and `timeline` patch them), so screens never read it for data, only for
 * whether what they show is live.
 */
interface LiveUpdatesRepository {
    /**
     * The connection's state. Collecting it is what keeps the one socket of the process open: it
     * opens with the first collector, while signed in, and closes five seconds after the last one
     * leaves, which is how it closes when the app goes to the background. It reconnects with
     * backoff, except when the server ends the session (close code 4401), which signs out.
     */
    val status: Flow<LiveUpdatesStatus>
}

sealed interface LiveUpdatesStatus {
    /** Signed out, or the session just ended: no socket. */
    data object Stopped : LiveUpdatesStatus

    /** Opening the socket, before the server's `hello`. */
    data object Connecting : LiveUpdatesStatus

    /** The cache follows the server. */
    data object Live : LiveUpdatesStatus

    /** The socket closed or could not open, because of [error]; the next attempt is in [retryIn]. */
    data class Waiting(val error: DomainError, val retryIn: Duration) : LiveUpdatesStatus
}

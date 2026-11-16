package org.petkitlocal.core.network

import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.network.dto.UpdateMessageDto

/*
 * The routes that are not REST: the two WebSockets and the live HLS files. Their message DTOs
 * are in dto/UpdateDtos.kt, covered by the contract tests; each client runs on the shared OkHttp
 * client of PetkitHttpClient ([LiveUpdatesClient] for `/ws`, video/ for live video).
 */

/** What the `/ws` live-updates socket yields while it is collected. */
sealed interface LiveUpdate {
    data class Message(val message: UpdateMessageDto) : LiveUpdate

    /** The socket closed; [error] is null for a close the app asked for. */
    data class Closed(val error: DomainError?) : LiveUpdate
}

/**
 * `GET /ws`: live updates (ARCHITECTURE.md §4), one socket per collection ([LiveUpdatesClient]).
 * Sharing it, closing it in the background and reconnecting are the data layer's.
 */
interface LiveUpdatesSocket {
    fun updates(): Flow<LiveUpdate>
}

/** WebSocket close codes of both sockets (api/openapi.yaml, `/ws` and `/devices/{id}/live`). */
object CloseCodes {
    const val NORMAL = 1000
    const val GOING_AWAY = 1001
    const val STREAM_ENDED = 1011
    const val TOO_SLOW = 1013
    const val SESSION_ENDED = 4401
    const val REPLACED = 4409
}

/**
 * The domain meaning of a `/ws` close: null when nothing went wrong (the app closed it),
 * [DomainError.SessionEnded] on 4401, and a network failure to reconnect after for the rest. The
 * live-video socket reads its codes its own way (video/LiveSocketDataSource.kt).
 */
fun closeCodeToDomainError(code: Int): DomainError? = when (code) {
    CloseCodes.NORMAL, CloseCodes.REPLACED -> null
    CloseCodes.SESSION_ENDED -> DomainError.SessionEnded(SessionEndReason.ClosedByServer)
    else -> DomainError.Network("WebSocket closed with code $code")
}

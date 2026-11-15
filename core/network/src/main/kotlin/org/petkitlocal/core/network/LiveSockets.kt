package org.petkitlocal.core.network

import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import okio.ByteString
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.network.dto.UpdateMessageDto

/*
 * The routes that are not REST: the two WebSockets and the live HLS files. Their message DTOs
 * exist now (dto/UpdateDtos.kt) so the contract tests cover them; the clients arrive with the
 * milestones that use them, on the shared OkHttp client of PetkitHttpClient.
 */

/** What the `/ws` live-updates socket yields while it is collected. */
sealed interface LiveUpdate {
    data class Message(val message: UpdateMessageDto) : LiveUpdate

    /** The socket closed; [error] is null for a close the app asked for. */
    data class Closed(val error: DomainError?) : LiveUpdate
}

/**
 * `GET /ws`: live updates (ARCHITECTURE.md §4).
 *
 * TODO(M2): implement on the shared OkHttp client: open on first collector, decode each frame
 *  with [decodeUpdateMessage], close `WhileSubscribed(5s)`, reconnect with exponential backoff
 *  and jitter capped at 30 s, except after close code 4401 ([closeCodeToDomainError]).
 */
interface LiveUpdatesSocket {
    fun updates(): Flow<LiveUpdate>
}

/** What the live-video socket yields: the MIME type once, then fMP4 fragments in order. */
sealed interface LiveVideoFrame {
    data class MimeType(val value: String) : LiveVideoFrame

    /** The init segment first (`ftyp`+`moov`), then media fragments (`moof`+`mdat`). */
    data class Fragment(val bytes: ByteString) : LiveVideoFrame

    /** The camera stream could not be opened; close and retry after a few seconds. */
    data class StreamError(val message: String) : LiveVideoFrame

    data class Closed(val code: Int, val error: DomainError?) : LiveVideoFrame
}

/**
 * `GET /devices/{id}/live`: fMP4 over a WebSocket (ADR 0005).
 *
 * TODO(M4): send one `LiveVideoMessageDto.Mse` with the decoder's codecs, then feed fragments to
 *  a pipe-backed Media3 `DataSource`; fall back to [LiveHlsSource] on close code 1013 or after
 *  two failed starts; stay silent on 4409.
 */
interface LiveVideoSocket {
    fun open(deviceId: Long, codecs: String): Flow<LiveVideoFrame>
}

/**
 * `GET /devices/{id}/live/hls/{file}`: the HLS fallback, fetched by Media3 with the bearer header
 * of the shared client, not by Retrofit.
 *
 * TODO(M4): resolve against the current server and hand to `DefaultHttpDataSource.Factory`.
 */
interface LiveHlsSource {
    /** `.../devices/{id}/live/hls/playlist.m3u8`, without `id`: it opens an HLS session. */
    fun playlistUrl(deviceId: Long): HttpUrl?
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
 * The domain meaning of a close: null when nothing went wrong (the app closed it, or the same
 * session opened the camera again), [DomainError.SessionEnded] on 4401, and a network failure
 * to recover from (reconnect, or fall back to HLS) for the rest.
 */
fun closeCodeToDomainError(code: Int): DomainError? = when (code) {
    CloseCodes.NORMAL, CloseCodes.REPLACED -> null
    CloseCodes.SESSION_ENDED -> DomainError.SessionEnded(SessionEndReason.ClosedByServer)
    else -> DomainError.Network("WebSocket closed with code $code")
}

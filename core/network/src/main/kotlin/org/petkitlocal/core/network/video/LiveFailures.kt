package org.petkitlocal.core.network.video

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.network.error.HttpErrorMapper

/** How the live data source stops a stream: thrown through Media3, read back by [liveFailureOf]. */
class LiveStreamException(val failure: LiveFailure) : IOException(failure.toString())

/**
 * What stopped a live player, from the error Media3 reported: the [LiveFailure] the live socket
 * threw, an HLS file the server refused (read like any refused request: 404, 429, 503...), a
 * stream the phone could not parse or decode, and otherwise the network.
 */
@OptIn(UnstableApi::class) // InvalidResponseCodeException's fields.
fun liveFailureOf(error: Throwable): LiveFailure {
    for (cause in generateSequence(error) { it.cause }) {
        when (cause) {
            is LiveStreamException -> return cause.failure
            is HttpDataSource.InvalidResponseCodeException -> return hlsRefusal(cause)
        }
    }
    return if (error is PlaybackException && error.errorCode / CODE_GROUP in UNPLAYABLE_GROUPS) {
        LiveFailure.Playback(error.errorCodeName)
    } else {
        LiveFailure.Network(error.message)
    }
}

@OptIn(UnstableApi::class)
private fun hlsRefusal(e: HttpDataSource.InvalidResponseCodeException): LiveFailure {
    val error = HttpErrorMapper.map(
        status = e.responseCode,
        body = e.responseBody.decodeToString(),
        retryAfter = e.headerFields.entries.firstOrNull {
            it.key.equals(RETRY_AFTER, ignoreCase = true)
        }?.value?.firstOrNull(),
        hadSession = true,
    )
    return when {
        error is DomainError.SessionEnded -> LiveFailure.SessionEnded

        // The HLS view lapsed or is not this session's: a new one, from the master playlist.
        error is DomainError.NotFound && error.code in HLS_VIEW_GONE -> LiveFailure.Network(error.code)

        else -> LiveFailure.Refused(error)
    }
}

private const val RETRY_AFTER = "Retry-After"
private val HLS_VIEW_GONE = setOf("hls_session_expired", "hls_session_not_found")

/** [PlaybackException] codes come in groups of a thousand: 3xxx parsing, 4xxx decoding. */
private const val CODE_GROUP = 1000
private val UNPLAYABLE_GROUPS = setOf(3, 4)

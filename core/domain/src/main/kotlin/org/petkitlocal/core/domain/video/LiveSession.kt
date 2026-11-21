package org.petkitlocal.core.domain.video

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.petkitlocal.core.domain.DomainError

/** How a camera's live stream reaches the player (ADR 0005). */
enum class LiveTransport {
    /** fMP4 over the `/devices/{id}/live` WebSocket: a fraction of a second behind the camera. */
    WebSocket,

    /** HLS from `/devices/{id}/live/hls/`, plain HTTP: a few seconds behind. */
    Hls,
}

/** Why a live stream did not start, or stopped, as far as what to do next differs. */
sealed interface LiveFailure {
    /** Close code 1011 (the camera or go2rtc went away) or 1001 (the server is stopping). */
    data object StreamEnded : LiveFailure

    /** Close code 1013: the phone did not keep up with the stream. */
    data object TooSlow : LiveFailure

    /** Close code 4409: this session opened the camera again, which replaced this view. */
    data object Replaced : LiveFailure

    /** Close code 4401, or a 401: the session is over. */
    data object SessionEnded : LiveFailure

    /** An `error` message: the camera did not take the viewer (it needs a few seconds). */
    data class StreamError(val message: String) : LiveFailure

    /** Refused before it opened: no stream (404), too many viewers (429), unavailable (503)... */
    data class Refused(val error: DomainError) : LiveFailure

    /** The connection could not be made, broke, timed out or closed with another code. */
    data class Network(val detail: String? = null) : LiveFailure

    /** What arrived could not be played: no decoder for it, or not the format announced. */
    data class Playback(val detail: String? = null) : LiveFailure
}

/** Where a live session is. */
sealed interface LivePhase {
    /** Not playing and not trying to: the screen is in the background, or not shown yet. */
    data object Idle : LivePhase

    /** A player is opening the stream. */
    data object Starting : LivePhase

    /** Frames are being shown. */
    data object Playing : LivePhase

    /** [failure] stopped it; it starts again by itself after [retryIn]. */
    data class Waiting(val failure: LiveFailure, val retryIn: Duration) : LivePhase

    /**
     * [failure] stopped it and only the user starts it again: the stream ended, another view
     * replaced this one, the camera has no stream or no room, or starting failed too often.
     */
    data class Stopped(val failure: LiveFailure) : LivePhase

    /** The session is over: the app returns to sign-in. */
    data object SessionEnded : LivePhase
}

/**
 * The live video of one camera as a state machine (ARCHITECTURE.md §4, ADR 0005), with no clock
 * and no I/O: each event returns the next state, and whoever holds it starts players, counts
 * down [LivePhase.Waiting] and reports back.
 *
 * - It starts on the WebSocket. Close code 1013 moves it to HLS at once; so do
 *   [WEBSOCKET_FAILED_STARTS] failed starts in a row, after the usual wait.
 * - An `error` message, a network failure or an unplayable stream is retried after
 *   [RETRY_DELAY]: a camera takes a new viewer only some seconds after the last one left.
 *   [HLS_FAILED_STARTS] failed starts in a row on HLS stop it until the user retries.
 * - The stream ending (1011, 1001), a replaced view (4409, silent), no stream (404) and no room
 *   (429) stop it until the user retries; 4401 ends the session.
 *
 * Every start is a new [attempt]: a new player and a new socket. Events name the attempt they are
 * about, so a late failure from a player already replaced changes nothing.
 *
 * A start that takes longer than [SLOW_START] is [startingSlowly]: a camera reached through a
 * relay (a D4SH) can take up to ~30 s to open its first stream (petkit-local#122/#125), which is
 * progress to show, not an error.
 */
data class LiveSession(
    val transport: LiveTransport = LiveTransport.WebSocket,
    val phase: LivePhase = LivePhase.Idle,
    /** Starts that failed in a row on [transport], since it last played or switched. */
    val failedStarts: Int = 0,
    val attempt: Int = 0,
    /** This attempt has been starting for longer than [SLOW_START] ([slowStart]). */
    val startingSlowly: Boolean = false,
) {
    /** The screen is visible: start, on the transport it had, unless the user must decide. */
    fun start(): LiveSession = when (phase) {
        LivePhase.Idle, is LivePhase.Waiting -> starting()
        LivePhase.Starting, LivePhase.Playing, is LivePhase.Stopped, LivePhase.SessionEnded -> this
    }

    /** The user's retry: from scratch, on the WebSocket again. */
    fun retry(): LiveSession = if (phase == LivePhase.SessionEnded) {
        this
    } else {
        copy(transport = LiveTransport.WebSocket, failedStarts = 0).starting()
    }

    /** The screen left or went to the background: no player, no socket. */
    fun stop(): LiveSession = when (phase) {
        LivePhase.Starting, LivePhase.Playing, is LivePhase.Waiting -> copy(
            phase = LivePhase.Idle,
            startingSlowly = false,
        )

        LivePhase.Idle, is LivePhase.Stopped, LivePhase.SessionEnded -> this
    }

    /**
     * The viewer slot is handed to another screen (a camera page's live card opening the live
     * screen): no player, no socket, and the next [start] is a fresh one on the WebSocket, even
     * after a failure only the user could retry, since the other screen started anew meanwhile.
     */
    fun release(): LiveSession = if (phase == LivePhase.SessionEnded) {
        this
    } else {
        copy(transport = LiveTransport.WebSocket, phase = LivePhase.Idle, failedStarts = 0, startingSlowly = false)
    }

    /** [LivePhase.Waiting] is over. */
    fun retryDue(): LiveSession = if (phase is LivePhase.Waiting) starting() else this

    /** [SLOW_START] went by and [attempt] is still starting. */
    fun slowStart(attempt: Int): LiveSession = if (attempt == this.attempt && phase == LivePhase.Starting) {
        copy(startingSlowly = true)
    } else {
        this
    }

    /** The player of [attempt] shows frames. */
    fun playing(attempt: Int): LiveSession = if (attempt == this.attempt && phase == LivePhase.Starting) {
        copy(phase = LivePhase.Playing, failedStarts = 0, startingSlowly = false)
    } else {
        this
    }

    /** The player of [attempt] failed with [failure]. */
    fun fail(attempt: Int, failure: LiveFailure): LiveSession {
        if (attempt != this.attempt || (phase != LivePhase.Starting && phase != LivePhase.Playing)) return this
        return copy(startingSlowly = false).failedWith(failure)
    }

    private fun failedWith(failure: LiveFailure): LiveSession = when (failure) {
        LiveFailure.SessionEnded -> copy(phase = LivePhase.SessionEnded)

        LiveFailure.StreamEnded, LiveFailure.Replaced -> copy(phase = LivePhase.Stopped(failure))

        LiveFailure.TooSlow -> if (transport == LiveTransport.WebSocket) {
            copy(transport = LiveTransport.Hls, failedStarts = 0).starting()
        } else {
            failedToStart(failure)
        }

        is LiveFailure.Refused -> when (failure.error) {
            is DomainError.SessionEnded -> copy(phase = LivePhase.SessionEnded)
            is DomainError.NotFound, is DomainError.RateLimited -> copy(phase = LivePhase.Stopped(failure))
            else -> failedToStart(failure)
        }

        is LiveFailure.StreamError, is LiveFailure.Network, is LiveFailure.Playback -> failedToStart(failure)
    }

    /**
     * A failure worth retrying: counted when it happened while starting (one that cut a playing
     * stream is a new start), then a wait, on HLS once the WebSocket failed too often.
     */
    private fun failedToStart(failure: LiveFailure): LiveSession {
        val failed = if (phase == LivePhase.Starting) failedStarts + 1 else 0
        return when {
            transport == LiveTransport.WebSocket && failed >= WEBSOCKET_FAILED_STARTS ->
                copy(transport = LiveTransport.Hls, failedStarts = 0, phase = LivePhase.Waiting(failure, RETRY_DELAY))

            transport == LiveTransport.Hls && failed >= HLS_FAILED_STARTS ->
                copy(failedStarts = failed, phase = LivePhase.Stopped(failure))

            else -> copy(failedStarts = failed, phase = LivePhase.Waiting(failure, RETRY_DELAY))
        }
    }

    private fun starting(): LiveSession =
        copy(phase = LivePhase.Starting, attempt = attempt + 1, startingSlowly = false)

    companion object {
        /** Failed starts in a row on the WebSocket before HLS is tried (ADR 0005). */
        const val WEBSOCKET_FAILED_STARTS = 2

        /** Failed starts in a row on HLS before it is left to the user. */
        const val HLS_FAILED_STARTS = 3

        /** "Retry after a few seconds" (api/openapi.yaml, `/devices/{id}/live`). */
        val RETRY_DELAY: Duration = 3.seconds

        /** How long a start takes before the screen says the camera is taking its time. */
        val SLOW_START: Duration = 8.seconds
    }
}

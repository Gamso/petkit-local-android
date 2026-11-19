package org.petkitlocal.core.domain.talk

import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.DomainError

/**
 * Talking through a camera's speaker (`/devices/{id}/talk`): the phone's microphone, sent to the
 * camera while the user holds Talk. Half-duplex: the camera's own sound is muted meanwhile, as it
 * has no echo cancellation on this path.
 */
interface TalkRepository {
    /** A talk to [deviceId]: nothing happens until its [TalkSession.events] are collected. */
    fun open(deviceId: Long): TalkSession
}

/** One talk, collected once. */
interface TalkSession {
    /**
     * Collecting it opens the socket; once the camera accepted, the microphone is recorded and
     * sent, and [TalkEvent.Talking] is emitted. [TalkEvent.Ended] is always the last event: the
     * talk is over, the microphone released and the socket closed. Cancelling the collection
     * ends the talk at once (the server still plays what it received).
     */
    val events: Flow<TalkEvent>

    /**
     * Ends the talk cleanly: the microphone stops, the server plays what it received and confirms,
     * then [TalkEvent.Ended] with no failure. Before the camera accepted, it just closes.
     */
    fun stop()
}

sealed interface TalkEvent {
    /** The camera accepted: what the microphone hears now reaches its speaker. */
    data object Talking : TalkEvent

    /** The talk is over: [failure] says why, or is null when it was stopped. */
    data class Ended(val failure: TalkFailure?) : TalkEvent
}

/** Why a talk did not start, or stopped by itself. */
sealed interface TalkFailure {
    /** The device has no speaker to talk through (`not_a_camera`). */
    data object NotACamera : TalkFailure

    /** The Two-Way Talk patcher is not applied to the camera (`needs_patcher`). */
    data object NeedsPatcher : TalkFailure

    /** The camera is off, unreachable, or hung up (`device_unreachable`). */
    data object DeviceUnreachable : TalkFailure

    /** Someone else, another phone or the panel, is talking through it (`busy`). */
    data object Busy : TalkFailure

    /** The server's audio converter is missing or stopped (`transcoder_failed`); [message] is its reason. */
    data class TranscoderFailed(val message: String) : TalkFailure

    /** No audio reached the server for 15 seconds (`idle_timeout`). */
    data object IdleTimeout : TalkFailure

    /** An `error` code this app does not know yet, with the server's own [message]. */
    data class Other(val code: String, val message: String) : TalkFailure

    /** The server is stopping (close code 1001). */
    data object ServerStopping : TalkFailure

    /** The phone's session ended (close code 4401): the app goes back to sign-in. */
    data object SessionEnded : TalkFailure

    /** The server refused to open the socket at all, as it refuses a request ([error]). */
    data class Refused(val error: DomainError) : TalkFailure

    /** The connection failed or closed without saying why. */
    data class Network(val message: String?) : TalkFailure

    /** The phone's microphone could not be opened: another app holds it, or it is missing. */
    data object MicrophoneUnavailable : TalkFailure
}

/** Whether the app may record the microphone (Android's `RECORD_AUDIO`). */
fun interface MicrophoneAccess {
    fun granted(): Boolean
}

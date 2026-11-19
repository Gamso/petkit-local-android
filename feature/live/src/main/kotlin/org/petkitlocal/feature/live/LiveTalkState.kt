package org.petkitlocal.feature.live

import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.talk.TalkFailure

/** Where the talk through the camera is: Talk is held to speak, walkie-talkie style. */
enum class TalkPhase {
    Idle,

    /** Talk is held and the socket is opening; the microphone is not recorded yet. */
    Connecting,

    /** The camera accepted: the microphone reaches its speaker. */
    Talking,

    /** Released: the camera plays what it received and the server confirms. */
    Stopping,
}

/** What is said under Talk, where it happened. */
sealed interface TalkNotice {
    /** Talk was pressed on a camera the server says cannot talk now, and why. */
    data class Unavailable(val reason: TalkAvailability) : TalkNotice

    /** Android is asked for the microphone: why the app wants it. */
    data object MicrophoneReason : TalkNotice

    /** The microphone was just allowed: hold Talk again to speak. */
    data object MicrophoneAllowed : TalkNotice

    /** The microphone is refused: how to allow it in Android's settings. */
    data object MicrophoneDenied : TalkNotice

    /** Talk was let go before the camera accepted: it has to be held. */
    data object HoldToTalk : TalkNotice

    /** The sound button was pressed while talking: the camera's sound stays off until the talk ends. */
    data object SoundOffWhileTalking : TalkNotice

    /** The talk did not start, or stopped by itself. */
    data class Failed(val failure: TalkFailure) : TalkNotice
}

/** Talk on the live screen: what the server says about the camera, what is happening, what to say. */
data class TalkUiState(
    val availability: TalkAvailability = TalkAvailability.Unsupported,
    val phase: TalkPhase = TalkPhase.Idle,
    val notice: TalkNotice? = null,
) {
    /** From the press until the server confirmed the end: the camera's own sound is off meanwhile. */
    val busy: Boolean get() = phase != TalkPhase.Idle
}

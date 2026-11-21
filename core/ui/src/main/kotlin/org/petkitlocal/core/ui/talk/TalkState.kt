package org.petkitlocal.core.ui.talk

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

/** Talk through a camera: what the server says about the camera, what is happening, what to say. */
data class TalkUiState(
    val availability: TalkAvailability = TalkAvailability.Unsupported,
    val phase: TalkPhase = TalkPhase.Idle,
    val notice: TalkNotice? = null,
) {
    /** From the press until the server confirmed the end: the camera's own sound is off meanwhile. */
    val busy: Boolean get() = phase != TalkPhase.Idle
}

/** What Talk's user can do; the screen wires each to its ViewModel's [TalkController]. */
data class TalkCallbacks(
    /** The finger went down on Talk. */
    val onTalkPressed: () -> Unit = {},
    /** The finger went up, or slid off. */
    val onTalkReleased: () -> Unit = {},
    /** Talk activated without a press (TalkBack's double tap): starts or stops talking. */
    val onTalkToggled: () -> Unit = {},
    /** This app's page in Android's settings, where the microphone can be allowed. */
    val onOpenAppSettings: () -> Unit = {},
)

/** Test tags of Talk, wherever it is. */
object TalkTestTags {
    /** Talk, held to speak through the camera. */
    const val TALK = "live_talk"

    /** Under Talk while talking: the red dot and what is happening. */
    const val INDICATOR = "live_talk_indicator"

    /** Under Talk: what was said last (why it cannot talk, the microphone, an error). */
    const val NOTICE = "live_talk_notice"

    /** Under Talk: why the server says the camera cannot talk now. */
    const val REASON = "live_talk_reason"
}

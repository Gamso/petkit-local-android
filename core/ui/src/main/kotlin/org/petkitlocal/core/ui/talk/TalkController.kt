package org.petkitlocal.core.ui.talk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.talk.MicrophoneAccess
import org.petkitlocal.core.domain.talk.OpenTalkUseCase
import org.petkitlocal.core.domain.talk.TalkEvent
import org.petkitlocal.core.domain.talk.TalkSession

/**
 * Talking through [deviceId]'s camera, for whichever screen shows Talk (the live screen, a camera
 * page's live card): what the press, the release and TalkBack's toggle do, the microphone asked
 * for once, and the talk ended when the screen leaves ([end]). [state] holds what is happening and
 * what to say; the screen adds the server's [TalkAvailability], which it reads from the device.
 */
class TalkController(
    private val scope: CoroutineScope,
    private val deviceId: Long,
    private val openTalk: OpenTalkUseCase,
    private val microphone: MicrophoneAccess,
) {
    private val local = MutableStateFlow(TalkUiState())
    private var job: Job? = null
    private var session: TalkSession? = null
    private val microphoneRequests = Channel<Unit>(Channel.BUFFERED)

    /** The talk's phase and notice; its availability is the screen's to fill in. */
    val state: StateFlow<TalkUiState> = local.asStateFlow()

    /** Each time Android must be asked for the microphone; answer with [onMicrophoneResult]. */
    val requestMicrophone: Flow<Unit> = microphoneRequests.receiveAsFlow()

    /** From the press until the server confirmed the end: the camera's own sound is off meanwhile. */
    val busy: Boolean get() = local.value.busy

    /**
     * Talk is pressed: the talk opens, and the camera's speaker plays the microphone once it
     * accepted, until [onReleased]. When the camera cannot talk ([availability]), or the
     * microphone is not allowed yet, the screen says so instead (and Android is asked).
     */
    fun onPressed(availability: TalkAvailability) {
        if (local.value.phase != TalkPhase.Idle) return
        when {
            availability != TalkAvailability.Available ->
                update { it.copy(notice = TalkNotice.Unavailable(availability)) }

            !microphone.granted() -> {
                update { it.copy(notice = TalkNotice.MicrophoneReason) }
                microphoneRequests.trySend(Unit)
            }

            else -> start()
        }
    }

    /** Talk is let go, or the press was cancelled (the finger slid off): the talk ends. */
    fun onReleased() {
        when (local.value.phase) {
            TalkPhase.Connecting -> cancel(TalkNotice.HoldToTalk)
            TalkPhase.Talking -> stop()
            TalkPhase.Idle, TalkPhase.Stopping -> Unit
        }
    }

    /** TalkBack's double tap: a first activation starts talking and the next one stops it. */
    fun onToggled(availability: TalkAvailability) {
        when (local.value.phase) {
            TalkPhase.Idle -> onPressed(availability)
            TalkPhase.Connecting -> cancel(notice = null)
            TalkPhase.Talking -> stop()
            TalkPhase.Stopping -> Unit
        }
    }

    /** Android's answer about the microphone. Allowed: Talk is pressed again to speak. */
    fun onMicrophoneResult(granted: Boolean) = update {
        it.copy(notice = if (granted) TalkNotice.MicrophoneAllowed else TalkNotice.MicrophoneDenied)
    }

    /** The sound button was pressed while talking: the camera's sound stays off, and Talk says so. */
    fun onSoundWhileTalking() = update { it.copy(notice = TalkNotice.SoundOffWhileTalking) }

    /** The screen leaves or goes to the background: whatever the talk is doing, it ends. */
    fun end() {
        when (local.value.phase) {
            TalkPhase.Connecting -> cancel(notice = null)
            TalkPhase.Talking -> stop()
            TalkPhase.Idle, TalkPhase.Stopping -> Unit
        }
    }

    private fun start() {
        val opened = openTalk(deviceId)
        session = opened
        update { it.copy(phase = TalkPhase.Connecting, notice = null) }
        job = scope.launch {
            opened.events.collect { event ->
                when (event) {
                    TalkEvent.Talking -> update { it.copy(phase = TalkPhase.Talking) }

                    is TalkEvent.Ended -> {
                        session = null
                        update { it.copy(phase = TalkPhase.Idle, notice = event.failure?.let(TalkNotice::Failed)) }
                    }
                }
            }
        }
    }

    /** Ends the talk cleanly: the camera plays what it received; the socket says when it is over. */
    private fun stop() {
        session?.stop()
        update { it.copy(phase = TalkPhase.Stopping) }
    }

    /** Ends the talk now, before the camera accepted it. */
    private fun cancel(notice: TalkNotice?) {
        job?.cancel()
        job = null
        session = null
        update { it.copy(phase = TalkPhase.Idle, notice = notice) }
    }

    private fun update(transform: (TalkUiState) -> TalkUiState) = local.update(transform)
}

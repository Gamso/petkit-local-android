package org.petkitlocal.feature.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.talk.MicrophoneAccess
import org.petkitlocal.core.domain.talk.OpenTalkUseCase
import org.petkitlocal.core.domain.talk.TalkEvent
import org.petkitlocal.core.domain.talk.TalkSession
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveSessionRunner
import org.petkitlocal.core.domain.video.ObserveRecentClipsUseCase
import org.petkitlocal.core.domain.video.RefreshRecentClipsUseCase
import org.petkitlocal.core.domain.video.SaveSnapshotUseCase
import org.petkitlocal.core.ui.RecentClipsState
import org.petkitlocal.core.ui.live.LivePlayback
import org.petkitlocal.core.ui.live.LivePlayerFactory
import org.petkitlocal.core.ui.live.playbackOf

/** Whether the screen has a camera to show. */
enum class LiveAvailability {
    /** The cache has not answered yet (a few milliseconds). */
    Loading,

    Camera,

    /** The device has no camera (`is_camera` false). */
    NotACamera,

    /** Not (or no longer) on the server. */
    Gone,
}

/** What a snapshot came to, said under the controls for a few seconds. */
enum class SnapshotResult { Saved, Failed, NoPicture }

/** What the screen does once for the ViewModel: Android's own prompts. */
sealed interface LiveEffect {
    /** Ask Android for the microphone; answer with [LiveViewModel.onMicrophoneResult]. */
    data object RequestMicrophone : LiveEffect
}

/** What the live video screen shows (SPEC.md §5.7). */
data class LiveUiState(
    val deviceId: Long,
    val deviceName: String? = null,
    val availability: LiveAvailability = LiveAvailability.Loading,
    val session: LiveSession = LiveSession(),
    /** Live video starts muted: a camera's sound should not surprise anyone. */
    val muted: Boolean = true,
    val fullscreen: Boolean = false,
    val savingSnapshot: Boolean = false,
    val snapshot: SnapshotResult? = null,
    /** The camera's recent clips, under the picture. */
    val clips: RecentClipsState = RecentClipsState(),
    /** Talking through the camera. */
    val talk: TalkUiState = TalkUiState(),
) {
    /**
     * Whether the camera's sound is off: as the user set it, and always while Talk is busy, since
     * the camera has no echo cancellation and its microphone would hear its own speaker. [muted]
     * is left as it was, so the user's choice comes back when the talk ends.
     */
    val soundMuted: Boolean get() = muted || talk.busy

    /** The player to build, or none: not a camera, in the background, waiting or stopped. */
    val playback: LivePlayback?
        get() = session.playbackOf(deviceId).takeIf { availability == LiveAvailability.Camera }
}

/**
 * The live video of one camera. The [LiveSession] decides which transport to try and when, and
 * its [LiveSessionRunner] counts down its waits (as on a camera page's live card); this ViewModel
 * feeds it what the screen and the player report, saves snapshots, and keeps the camera's recent
 * clips under it. The player itself is the screen's (it needs a Context) and follows [LiveUiState.playback].
 */
@HiltViewModel
@Suppress("TooManyFunctions") // One function per user intent and player event (ARCHITECTURE.md §3).
class LiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeDevice: ObserveDeviceUseCase,
    private val saveSnapshot: SaveSnapshotUseCase,
    observeRecentClips: ObserveRecentClipsUseCase,
    private val refreshRecentClips: RefreshRecentClipsUseCase,
    private val openTalk: OpenTalkUseCase,
    private val microphone: MicrophoneAccess,
    private val clock: Clock,
    /** Handed to the screen, which builds its players with it. */
    val playerFactory: LivePlayerFactory,
) : ViewModel() {
    val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY])
    private val local = MutableStateFlow(
        LiveUiState(deviceId, clips = RecentClipsState(zone = clock.zone, today = LocalDate.now(clock))),
    )
    private val live = LiveSessionRunner(viewModelScope)
    private var snapshotNotice: Job? = null
    private var clipsRefresh: Job? = null
    private var talkJob: Job? = null
    private var talkSession: TalkSession? = null
    private val effectChannel = Channel<LiveEffect>(Channel.BUFFERED)

    /** One-shot effects, collected once by the screen. */
    val effects: Flow<LiveEffect> = effectChannel.receiveAsFlow()

    val uiState: StateFlow<LiveUiState> = combine(
        observeDevice(deviceId),
        observeRecentClips(deviceId),
        local,
        live.session,
    ) { device, clips, state, session ->
        state.copy(
            session = session,
            deviceName = device?.modelName,
            availability = device.availability(),
            clips = state.clips.copy(clips = clips, cacheLoaded = true),
            talk = state.talk.copy(availability = device?.talk ?: TalkAvailability.Unsupported),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    /** The player to have now, for a collection that goes on in the background ([uiState]'s stops). */
    val playback: StateFlow<LivePlayback?> = uiState.map { it.playback }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    init {
        onRefreshClips()
    }

    /** The screen is visible: play. */
    fun onVisible() = live.start()

    /** The screen went to the background or away: no player, no socket, no talk. */
    fun onHidden() {
        live.stop()
        endTalk()
    }

    fun onRetry() = live.retry()

    /** The player of [attempt] shows its first frame. */
    fun onPlaying(attempt: Int) = live.playing(attempt)

    /** The player of [attempt] stopped with [failure]. */
    fun onPlayerFailed(attempt: Int, failure: LiveFailure) = live.fail(attempt, failure)

    /** Reads the camera's newest clips from the server; one refresh at a time, for a camera only. */
    fun onRefreshClips() {
        if (clipsRefresh?.isActive == true) return
        clipsRefresh = viewModelScope.launch {
            if (observeDevice(deviceId).first()?.isCamera != true) return@launch
            updateClips { it.copy(refreshing = true, error = null, today = LocalDate.now(clock)) }
            val result = refreshRecentClips(deviceId)
            updateClips {
                it.copy(
                    refreshing = false,
                    refreshed = it.refreshed || result is Outcome.Success,
                    error = (result as? Outcome.Failure)?.error,
                )
            }
        }
    }

    private fun updateClips(transform: (RecentClipsState) -> RecentClipsState) =
        local.update { it.copy(clips = transform(it.clips)) }

    /** The sound button. While Talk is busy the camera's sound stays off, and the screen says so. */
    fun onToggleMute() = local.update {
        if (it.talk.busy) {
            it.withTalk { talk ->
                talk.copy(notice = TalkNotice.SoundOffWhileTalking)
            }
        } else {
            it.copy(muted = !it.muted)
        }
    }

    /**
     * Talk is pressed (the finger went down): the talk opens, and the camera's speaker plays the
     * microphone once it accepted, until [onTalkReleased]. When the camera cannot talk, or the
     * microphone is not allowed yet, the screen says so instead (and Android is asked).
     */
    fun onTalkPressed() {
        val talk = uiState.value.talk
        if (local.value.talk.phase != TalkPhase.Idle) return
        when {
            talk.availability != TalkAvailability.Available ->
                updateTalk { it.copy(notice = TalkNotice.Unavailable(talk.availability)) }

            !microphone.granted() -> {
                updateTalk { it.copy(notice = TalkNotice.MicrophoneReason) }
                effectChannel.trySend(LiveEffect.RequestMicrophone)
            }

            else -> startTalk()
        }
    }

    /** Talk is let go, or the press was cancelled (the finger slid off): the talk ends. */
    fun onTalkReleased() {
        when (local.value.talk.phase) {
            TalkPhase.Connecting -> cancelTalk(TalkNotice.HoldToTalk)
            TalkPhase.Talking -> stopTalk()
            TalkPhase.Idle, TalkPhase.Stopping -> Unit
        }
    }

    /**
     * Talk activated without a press, by TalkBack's double tap: holding is impractical there, so a
     * first activation starts talking and the next one stops it.
     */
    fun onTalkToggled() {
        when (local.value.talk.phase) {
            TalkPhase.Idle -> onTalkPressed()
            TalkPhase.Connecting -> cancelTalk(notice = null)
            TalkPhase.Talking -> stopTalk()
            TalkPhase.Stopping -> Unit
        }
    }

    /** Android's answer about the microphone. Allowed: Talk is pressed again to speak. */
    fun onMicrophoneResult(granted: Boolean) = updateTalk {
        it.copy(notice = if (granted) TalkNotice.MicrophoneAllowed else TalkNotice.MicrophoneDenied)
    }

    private fun startTalk() {
        val session = openTalk(deviceId)
        talkSession = session
        updateTalk { it.copy(phase = TalkPhase.Connecting, notice = null) }
        talkJob = viewModelScope.launch {
            session.events.collect { event ->
                when (event) {
                    TalkEvent.Talking -> updateTalk { it.copy(phase = TalkPhase.Talking) }

                    is TalkEvent.Ended -> {
                        talkSession = null
                        updateTalk { it.copy(phase = TalkPhase.Idle, notice = event.failure?.let(TalkNotice::Failed)) }
                    }
                }
            }
        }
    }

    /** Ends the talk cleanly: the camera plays what it received; the socket says when it is over. */
    private fun stopTalk() {
        talkSession?.stop()
        updateTalk { it.copy(phase = TalkPhase.Stopping) }
    }

    /** Ends the talk now, before the camera accepted it. */
    private fun cancelTalk(notice: TalkNotice?) {
        talkJob?.cancel()
        talkJob = null
        talkSession = null
        updateTalk { it.copy(phase = TalkPhase.Idle, notice = notice) }
    }

    /** Leaving: whatever the talk is doing, it ends. */
    private fun endTalk() {
        when (local.value.talk.phase) {
            TalkPhase.Connecting -> cancelTalk(notice = null)
            TalkPhase.Talking -> stopTalk()
            TalkPhase.Idle, TalkPhase.Stopping -> Unit
        }
    }

    private fun updateTalk(transform: (TalkUiState) -> TalkUiState) = local.update { it.withTalk(transform) }

    private fun LiveUiState.withTalk(transform: (TalkUiState) -> TalkUiState) = copy(talk = transform(talk))

    fun onFullscreen(on: Boolean) = local.update { it.copy(fullscreen = on) }

    /** A frame was taken ([jpeg]), or there was none to take (null). */
    fun onSnapshot(jpeg: ByteArray?) {
        if (jpeg == null) return showSnapshot(SnapshotResult.NoPicture)
        if (local.value.savingSnapshot) return
        local.update { it.copy(savingSnapshot = true) }
        viewModelScope.launch {
            val name = uiState.value.deviceName.orEmpty()
            val saved = saveSnapshot(name, jpeg)
            local.update { it.copy(savingSnapshot = false) }
            showSnapshot(if (saved) SnapshotResult.Saved else SnapshotResult.Failed)
        }
    }

    private fun showSnapshot(result: SnapshotResult) {
        local.update { it.copy(snapshot = result) }
        snapshotNotice?.cancel()
        snapshotNotice = viewModelScope.launch {
            delay(SNAPSHOT_NOTICE)
            local.update { it.copy(snapshot = null) }
        }
    }

    private fun Device?.availability(): LiveAvailability = when {
        this == null -> LiveAvailability.Gone
        isCamera -> LiveAvailability.Camera
        else -> LiveAvailability.NotACamera
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private val SNAPSHOT_NOTICE = 4.seconds

        /** The [LiveDestination] property the navigation library stores the argument under. */
        const val DEVICE_ID_KEY = "deviceId"
    }
}

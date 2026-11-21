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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.talk.MicrophoneAccess
import org.petkitlocal.core.domain.talk.OpenTalkUseCase
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveSessionRunner
import org.petkitlocal.core.domain.video.ObserveRecentClipsUseCase
import org.petkitlocal.core.domain.video.RefreshRecentClipsUseCase
import org.petkitlocal.core.domain.video.SaveSnapshotUseCase
import org.petkitlocal.core.ui.RecentClipsState
import org.petkitlocal.core.ui.live.LivePlayback
import org.petkitlocal.core.ui.live.LivePlayerFactory
import org.petkitlocal.core.ui.live.SnapshotResult
import org.petkitlocal.core.ui.live.playbackOf
import org.petkitlocal.core.ui.talk.TalkController
import org.petkitlocal.core.ui.talk.TalkUiState

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
    private val talk = TalkController(viewModelScope, deviceId, openTalk, microphone)

    /** One-shot effects, collected once by the screen. */
    val effects: Flow<LiveEffect> = talk.requestMicrophone.map { LiveEffect.RequestMicrophone }

    val uiState: StateFlow<LiveUiState> = combine(
        observeDevice(deviceId),
        observeRecentClips(deviceId),
        local,
        live.session,
        talk.state,
    ) { device, clips, state, session, talking ->
        state.copy(
            session = session,
            deviceName = device?.modelName,
            availability = device.availability(),
            clips = state.clips.copy(clips = clips, cacheLoaded = true),
            talk = talking.copy(availability = device?.talk ?: TalkAvailability.Unsupported),
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
        talk.end()
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
    fun onToggleMute() {
        if (talk.busy) talk.onSoundWhileTalking() else local.update { it.copy(muted = !it.muted) }
    }

    /** Talk is pressed (the finger went down); see [TalkController.onPressed]. */
    fun onTalkPressed() = talk.onPressed(uiState.value.talk.availability)

    /** Talk is let go, or the press was cancelled (the finger slid off): the talk ends. */
    fun onTalkReleased() = talk.onReleased()

    /** Talk activated without a press, by TalkBack's double tap: starts or stops talking. */
    fun onTalkToggled() = talk.onToggled(uiState.value.talk.availability)

    /** Android's answer about the microphone. Allowed: Talk is pressed again to speak. */
    fun onMicrophoneResult(granted: Boolean) = talk.onMicrophoneResult(granted)

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

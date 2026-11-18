package org.petkitlocal.feature.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.domain.video.ObserveRecentClipsUseCase
import org.petkitlocal.core.domain.video.RefreshRecentClipsUseCase
import org.petkitlocal.core.domain.video.SaveSnapshotUseCase
import org.petkitlocal.core.ui.RecentClipsState

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

/** The player to have now: a new one for every attempt of the live session. */
data class LivePlayback(val deviceId: Long, val transport: LiveTransport, val attempt: Int)

/** What the live video screen shows (SPEC.md §5.7). */
data class LiveUiState(
    val deviceId: Long,
    val deviceName: String? = null,
    val availability: LiveAvailability = LiveAvailability.Loading,
    val session: LiveSession = LiveSession(),
    /** How far behind the camera the picture is, measured by the player while it plays. */
    val delay: Duration? = null,
    /** Live video starts muted: a camera's sound should not surprise anyone. */
    val muted: Boolean = true,
    val fullscreen: Boolean = false,
    val savingSnapshot: Boolean = false,
    val snapshot: SnapshotResult? = null,
    /** The camera's recent clips, under the picture. */
    val clips: RecentClipsState = RecentClipsState(),
) {
    /** The player to build, or none: not a camera, in the background, waiting or stopped. */
    val playback: LivePlayback?
        get() = LivePlayback(deviceId, session.transport, session.attempt).takeIf {
            availability == LiveAvailability.Camera &&
                (session.phase == LivePhase.Starting || session.phase == LivePhase.Playing)
        }
}

/**
 * The live video of one camera. The [LiveSession] decides which transport to try and when; this
 * ViewModel feeds it what the screen and the player report, counts down its waits, and saves
 * snapshots, and keeps the camera's recent clips under it. The player itself is the screen's (it needs a Context) and follows [LiveUiState.playback].
 */
@HiltViewModel
@Suppress("TooManyFunctions") // One function per user intent and player event (ARCHITECTURE.md §3).
class LiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeDevice: ObserveDeviceUseCase,
    private val saveSnapshot: SaveSnapshotUseCase,
    observeRecentClips: ObserveRecentClipsUseCase,
    private val refreshRecentClips: RefreshRecentClipsUseCase,
    private val clock: Clock,
    /** Handed to the screen, which builds its players with it. */
    val playerFactory: LivePlayerFactory,
) : ViewModel() {
    val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY])
    private val local = MutableStateFlow(
        LiveUiState(deviceId, clips = RecentClipsState(zone = clock.zone, today = LocalDate.now(clock))),
    )
    private var retry: Job? = null
    private var snapshotNotice: Job? = null
    private var clipsRefresh: Job? = null

    val uiState: StateFlow<LiveUiState> = combine(
        observeDevice(deviceId),
        observeRecentClips(deviceId),
        local,
    ) { device, clips, state ->
        state.copy(
            deviceName = device?.modelName,
            availability = device.availability(),
            clips = state.clips.copy(clips = clips, cacheLoaded = true),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        onRefreshClips()
    }

    /** The screen is visible: play. */
    fun onVisible() = updateSession { it.start() }

    /** The screen went to the background or away: no player, no socket. */
    fun onHidden() = updateSession { it.stop() }

    fun onRetry() = updateSession { it.retry() }

    /** The player of [attempt] shows its first frame. */
    fun onPlaying(attempt: Int) = updateSession { it.playing(attempt) }

    /** The player of [attempt] stopped with [failure]. */
    fun onPlayerFailed(attempt: Int, failure: LiveFailure) = updateSession { it.fail(attempt, failure) }

    /** The player of [attempt] measured how far behind the camera it is. */
    fun onDelay(attempt: Int, delay: Duration?) = local.update { state ->
        val current = attempt == state.session.attempt && state.session.phase == LivePhase.Playing
        if (current) state.copy(delay = delay) else state
    }

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

    fun onToggleMute() = local.update { it.copy(muted = !it.muted) }

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

    /** Moves the session on, forgets a delay measured by an older player, and times its waits. */
    private fun updateSession(transform: (LiveSession) -> LiveSession) {
        val before = local.value.session
        local.update { state ->
            val session = transform(state.session)
            state.copy(session = session, delay = state.delay.takeIf { session.phase == LivePhase.Playing })
        }
        val after = local.value.session
        val phase = after.phase
        if (after == before) return
        retry?.cancel()
        if (phase is LivePhase.Waiting) {
            retry = viewModelScope.launch {
                delay(phase.retryIn)
                updateSession { it.retryDue() }
            }
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

package org.petkitlocal.feature.device.common

import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.talk.MicrophoneAccess
import org.petkitlocal.core.domain.talk.OpenTalkUseCase
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSessionRunner
import org.petkitlocal.core.domain.video.ObserveLatestStillUseCase
import org.petkitlocal.core.domain.video.SaveSnapshotUseCase
import org.petkitlocal.core.ui.LiveCard
import org.petkitlocal.core.ui.LiveCardCallbacks
import org.petkitlocal.core.ui.LiveCardFullscreen
import org.petkitlocal.core.ui.LiveCardState
import org.petkitlocal.core.ui.live.DEFAULT_LIVE_ASPECT
import org.petkitlocal.core.ui.live.DarkSystemBars
import org.petkitlocal.core.ui.live.ImmersiveLandscape
import org.petkitlocal.core.ui.live.LivePlayback
import org.petkitlocal.core.ui.live.LivePlayerEvents
import org.petkitlocal.core.ui.live.LivePlayerFactory
import org.petkitlocal.core.ui.live.LiveVideoTexture
import org.petkitlocal.core.ui.live.SnapshotResult
import org.petkitlocal.core.ui.live.applyMuted
import org.petkitlocal.core.ui.live.aspectRatioOf
import org.petkitlocal.core.ui.live.playbackOf
import org.petkitlocal.core.ui.live.rememberLivePlayer
import org.petkitlocal.core.ui.live.rememberVideoSize
import org.petkitlocal.core.ui.live.textureJpeg
import org.petkitlocal.core.ui.talk.MicrophoneRequests
import org.petkitlocal.core.ui.talk.TalkCallbacks
import org.petkitlocal.core.ui.talk.TalkController
import org.petkitlocal.core.ui.talk.openAppSettings
import org.petkitlocal.feature.device.DEVICE_ID_KEY

/**
 * The live card of a camera's page: its live video playing in place, over the latest still its
 * cache holds (ADR 0002) until the first frame, with Talk under it and a full screen of its own.
 * The still asks the server for nothing itself: the page's recent clips refresh
 * ([RecentClipsViewModel]) reads the device's newest cards, and a live update writes a new one.
 *
 * The video is the live screen's own machinery: a [LiveSessionRunner] decides what to play and
 * when to try again, and the page builds the player with [playerFactory]. It plays only while
 * something shows it ([onVisible], [onHidden]: the card on a started page, or its full screen): a
 * stream must not run while nobody looks, as a D4SH's camera is reached through a relay holding
 * the device's only connection, and each viewer takes one of the server's two slots per camera.
 * Full screen is the same player and the same session in the whole window: no second viewer.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // One function per user intent and player event (ARCHITECTURE.md §3).
class LiveCardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeLatestStill: ObserveLatestStillUseCase,
    observeDevice: ObserveDeviceUseCase,
    openTalk: OpenTalkUseCase,
    microphone: MicrophoneAccess,
    private val saveSnapshot: SaveSnapshotUseCase,
    clock: Clock,
    /** Handed to the page, which builds the card's players with it. */
    val playerFactory: LivePlayerFactory,
) : ViewModel() {
    private val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY]) { "no $DEVICE_ID_KEY argument" }
    private val live = LiveSessionRunner(viewModelScope)
    private val talk = TalkController(viewModelScope, deviceId, openTalk, microphone)
    private val local = MutableStateFlow(LiveCardState(zone = clock.zone, today = LocalDate.now(clock)))
    private var shown = 0
    private var snapshotNotice: Job? = null
    private val device = observeDevice(deviceId)

    val state: StateFlow<LiveCardState> = combine(
        observeLatestStill(deviceId),
        live.session,
        local,
        talk.state,
        device,
    ) { still, session, state, talking, device ->
        state.copy(
            still = still,
            today = LocalDate.now(clock),
            session = session,
            talk = talking.copy(availability = device?.talk ?: TalkAvailability.Unsupported),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    /** The player to have now; it follows the session in the background too (rememberLivePlayer). */
    val playback: StateFlow<LivePlayback?> = live.session.map { it.playbackOf(deviceId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Each time Android must be asked for the microphone; answer with [onMicrophoneResult]. */
    val requestMicrophone = talk.requestMicrophone

    /** The card on a started page, or its full screen, shows the video: play. */
    fun onVisible() {
        shown++
        live.start()
    }

    /**
     * What showed it left, or the app went to the background: once nothing shows it, no player,
     * no viewer slot, no talk.
     */
    fun onHidden() {
        shown = (shown - 1).coerceAtLeast(0)
        if (shown == 0) {
            live.stop()
            talk.end()
        }
    }

    fun onRetry() = live.retry()

    /** The player of [attempt] shows its first frame. */
    fun onPlaying(attempt: Int) = live.playing(attempt)

    /** The player of [attempt] stopped with [failure]. */
    fun onPlayerFailed(attempt: Int, failure: LiveFailure) = live.fail(attempt, failure)

    /** The sound button. While Talk is busy the camera's sound stays off, and Talk says so. */
    fun onToggleMute() {
        if (talk.busy) talk.onSoundWhileTalking() else local.update { it.copy(muted = !it.muted) }
    }

    /** Full screen in place, or back to the page: the same player, the same session. */
    fun onFullscreen(on: Boolean) = local.update { it.copy(fullscreen = on) }

    fun onTalkPressed() = talk.onPressed(state.value.talk.availability)

    fun onTalkReleased() = talk.onReleased()

    fun onTalkToggled() = talk.onToggled(state.value.talk.availability)

    fun onMicrophoneResult(granted: Boolean) = talk.onMicrophoneResult(granted)

    /** A frame was taken ([jpeg]), or there was none to take (null). */
    fun onSnapshot(jpeg: ByteArray?) {
        if (jpeg == null) return showSnapshot(SnapshotResult.NoPicture)
        if (local.value.savingSnapshot) return
        local.update { it.copy(savingSnapshot = true) }
        viewModelScope.launch {
            val name = device.first()?.modelName.orEmpty()
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val SNAPSHOT_NOTICE = 4.seconds
    }
}

/**
 * A camera page's live card as the page shows it: [state], what its controls do, when it is shown
 * and left ([onVisible], [onHidden]), and its [video] at the video's [aspectRatio]. The default
 * plays nothing: previews and screen tests have no player.
 */
@Immutable
internal class LiveCardUi(
    val state: LiveCardState = LiveCardState(),
    val callbacks: LiveCardCallbacks = LiveCardCallbacks(),
    val onVisible: () -> Unit = {},
    val onHidden: () -> Unit = {},
    val aspectRatio: Float = DEFAULT_LIVE_ASPECT,
    val video: @Composable (Modifier) -> Unit = {},
)

/** The page's device's live card, playing while it is shown. */
@Composable
internal fun rememberLiveCard(viewModel: LiveCardViewModel = hiltViewModel()): LiveCardUi {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Not lifecycle-aware: the stop at ON_STOP must still reach the player (rememberLivePlayer).
    val playback by viewModel.playback.collectAsState()
    val events = remember(viewModel) { LivePlayerEvents(viewModel::onPlaying, viewModel::onPlayerFailed) }
    val player = rememberLivePlayer(playback, viewModel.playerFactory, events)
    LaunchedEffect(player, state.soundMuted) { player?.applyMuted(state.soundMuted) }
    val videoSize = rememberVideoSize(player)
    MicrophoneRequests(viewModel.requestMicrophone, viewModel::onMicrophoneResult)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var texture by remember { mutableStateOf<TextureView?>(null) }
    val callbacks = remember(viewModel) {
        LiveCardCallbacks(
            onRetry = viewModel::onRetry,
            onToggleMute = viewModel::onToggleMute,
            onFullscreen = { viewModel.onFullscreen(true) },
            onExitFullscreen = { viewModel.onFullscreen(false) },
            onSnapshot = {
                // The card's or the full screen's, whichever is on screen now.
                val view = texture?.takeIf { it.isAttachedToWindow }
                val playing = viewModel.state.value.session.phase == LivePhase.Playing
                scope.launch { viewModel.onSnapshot(view?.takeIf { playing }?.let { textureJpeg(it) }) }
            },
            talk = TalkCallbacks(
                onTalkPressed = viewModel::onTalkPressed,
                onTalkReleased = viewModel::onTalkReleased,
                onTalkToggled = viewModel::onTalkToggled,
                onOpenAppSettings = { openAppSettings(context) },
            ),
        )
    }
    return LiveCardUi(
        state = state,
        callbacks = callbacks,
        onVisible = viewModel::onVisible,
        onHidden = viewModel::onHidden,
        aspectRatio = aspectRatioOf(videoSize),
        video = { modifier -> LiveVideoTexture(player, modifier, onView = { view -> view?.let { texture = it } }) },
    )
}

/**
 * The live card at the top of [device]'s page, for a device the server says has a camera
 * (`is_camera`) only. It plays while it is composed on a started page: leaving the composition
 * (the page left, or the card scrolled out of a list), the app going to the background and
 * another screen covering the page all stop it, unless its full screen still shows it.
 */
@Composable
internal fun CameraLiveCard(device: Device, live: LiveCardUi, modifier: Modifier = Modifier) {
    if (!device.isCamera) return
    VisibleWhileStarted(live)
    LiveCard(device.modelName, live.state, live.callbacks, modifier, live.aspectRatio, live.video)
}

/**
 * [device]'s live card in full screen, over the whole page: the same player in the whole window,
 * landscape with the system bars hidden; back, or the button, returns to the page. Nothing while
 * the card is on the page.
 */
@Composable
internal fun CameraLiveFullscreen(device: Device?, live: LiveCardUi) {
    if (device?.isCamera != true || !live.state.fullscreen) return
    VisibleWhileStarted(live)
    DarkSystemBars()
    ImmersiveLandscape(on = true)
    BackHandler { live.callbacks.onExitFullscreen() }
    LiveCardFullscreen(device.modelName, live.state, live.callbacks, aspectRatio = live.aspectRatio, video = live.video)
}

/** Shows [live]'s video while this is composed on a started screen. */
@Composable
private fun VisibleWhileStarted(live: LiveCardUi) {
    val latest by rememberUpdatedState(live)
    LifecycleStartEffect(Unit) {
        latest.onVisible()
        onStopOrDispose { latest.onHidden() }
    }
}

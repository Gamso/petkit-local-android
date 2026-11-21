package org.petkitlocal.feature.device.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LiveSessionRunner
import org.petkitlocal.core.domain.video.ObserveLatestStillUseCase
import org.petkitlocal.core.ui.LiveCard
import org.petkitlocal.core.ui.LiveCardCallbacks
import org.petkitlocal.core.ui.LiveCardState
import org.petkitlocal.core.ui.live.LivePlayback
import org.petkitlocal.core.ui.live.LivePlayerEvents
import org.petkitlocal.core.ui.live.LivePlayerFactory
import org.petkitlocal.core.ui.live.LiveVideoTexture
import org.petkitlocal.core.ui.live.applyMuted
import org.petkitlocal.core.ui.live.playbackOf
import org.petkitlocal.core.ui.live.rememberLivePlayer
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.DeviceNavigation

/**
 * The live card of a camera's page: its live video playing in place, over the latest still its
 * cache holds (ADR 0002) until the first frame. The still asks the server for nothing itself: the
 * page's recent clips refresh ([RecentClipsViewModel]) reads the device's newest cards when the
 * page opens and when it is pulled down, and a live update writes a new one, so it follows both.
 *
 * The video is the live screen's own machinery: a [LiveSessionRunner] decides what to play and
 * when to try again, and the screen builds the player with [playerFactory]. It plays only while
 * the card is on a visible page ([onVisible], [onHidden]): a stream must not run while nobody
 * looks, as a D4SH's camera is reached through a relay holding the device's only connection, and
 * each viewer takes one of the server's two slots per camera.
 */
@HiltViewModel
class LiveCardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeLatestStill: ObserveLatestStillUseCase,
    clock: Clock,
    /** Handed to the page, which builds the card's players with it. */
    val playerFactory: LivePlayerFactory,
) : ViewModel() {
    private val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY]) { "no $DEVICE_ID_KEY argument" }
    private val live = LiveSessionRunner(viewModelScope)
    private val muted = MutableStateFlow(true)

    val state: StateFlow<LiveCardState> = combine(
        observeLatestStill(deviceId),
        live.session,
        muted,
    ) { still, session, muted ->
        LiveCardState(still, clock.zone, LocalDate.now(clock), session, muted)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        LiveCardState(zone = clock.zone, today = LocalDate.now(clock)),
    )

    /** The player to have now; it follows the session in the background too (rememberLivePlayer). */
    val playback: StateFlow<LivePlayback?> = live.session.map { it.playbackOf(deviceId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The card is on a visible page: play. */
    fun onVisible() = live.start()

    /** The card left the screen, or the app went to the background: no player, no viewer slot. */
    fun onHidden() = live.stop()

    fun onRetry() = live.retry()

    /** The player of [attempt] shows its first frame. */
    fun onPlaying(attempt: Int) = live.playing(attempt)

    /** The player of [attempt] stopped with [failure]. */
    fun onPlayerFailed(attempt: Int, failure: LiveFailure) = live.fail(attempt, failure)

    fun onToggleMute() = muted.update { !it }

    /**
     * Full screen is opening the live video screen: this card's viewer goes first. The server
     * keeps one view per camera for a session and caps a camera at two, so the card must not hold
     * one while the screen opens its own. It starts afresh when the page is shown again, even
     * after a failure only the user could retry: the live screen started anew meanwhile.
     */
    fun onOpenFullscreen() = live.release()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * A camera page's live card as the page shows it: [state], what its controls do, when it is shown
 * and left ([onVisible], [onHidden]), and its [video]. The default plays nothing: previews and
 * screen tests have no player.
 */
@Immutable
internal class LiveCardUi(
    val state: LiveCardState = LiveCardState(),
    val callbacks: LiveCardCallbacks = LiveCardCallbacks(),
    val onVisible: () -> Unit = {},
    val onHidden: () -> Unit = {},
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
    LaunchedEffect(player, state.muted) { player?.applyMuted(state.muted) }
    val callbacks = remember(viewModel) {
        LiveCardCallbacks(
            onRetry = viewModel::onRetry,
            onToggleMute = viewModel::onToggleMute,
            onFullscreen = viewModel::onOpenFullscreen,
        )
    }
    return LiveCardUi(
        state = state,
        callbacks = callbacks,
        onVisible = viewModel::onVisible,
        onHidden = viewModel::onHidden,
        video = { modifier -> LiveVideoTexture(player, modifier) },
    )
}

/**
 * The live card at the top of [device]'s page, for a device the server says has a camera
 * (`is_camera`) only. It plays while it is composed on a started page: leaving the composition
 * (the page left, or the card scrolled out of a list), the app going to the background and
 * another screen covering the page all stop it. Full screen releases its viewer, then opens the
 * live video through [onOpenLive].
 */
@Composable
internal fun CameraLiveCard(device: Device, live: LiveCardUi, onOpenLive: () -> Unit, modifier: Modifier = Modifier) {
    if (!device.isCamera) return
    val latest by rememberUpdatedState(live)
    LifecycleStartEffect(device.id) {
        latest.onVisible()
        onStopOrDispose { latest.onHidden() }
    }
    val callbacks = live.callbacks.copy(
        onFullscreen = {
            live.callbacks.onFullscreen()
            onOpenLive()
        },
    )
    LiveCard(device.modelName, live.state, callbacks, modifier, live.video)
}

/** The live card of [device]'s family page, opening the live video through [navigation]. */
@Composable
internal fun CameraLiveCard(
    device: Device,
    live: LiveCardUi,
    navigation: DeviceNavigation,
    modifier: Modifier = Modifier,
) = CameraLiveCard(device, live, onOpenLive = { navigation.onOpenLive(device.id) }, modifier = modifier)

package org.petkitlocal.feature.live

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport

/** What the live screen's user can do; the route wires each to the ViewModel. */
data class LiveCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onToggleMute: () -> Unit = {},
    val onFullscreen: (on: Boolean) -> Unit = {},
    val onSnapshot: () -> Unit = {},
)

object LiveTestTags {
    const val SCREEN = "live_screen"
    const val VIDEO = "live_video"

    /** "Live · 0.4 s" while frames are shown. */
    const val BADGE = "live_badge"

    /** "HLS" while on the fallback. */
    const val TRANSPORT = "live_transport"
    const val STATUS = "live_status"
}

@Composable
internal fun LiveRoute(onBack: () -> Unit, viewModel: LiveViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Playing only while the screen is visible: no player and no socket in the background.
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    val events = remember(viewModel) {
        LivePlayerEvents(viewModel::onPlaying, viewModel::onPlayerFailed, viewModel::onDelay)
    }
    val player = rememberLivePlayer(uiState.playback, viewModel.playerFactory, events)
    LaunchedEffect(player, uiState.muted) { player?.applyMuted(uiState.muted) }
    val videoSize = rememberVideoSize(player)
    var surface by remember { mutableStateOf<SurfaceView?>(null) }
    val scope = rememberCoroutineScope()

    DarkSystemBars()
    ImmersiveLandscape(uiState.fullscreen)
    BackHandler(enabled = uiState.fullscreen) { viewModel.onFullscreen(false) }

    LiveScreen(
        uiState = uiState,
        callbacks = LiveCallbacks(
            onBack = onBack,
            onRetry = viewModel::onRetry,
            onToggleMute = viewModel::onToggleMute,
            onFullscreen = viewModel::onFullscreen,
            onSnapshot = {
                val view = surface
                val playing = viewModel.uiState.value.session.phase == LivePhase.Playing
                scope.launch { viewModel.onSnapshot(view?.takeIf { playing }?.let { captureJpeg(it, videoSize) }) }
            },
        ),
        aspectRatio = aspectRatioOf(videoSize),
    ) { modifier -> LiveVideoSurface(player, onView = { surface = it }, modifier = modifier) }
}

/** The size of [player]'s video, followed as it changes; unknown (0 x 0) before the first frame. */
@Composable
private fun rememberVideoSize(player: Player?): VideoSize {
    var size by remember(player) { mutableStateOf(player?.videoSize ?: VideoSize.UNKNOWN) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                size = videoSize
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    return size
}

/**
 * A camera's live video (SPEC.md §5.7), always dark (docs/DESIGN.md): the picture full width
 * with its badge (live, how far behind, HLS on the fallback) and what the session is doing over
 * it, then sound, snapshot and full screen, and "Talk", which this app does not offer. Full
 * screen, the picture takes the whole landscape window with its controls over it.
 *
 * [video] draws the picture in the modifier it is given; the route passes the player's surface.
 */
@Composable
internal fun LiveScreen(
    uiState: LiveUiState,
    callbacks: LiveCallbacks,
    modifier: Modifier = Modifier,
    aspectRatio: Float = DEFAULT_ASPECT,
    video: @Composable (Modifier) -> Unit = {},
) {
    PkTheme(darkTheme = true) {
        val fullscreen = uiState.fullscreen && uiState.availability == LiveAvailability.Camera
        // One layout for both modes, so the picture's surface (and its player) stays the same.
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(PkTheme.colors.background)
                .testTag(LiveTestTags.SCREEN)
                .then(
                    if (fullscreen) Modifier else Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()),
                ),
        ) {
            if (!fullscreen) LiveHeader(uiState.deviceName, callbacks.onBack)
            VideoBox(
                uiState = uiState,
                callbacks = callbacks,
                fullscreen = fullscreen,
                aspectRatio = aspectRatio,
                video = video,
                modifier = if (fullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .padding(horizontal = PkTheme.spacing.gutter)
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clip(PkTheme.shapes.tile)
                },
            )
            if (!fullscreen && uiState.availability == LiveAvailability.Camera) LiveControls(uiState, callbacks)
        }
    }
}

private const val DEFAULT_ASPECT = 16f / 9f

@Composable
private fun LiveHeader(deviceName: String?, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(PkTheme.spacing.gutter),
    ) {
        PkBackButton(onClick = onBack)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Column(Modifier.weight(1f)) {
            Text(
                deviceName ?: stringResource(R.string.live_title),
                style = PkTheme.typography.titleLarge,
                color = PkTheme.colors.onSurface,
                maxLines = 2,
                modifier = Modifier.semantics { heading() },
            )
            if (deviceName != null) {
                Text(
                    stringResource(R.string.live_title),
                    style = PkTheme.typography.bodyMedium,
                    color = PkTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun VideoBox(
    uiState: LiveUiState,
    callbacks: LiveCallbacks,
    fullscreen: Boolean,
    aspectRatio: Float,
    video: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(if (fullscreen) RectangleShape else PkTheme.shapes.tile)
            .background(Picture),
    ) {
        if (uiState.availability == LiveAvailability.Camera) {
            video(Modifier.aspectRatio(aspectRatio).testTag(LiveTestTags.VIDEO))
        }
        SessionOverlay(uiState, callbacks.onRetry)
        Badges(
            uiState.session,
            uiState.delay,
            Modifier
                .align(Alignment.TopStart)
                .then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier)
                .padding(PkTheme.spacing.s),
        )
        if (fullscreen) FullscreenControls(uiState, callbacks)
    }
}

/** The picture's own colours, whatever the theme: black around it, white on it. */
internal val Picture = Color.Black
internal val OnPicture = Color.White
internal const val SCRIM_ALPHA = 0.6f
internal const val PILL_ALPHA = 0.55f

@Preview(name = "Playing", widthDp = 360, heightDp = 640)
@Preview(name = "French", widthDp = 360, heightDp = 640, locale = "fr")
@Composable
private fun LiveScreenPlayingPreview() {
    LiveScreen(
        LiveUiState(
            deviceId = 1003,
            deviceName = "Purobot Max Pro 2",
            availability = LiveAvailability.Camera,
            session = LiveSession(phase = LivePhase.Playing, attempt = 1),
            delay = 400.milliseconds,
        ),
        LiveCallbacks(),
    )
}

@Preview(name = "HLS, reconnecting", widthDp = 360, heightDp = 640)
@Composable
private fun LiveScreenWaitingPreview() {
    LiveScreen(
        LiveUiState(
            deviceId = 1003,
            deviceName = "Purobot Max Pro 2",
            availability = LiveAvailability.Camera,
            session = LiveSession(
                transport = LiveTransport.Hls,
                phase = LivePhase.Waiting(LiveFailure.Network(), LiveSession.RETRY_DELAY),
                attempt = 3,
            ),
        ),
        LiveCallbacks(),
    )
}

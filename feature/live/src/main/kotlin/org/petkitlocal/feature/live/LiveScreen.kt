package org.petkitlocal.feature.live

import android.Manifest
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.ui.RecentClipsCard
import org.petkitlocal.core.ui.live.DEFAULT_LIVE_ASPECT
import org.petkitlocal.core.ui.live.DarkSystemBars
import org.petkitlocal.core.ui.live.ImmersiveLandscape
import org.petkitlocal.core.ui.live.LiveBadges
import org.petkitlocal.core.ui.live.LivePicture
import org.petkitlocal.core.ui.live.LivePlayerEvents
import org.petkitlocal.core.ui.live.LiveVideoSurface
import org.petkitlocal.core.ui.live.LiveVideoTestTags
import org.petkitlocal.core.ui.live.applyMuted
import org.petkitlocal.core.ui.live.aspectRatioOf
import org.petkitlocal.core.ui.live.rememberLivePlayer
import org.petkitlocal.core.ui.live.rememberVideoSize
import org.petkitlocal.core.ui.talk.TalkCallbacks
import org.petkitlocal.core.ui.talk.TalkTestTags
import org.petkitlocal.core.ui.talk.openAppSettings

/** What the live screen's user can do; the route wires each to the ViewModel. */
data class LiveCallbacks(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onToggleMute: () -> Unit = {},
    val onFullscreen: (on: Boolean) -> Unit = {},
    val onSnapshot: () -> Unit = {},
    /** One of the recent clips, in the clip player. */
    val onOpenClip: (MediaFile) -> Unit = {},
    /** History, narrowed to this camera: the rest of its clips. */
    val onSeeAllClips: () -> Unit = {},
    val onRetryClips: () -> Unit = {},
    /** Talk: the finger went down on it. */
    val onTalkPressed: () -> Unit = {},
    /** Talk: the finger went up, or slid off. */
    val onTalkReleased: () -> Unit = {},
    /** Talk activated without a press (TalkBack's double tap): starts or stops talking. */
    val onTalkToggled: () -> Unit = {},
    /** This app's page in Android's settings, where the microphone can be allowed. */
    val onOpenAppSettings: () -> Unit = {},
) {
    /** What Talk does here, for core:ui's [org.petkitlocal.core.ui.talk.TalkControls]. */
    val talk: TalkCallbacks get() = TalkCallbacks(onTalkPressed, onTalkReleased, onTalkToggled, onOpenAppSettings)
}

object LiveTestTags {
    const val SCREEN = "live_screen"
    const val VIDEO = "live_video"

    /** "Live" while frames are shown. */
    const val BADGE = LiveVideoTestTags.BADGE

    /** "HLS" while on the fallback. */
    const val TRANSPORT = LiveVideoTestTags.TRANSPORT
    const val STATUS = LiveVideoTestTags.STATUS

    /** Talk, held to speak through the camera. */
    const val TALK = TalkTestTags.TALK

    /** Under Talk while talking: the red dot and what is happening. */
    const val TALK_INDICATOR = TalkTestTags.INDICATOR

    /** Under Talk: what was said last (why it cannot talk, the microphone, an error). */
    const val TALK_NOTICE = TalkTestTags.NOTICE

    /** Under Talk: why the server says the camera cannot talk now. */
    const val TALK_REASON = TalkTestTags.REASON
}

@Composable
internal fun LiveRoute(
    onBack: () -> Unit,
    onOpenClip: (MediaFile) -> Unit,
    onOpenHistory: (deviceId: Long) -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onMicrophoneResult(it)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LiveEffect.RequestMicrophone -> askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    // Playing (and talking) only while the screen is visible: no player and no socket in the background.
    LifecycleStartEffect(viewModel) {
        viewModel.onVisible()
        onStopOrDispose { viewModel.onHidden() }
    }
    val events = remember(viewModel) {
        LivePlayerEvents(viewModel::onPlaying, viewModel::onPlayerFailed)
    }
    // Not lifecycle-aware: the session's stop at ON_STOP must still reach the player (rememberLivePlayer).
    val playback by viewModel.playback.collectAsState()
    val player = rememberLivePlayer(playback, viewModel.playerFactory, events)
    LaunchedEffect(player, uiState.soundMuted) { player?.applyMuted(uiState.soundMuted) }
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
            onOpenClip = onOpenClip,
            onSeeAllClips = { onOpenHistory(viewModel.deviceId) },
            onRetryClips = viewModel::onRefreshClips,
            onTalkPressed = viewModel::onTalkPressed,
            onTalkReleased = viewModel::onTalkReleased,
            onTalkToggled = viewModel::onTalkToggled,
            onOpenAppSettings = { openAppSettings(context) },
        ),
        aspectRatio = aspectRatioOf(videoSize),
    ) { modifier -> LiveVideoSurface(player, onView = { surface = it }, modifier = modifier) }
}

/**
 * A camera's live video (SPEC.md §5.7), always dark (docs/DESIGN.md): the picture full width
 * with its badge (live, and HLS on the fallback) and what the session is doing over
 * it, then sound, snapshot and full screen, Talk (held to speak through the camera), and
 * the camera's recent clips, each opening the clip player (which stops the live video). Full
 * screen, the picture takes the whole landscape window with its controls over it.
 *
 * [video] draws the picture in the modifier it is given; the route passes the player's surface.
 */
@Composable
internal fun LiveScreen(
    uiState: LiveUiState,
    callbacks: LiveCallbacks,
    modifier: Modifier = Modifier,
    aspectRatio: Float = DEFAULT_LIVE_ASPECT,
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
            if (!fullscreen && uiState.availability == LiveAvailability.Camera) {
                LiveControls(uiState, callbacks)
                RecentClipsCard(
                    state = uiState.clips,
                    onOpenClip = callbacks.onOpenClip,
                    onSeeAll = callbacks.onSeeAllClips,
                    onRetry = callbacks.onRetryClips,
                    modifier = Modifier.padding(
                        start = PkTheme.spacing.gutter,
                        end = PkTheme.spacing.gutter,
                        bottom = PkTheme.spacing.gutter,
                    ),
                )
            }
        }
    }
}

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
            .background(LivePicture),
    ) {
        if (uiState.availability == LiveAvailability.Camera) {
            video(Modifier.aspectRatio(aspectRatio).testTag(LiveTestTags.VIDEO))
        }
        SessionOverlay(uiState, callbacks.onRetry)
        LiveBadges(
            uiState.session,
            Modifier
                .align(Alignment.TopStart)
                .then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier)
                .padding(PkTheme.spacing.s),
        )
        if (fullscreen) FullscreenControls(uiState, callbacks)
    }
}

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

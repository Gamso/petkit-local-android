package org.petkitlocal.feature.history

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.ui.formatDurationMinSec

/*
 * Full-screen media, over the detail sheet. Both are always dark, like the live video screen
 * (docs/DESIGN.md): the picture is what matters. Both fetch through the app's own HTTP stack,
 * with the bearer header; no URL carries a token.
 */

private val Scrim = Color.Black
private val OnScrim = Color.White

/** The event's photos, swiped through, each zoomable (pinch, or a double tap). */
@Composable
internal fun PhotoViewer(state: MediaViewerState.Photos, onClose: () -> Unit) {
    FullScreen(onClose, Modifier.testTag(HistoryTestTags.PHOTO_VIEWER)) {
        val pager = rememberPagerState(initialPage = state.index) { state.photos.size }
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(state.photos[page])
        }
        val photo = state.photos.getOrNull(pager.currentPage)
        TopBar(
            title = buildString {
                photo?.let { append(mediaKindName(it.kind)) }
                if (state.photos.size > 1) {
                    append(" · ")
                    append(stringResource(R.string.history_photo_position, pager.currentPage + 1, state.photos.size))
                }
            },
            onClose = onClose,
        )
    }
}

@Composable
private fun ZoomableImage(photo: MediaFile) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    AsyncImage(
        model = photo.url,
        contentDescription = mediaKindName(photo.kind),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                        offset = Offset.Zero
                    },
                )
            }
            // Panning only once zoomed in, so a swipe at 1x still turns the page.
            .transformable(transform, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * A clip, played in-app and seekable (SPEC.md §5.4). [factory] builds a Media3 player on the
 * app's authenticated client; a seek is a `Range` request. The player lives as long as this
 * screen, and pauses when the app goes to the background.
 */
@OptIn(UnstableApi::class) // PlayerSurface is still marked unstable in Media3.
@Composable
internal fun ClipPlayer(clip: MediaFile, factory: ClipPlayerFactory?, onClose: () -> Unit) {
    FullScreen(onClose, Modifier.testTag(HistoryTestTags.CLIP_PLAYER)) {
        val context = LocalContext.current
        val player = remember(factory, clip.url) {
            factory?.create(context)?.apply {
                setMediaItem(MediaItem.fromUri(clip.url))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(player) { onDispose { player?.release() } }
        if (player != null) {
            val state = rememberClipState(player)
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
            PlayerSurface(
                player = player,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(state.aspectRatio),
            )
            PlayerControls(player, state, Modifier.align(Alignment.BottomCenter))
        }
        TopBar(title = mediaKindName(clip.kind), onClose = onClose)
    }
}

/** What the controls show of a player, read on the main thread the player belongs to. */
@Stable
private class ClipState {
    var playing by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var durationMs by mutableLongStateOf(0L)
    var positionMs by mutableLongStateOf(0L)
    var aspectRatio by mutableFloatStateOf(DEFAULT_ASPECT)

    fun read(player: Player) {
        playing = player.isPlaying
        ended = player.playbackState == Player.STATE_ENDED
        failed = player.playerError != null
        durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0L
        positionMs = player.currentPosition.coerceIn(0, durationMs)
        val size = player.videoSize
        if (size.width > 0 && size.height > 0) {
            aspectRatio = size.width * size.pixelWidthHeightRatio / size.height
        }
    }
}

/**
 * The player's state, from its listener (added and removed as the composition applies) and a
 * position tick, both on the main thread: a Media3 player may only be touched on its own thread,
 * which Media3's own Compose state holders leave to the composition's effect dispatcher.
 */
@Composable
private fun rememberClipState(player: Player): ClipState {
    val state = remember(player) { ClipState() }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = state.read(player)
        }
        player.addListener(listener)
        state.read(player)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        withContext(Dispatchers.Main.immediate) {
            while (true) {
                state.read(player)
                delay(TICK_MS)
            }
        }
    }
    return state
}

private const val DEFAULT_ASPECT = 16f / 9f

@Composable
private fun PlayerControls(player: Player, state: ClipState, modifier: Modifier = Modifier) {
    var seeking by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs
    val position = seeking ?: state.positionMs.toFloat()
    val seekLabel = stringResource(R.string.history_clip_position)
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .background(Scrim.copy(alpha = CONTROLS_ALPHA))
            .safeDrawingPadding()
            .padding(horizontal = PkTheme.spacing.gutter, vertical = PkTheme.spacing.s),
    ) {
        if (state.failed) {
            Text(
                stringResource(R.string.history_clip_failed),
                style = PkTheme.typography.bodyMedium,
                color = OnScrim,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Slider(
            value = position,
            onValueChange = { seeking = it },
            onValueChangeFinished = {
                seeking?.let { player.seekTo(it.toLong()) }
                seeking = null
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = OnScrim,
                activeTrackColor = OnScrim,
                inactiveTrackColor = OnScrim.copy(alpha = INACTIVE_ALPHA),
            ),
            modifier = Modifier.semantics { contentDescription = seekLabel },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayPauseButton(player, state)
            Spacer(Modifier.width(PkTheme.spacing.s))
            Text(
                "${formatDurationMinSec((position / MS).toInt())} / ${formatDurationMinSec((duration / MS).toInt())}",
                style = PkTheme.typography.labelLarge,
                color = OnScrim,
            )
        }
    }
}

/** Pause while playing, play again from the start once ended, play otherwise. */
@Composable
private fun PlayPauseButton(player: Player, state: ClipState) {
    val (icon, label) = when {
        state.playing -> PkIcons.Pause to R.string.history_pause
        state.ended -> PkIcons.Replay to R.string.history_replay
        else -> PkIcons.PlayArrow to R.string.history_play
    }
    PkIconButton(
        icon = icon,
        contentDescription = stringResource(label),
        onClick = {
            when {
                state.playing -> player.pause()

                state.ended -> {
                    player.seekTo(0)
                    player.play()
                }

                else -> player.play()
            }
        },
    )
}

private const val TICK_MS = 250L
private const val MS = 1000f
private const val CONTROLS_ALPHA = 0.6f
private const val INACTIVE_ALPHA = 0.35f

@Composable
private fun FullScreen(onClose: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Scrim),
            content = content,
        )
    }
}

@Composable
private fun BoxScope.TopBar(title: String, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Scrim.copy(alpha = CONTROLS_ALPHA))
            .safeDrawingPadding()
            .padding(horizontal = PkTheme.spacing.xs, vertical = PkTheme.spacing.xs),
    ) {
        PkIconButton(PkIcons.Close, stringResource(R.string.history_close), onClose)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(title, style = PkTheme.typography.titleMedium, color = OnScrim, modifier = Modifier.weight(1f))
    }
}

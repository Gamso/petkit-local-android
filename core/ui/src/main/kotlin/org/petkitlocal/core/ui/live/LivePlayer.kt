package org.petkitlocal.core.ui.live

import android.graphics.Bitmap
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport

/** The player to have now: a new one for every attempt of the live session. */
data class LivePlayback(val deviceId: Long, val transport: LiveTransport, val attempt: Int)

/** The player [deviceId]'s session wants now, or none: idle, waiting or stopped. */
fun LiveSession.playbackOf(deviceId: Long): LivePlayback? = LivePlayback(deviceId, transport, attempt).takeIf {
    phase == LivePhase.Starting || phase == LivePhase.Playing
}

/** What a screen hears from the player of one attempt. */
class LivePlayerEvents(
    val onPlaying: (attempt: Int) -> Unit,
    val onFailed: (attempt: Int, failure: LiveFailure) -> Unit,
)

/**
 * The player of [playback]: a new one for each attempt, built by [factory], prepared and playing,
 * released when the attempt is over or the caller leaves the composition (which closes its socket
 * and frees its viewer slot on the server). None while there is nothing to play. Its first frame
 * and its failure are reported for the attempt it belongs to, so a late report of an older player
 * changes nothing.
 *
 * [playback] must follow the session even in the background (collect it with `collectAsState`,
 * not `collectAsStateWithLifecycle`): a lifecycle-aware collection stops at ON_STOP, possibly
 * before the session's own stop reaches it, and the player would then play on behind the screen.
 */
@Composable
fun rememberLivePlayer(playback: LivePlayback?, factory: LivePlayerFactory, events: LivePlayerEvents): Player? {
    val context = LocalContext.current
    val player = remember(playback) {
        playback?.let { factory.create(context, it.deviceId, it.transport) }?.apply {
            playWhenReady = true
            prepare()
        }
    }
    val latest by rememberUpdatedState(events)
    DisposableEffect(player) {
        val attempt = playback?.attempt
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                attempt?.let { latest.onPlaying(it) }
            }

            override fun onPlayerError(error: PlaybackException) {
                attempt?.let { latest.onFailed(it, factory.failureOf(error)) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // A live stream has no end: one that ends was stopped by the server.
                if (playbackState == Player.STATE_ENDED) attempt?.let { latest.onFailed(it, LiveFailure.StreamEnded) }
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }
    return player
}

/**
 * The live picture in a [SurfaceView], which a snapshot copies with `PixelCopy`: the live
 * screen's. [onView] hands the view to whoever takes snapshots, and null once it is gone.
 */
@Composable
fun LiveVideoSurface(player: Player?, onView: (SurfaceView?) -> Unit, modifier: Modifier = Modifier) {
    var view by remember { mutableStateOf<SurfaceView?>(null) }
    AndroidView(
        factory = { context ->
            SurfaceView(context).also {
                view = it
                onView(it)
            }
        },
        onRelease = {
            view = null
            onView(null)
        },
        modifier = modifier,
    )
    DisposableEffect(player, view) {
        val surface = view
        if (player != null && surface != null) player.setVideoSurfaceView(surface)
        onDispose {
            // A released player owes the surface nothing more.
            if (player != null && surface != null && player.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
                player.clearVideoSurfaceView(surface)
            }
        }
    }
}

/**
 * The live picture in a [TextureView]: a camera page's live card, and its full screen. Unlike a
 * [SurfaceView], it is drawn with the page, so it scrolls with it and takes the card's rounded
 * corners; [textureJpeg] copies its frame for a snapshot. [onView] hands the view over, and null
 * once it is gone.
 */
@Composable
fun LiveVideoTexture(player: Player?, modifier: Modifier = Modifier, onView: (TextureView?) -> Unit = {}) {
    var view by remember { mutableStateOf<TextureView?>(null) }
    AndroidView(
        factory = { context ->
            TextureView(context).also {
                view = it
                onView(it)
            }
        },
        onRelease = {
            view = null
            onView(null)
        },
        modifier = modifier,
    )
    DisposableEffect(player, view) {
        val texture = view
        if (player != null && texture != null) player.setVideoTextureView(texture)
        onDispose {
            if (player != null && texture != null && player.isCommandAvailable(Player.COMMAND_SET_VIDEO_SURFACE)) {
                player.clearVideoTextureView(texture)
            }
        }
    }
}

/** The size of [player]'s video, followed as it changes; unknown (0 x 0) before the first frame. */
@Composable
fun rememberVideoSize(player: Player?): VideoSize {
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

/** The aspect ratio of [size], or 16:9 before the first frame. */
fun aspectRatioOf(size: VideoSize): Float = if (size.width > 0 && size.height > 0) {
    size.width * size.pixelWidthHeightRatio / size.height
} else {
    DEFAULT_LIVE_ASPECT
}

/** A camera's picture before its player says otherwise. */
const val DEFAULT_LIVE_ASPECT = 16f / 9f

/** Muted is volume 0; the player keeps its own volume otherwise. */
fun Player.applyMuted(muted: Boolean) {
    if (isCommandAvailable(Player.COMMAND_SET_VOLUME)) volume = if (muted) 0f else 1f
}

/** The frame [view] shows, as a JPEG, or null when it has none yet. */
suspend fun textureJpeg(view: TextureView): ByteArray? {
    if (!view.isAvailable) return null
    val bitmap = view.bitmap ?: return null
    return withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, SNAPSHOT_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}

private const val SNAPSHOT_QUALITY = 92

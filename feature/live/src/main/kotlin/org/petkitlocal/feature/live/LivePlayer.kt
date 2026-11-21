package org.petkitlocal.feature.live

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
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
import androidx.core.graphics.createBitmap
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.petkitlocal.core.domain.video.LiveFailure

/** What the screen hears from the player of one attempt. */
internal class LivePlayerEvents(
    val onPlaying: (attempt: Int) -> Unit,
    val onFailed: (attempt: Int, failure: LiveFailure) -> Unit,
)

/**
 * The player of [playback]: a new one for each attempt, built by [factory], prepared and playing,
 * released when the attempt is over or the screen leaves (which closes its socket). None while
 * there is nothing to play. Its first frame and its failure are reported for the attempt it
 * belongs to, so a late report of an older player changes nothing.
 */
@Composable
internal fun rememberLivePlayer(
    playback: LivePlayback?,
    factory: LivePlayerFactory,
    events: LivePlayerEvents,
): Player? {
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
 * The live picture: a [SurfaceView], which a snapshot copies with [PixelCopy]. [onView] hands
 * the view to whoever takes snapshots, and null once it is gone.
 */
@Composable
internal fun LiveVideoSurface(player: Player?, onView: (SurfaceView?) -> Unit, modifier: Modifier = Modifier) {
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

/** The aspect ratio of [size], or 16:9 before the first frame. */
internal fun aspectRatioOf(size: VideoSize): Float = if (size.width > 0 && size.height > 0) {
    size.width * size.pixelWidthHeightRatio / size.height
} else {
    DEFAULT_ASPECT
}

private const val DEFAULT_ASPECT = 16f / 9f

/**
 * The frame [view] shows, as a JPEG at the video's own size ([video], if known), or null when
 * there is none to copy: not laid out yet, no surface, or the copy failed.
 */
internal suspend fun captureJpeg(view: SurfaceView, video: VideoSize): ByteArray? {
    if (view.width == 0 || view.height == 0 || !view.holder.surface.isValid) return null
    val width = video.width.takeIf { it > 0 } ?: view.width
    val height = video.height.takeIf { it > 0 } ?: view.height
    val bitmap = createBitmap(width, height)
    val copied = suspendCancellableCoroutine { continuation ->
        PixelCopy.request(
            view,
            bitmap,
            { result -> continuation.resume(result == PixelCopy.SUCCESS) },
            Handler(Looper.getMainLooper()),
        )
    }
    if (!copied) {
        bitmap.recycle()
        return null
    }
    return withContext(Dispatchers.Default) {
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}

private const val JPEG_QUALITY = 92

/** Muted is volume 0; the player keeps its own volume otherwise. */
internal fun Player.applyMuted(muted: Boolean) {
    if (isCommandAvailable(Player.COMMAND_SET_VOLUME)) volume = if (muted) 0f else 1f
}

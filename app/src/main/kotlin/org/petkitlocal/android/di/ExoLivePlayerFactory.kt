package org.petkitlocal.android.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import dagger.Lazy
import okhttp3.OkHttpClient
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.network.auth.SessionEndListener
import org.petkitlocal.core.network.video.LiveCodecs
import org.petkitlocal.core.network.video.LiveMediaSources
import org.petkitlocal.core.network.video.liveFailureOf
import org.petkitlocal.feature.live.LivePlayerFactory

/**
 * The live screen's players: an ExoPlayer per attempt on core:network's live sources
 * ([LiveMediaSources]: the fMP4 WebSocket, or HLS), with [lowLatencyLoadControl] and the app's
 * renderers ([renderersFactory]). The `mse` codec list is read from the phone's decoders once.
 */
internal class ExoLivePlayerFactory(
    private val client: Lazy<OkHttpClient>,
    private val sessionEnded: Lazy<SessionEndListener>,
) : LivePlayerFactory {
    private val codecs by lazy { LiveCodecs.mse(LiveCodecs.decoderMimeTypes()) }
    private val sources by lazy { LiveMediaSources(client.get(), sessionEnded.get()) { codecs } }

    @OptIn(UnstableApi::class) // setMediaSource.
    override fun create(context: Context, deviceId: Long, transport: LiveTransport): Player =
        ExoPlayer.Builder(context, renderersFactory(context))
            .setLoadControl(lowLatencyLoadControl())
            .build()
            .apply { setMediaSource(sources.create(deviceId, transport)) }

    override fun failureOf(error: PlaybackException): LiveFailure = liveFailureOf(error)
}

/**
 * Starts showing frames after a quarter of a second and keeps at most a few seconds ahead: the
 * point of live video is to be close behind the camera. A WebSocket that sends faster than the
 * player shows (it cannot, as the camera sends in real time, unless playback stalls) fills the
 * socket's pipe until the server closes it with 1013, and the session moves to HLS.
 */
@OptIn(UnstableApi::class)
internal fun lowLatencyLoadControl(): LoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, BUFFER_FOR_PLAYBACK_MS, BUFFER_AFTER_REBUFFER_MS)
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()

private const val MIN_BUFFER_MS = 1_000
private const val MAX_BUFFER_MS = 5_000
private const val BUFFER_FOR_PLAYBACK_MS = 250
private const val BUFFER_AFTER_REBUFFER_MS = 500

package org.petkitlocal.android.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.petkitlocal.core.network.MediaDataSources
import org.petkitlocal.core.network.auth.SessionEndListener
import org.petkitlocal.feature.history.ClipPlayerFactory
import org.petkitlocal.feature.live.LivePlayerFactory

/**
 * The app's video players, both Media3's ExoPlayer on the app's one OkHttp client, so every
 * request carries the bearer header (never a URL) and reaches the current server (SPEC.md §8):
 *
 * - History's clip player, where a seek is a `Range` request (SPEC.md §5.4);
 * - the live player, on the WebSocket or HLS (SPEC.md §5.7, ADR 0005), [ExoLivePlayerFactory].
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @OptIn(UnstableApi::class) // DefaultMediaSourceFactory(DataSource.Factory).
    @Provides
    fun clipPlayerFactory(client: Lazy<OkHttpClient>): ClipPlayerFactory = ClipPlayerFactory { context ->
        ExoPlayer.Builder(context, renderersFactory(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(MediaDataSources.authenticated(client.get())))
            .build()
    }

    @Provides
    fun livePlayerFactory(client: Lazy<OkHttpClient>, sessionEnded: Lazy<SessionEndListener>): LivePlayerFactory =
        ExoLivePlayerFactory(client, sessionEnded)
}

/**
 * Renderers that fall back to the next decoder when one cannot start, trying the emulator's
 * own decoders last ([emulatorCodecsLast]).
 */
@OptIn(UnstableApi::class) // The codec selector.
internal fun renderersFactory(context: Context): DefaultRenderersFactory = DefaultRenderersFactory(context)
    .setEnableDecoderFallback(true)
    .setMediaCodecSelector { mimeType, secure, tunneling ->
        emulatorCodecsLast(MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)) { it.name }
    }

/**
 * [codecs] with the Android emulator's host-backed decoders (`c2.goldfish.*`) after the others,
 * order otherwise kept. On an emulator the goldfish H.264 decoder starts, then fails on the first
 * frames of a clip, where Media3's fallback (which covers a decoder that cannot start) no longer
 * applies; the platform's software decoder plays the same clip. A phone has no goldfish decoder,
 * so its order is unchanged.
 */
internal fun <T> emulatorCodecsLast(codecs: List<T>, name: (T) -> String): List<T> =
    codecs.sortedBy { name(it).contains(EMULATOR_CODEC, ignoreCase = true) }

private const val EMULATOR_CODEC = "goldfish"

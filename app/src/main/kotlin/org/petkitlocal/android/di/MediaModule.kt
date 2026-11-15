package org.petkitlocal.android.di

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
import org.petkitlocal.feature.history.ClipPlayerFactory

/**
 * History's clip player: Media3's ExoPlayer reading through the app's one OkHttp client, so a
 * clip carries the bearer header in its requests (never in its URL) and a seek is a `Range`
 * request (SPEC.md §5.4, §8).
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
    @OptIn(UnstableApi::class) // DefaultMediaSourceFactory(DataSource.Factory), the codec selector.
    @Provides
    fun clipPlayerFactory(client: Lazy<OkHttpClient>): ClipPlayerFactory = ClipPlayerFactory { context ->
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mimeType, secure, tunneling ->
                emulatorCodecsLast(MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)) { it.name }
            }
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(MediaDataSources.authenticated(client.get())))
            .build()
    }
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

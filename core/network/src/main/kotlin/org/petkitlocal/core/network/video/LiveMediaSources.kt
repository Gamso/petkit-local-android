package org.petkitlocal.core.network.video

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.network.MediaDataSources
import org.petkitlocal.core.network.PetkitHttpClient
import org.petkitlocal.core.network.auth.SessionEndListener

/** The live routes, built against the placeholder base the server-URL interceptor rewrites. */
object LiveVideoRoutes {
    /** `GET /devices/{id}/live`: the WebSocket. */
    fun socket(deviceId: Long): HttpUrl = PetkitHttpClient.PLACEHOLDER_BASE_URL.resolve("devices/$deviceId/live")!!

    /** `.../live/hls/playlist.m3u8`, without `id`: it opens an HLS view. */
    fun hlsPlaylist(deviceId: Long): HttpUrl =
        PetkitHttpClient.PLACEHOLDER_BASE_URL.resolve("devices/$deviceId/live/hls/playlist.m3u8")!!
}

/**
 * The Media3 source of a camera's live video, per transport (ADR 0005), both on the app's one
 * OkHttp client and its bearer header:
 *
 * - [LiveTransport.WebSocket]: [LiveSocketDataSource] read by the fragmented MP4 extractor
 *   alone, its timestamps moved to start at zero, since a viewer joining a running stream gets
 *   fragments whose decode time is however long the stream has run;
 * - [LiveTransport.Hls]: Media3's HLS source reading the playlists and segments through
 *   [MediaDataSources.authenticated], every relative URI resolved on the server.
 *
 * Neither retries a failed load: a reopened socket would start a new stream mid-file, and the
 * live session decides what comes next (LiveSession), so every failure reaches it at once.
 */
@OptIn(UnstableApi::class)
class LiveMediaSources(
    private val client: OkHttpClient,
    private val sessionEnded: SessionEndListener,
    /** The `mse` codec list (LiveCodecs), asked for at each start. */
    private val codecs: () -> String,
) {
    fun create(deviceId: Long, transport: LiveTransport): MediaSource = when (transport) {
        LiveTransport.WebSocket -> ProgressiveMediaSource.Factory(
            DataSource.Factory { LiveSocketDataSource(client, codecs(), sessionEnded) },
            ExtractorsFactory { arrayOf<Extractor>(fragmentedMp4Extractor()) },
        )
            .setLoadErrorHandlingPolicy(NoRetries)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(LiveVideoRoutes.socket(deviceId).toString())
                    .setMimeType(MimeTypes.VIDEO_MP4)
                    .build(),
            )

        LiveTransport.Hls -> HlsMediaSource.Factory(MediaDataSources.authenticated(client))
            .setLoadErrorHandlingPolicy(NoRetries)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(LiveVideoRoutes.hlsPlaylist(deviceId).toString())
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build(),
            )
    }

    private fun fragmentedMp4Extractor() = FragmentedMp4Extractor(
        SubtitleParser.Factory.UNSUPPORTED,
        0,
        TimestampAdjuster(0),
        null,
        emptyList(),
        null,
    )

    /** Every failed load is final: the live session retries, with a new player. */
    private object NoRetries : LoadErrorHandlingPolicy {
        override fun getFallbackSelectionFor(
            fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
            loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
        ): LoadErrorHandlingPolicy.FallbackSelection? = null

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long = C.TIME_UNSET

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 0
    }
}

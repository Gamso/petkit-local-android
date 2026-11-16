package org.petkitlocal.core.network.video

import android.media.MediaCodecList
import androidx.media3.common.MimeTypes

/**
 * The codecs the live socket's `mse` request offers (api/openapi.yaml): the MSE strings of the
 * formats this phone has a decoder for, which go2rtc matches against the camera's. H.264 is
 * offered even with no decoder found, so a request never goes out empty: every Android phone
 * decodes it.
 */
object LiveCodecs {
    private val H264 = listOf("avc1.640029", "avc1.64002A", "avc1.640033")
    private const val H265 = "hvc1.1.6.L153.B0"
    private val AAC = listOf("mp4a.40.2", "mp4a.40.5")
    private const val OPUS = "opus"

    /** The comma-separated list for decoders of [decoderMimeTypes] (`video/avc`, `audio/opus`...). */
    fun mse(decoderMimeTypes: Set<String>): String = buildList {
        addAll(H264)
        if (MimeTypes.VIDEO_H265 in decoderMimeTypes) add(H265)
        if (MimeTypes.AUDIO_AAC in decoderMimeTypes) addAll(AAC)
        if (MimeTypes.AUDIO_OPUS in decoderMimeTypes) add(OPUS)
    }.joinToString(",")

    /** The MIME types this phone's decoders take, from the platform's codec list. */
    fun decoderMimeTypes(): Set<String> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filterNot { it.isEncoder }
        .flatMapTo(HashSet()) { info -> info.supportedTypes.map { it.lowercase() } }
}

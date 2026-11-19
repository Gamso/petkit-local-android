package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The text messages of `GET /devices/{id}/talk` (api/openapi.yaml, its description: the document
 * names them in prose, with no schema). The binary messages are the microphone, raw PCM in
 * [TalkMessageDto.Ready]'s format.
 */
@Serializable
sealed interface TalkMessageDto {
    /** Server to client, once: the camera accepted; send audio in this format from now on. */
    @Serializable
    @SerialName("ready")
    data class Ready(
        /** `s16le`. */
        @SerialName("format") val format: String? = null,
        @SerialName("sample_rate") val sampleRate: Int? = null,
        @SerialName("channels") val channels: Int? = null,
    ) : TalkMessageDto

    /** Client to server: end the talk; the audio already sent is played to its end. */
    @Serializable
    @SerialName("stop")
    data object Stop : TalkMessageDto

    /** Server to client: the answer to [Stop], before the server closes with 1000. */
    @Serializable
    @SerialName("stopped")
    data object Stopped : TalkMessageDto

    /** Server to client: the talk could not start or go on; the server then closes with 4000. */
    @Serializable
    @SerialName("error")
    data class Error(
        /** One of [TalkErrorCode]; act on it. */
        @SerialName("code") val code: String,
        /** For a person. */
        @SerialName("message") val message: String = "",
    ) : TalkMessageDto
}

/** Values of [TalkMessageDto.Error.code]. */
object TalkErrorCode {
    const val NOT_A_CAMERA = "not_a_camera"
    const val NEEDS_PATCHER = "needs_patcher"
    const val DEVICE_UNREACHABLE = "device_unreachable"
    const val BUSY = "busy"
    const val TRANSCODER_FAILED = "transcoder_failed"
    const val IDLE_TIMEOUT = "idle_timeout"
}

package org.petkitlocal.core.data.talk

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/** The phone's microphone, in the talk route's format. */
interface Microphone {
    /**
     * Records while collected, in chunks of raw PCM: signed 16-bit little-endian, 16 kHz, mono,
     * exactly what `/devices/{id}/talk` takes, with no encoder. The microphone is released when the
     * collection ends. Fails with [MicrophoneUnavailableException] when it cannot be opened.
     */
    fun record(): Flow<ByteArray>
}

/** The microphone could not be opened or stopped delivering: no permission, another app holds it, none. */
class MicrophoneUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * [Microphone] on Android's [AudioRecord] at 16 kHz mono 16-bit, the format the server's ffmpeg
 * reads (`-f s16le -ar 16000 -ch_layout mono`), 40 ms per chunk. Its blocking reads run on the IO
 * dispatcher. The screen has checked `RECORD_AUDIO`; without it the recorder does not initialise,
 * and this fails like any other unavailable microphone.
 */
class AudioRecordMicrophone @Inject constructor() : Microphone {
    @SuppressLint("MissingPermission") // Checked before talking (MicrophoneAccess).
    override fun record(): Flow<ByteArray> = flow {
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minimum <= 0) throw MicrophoneUnavailableException("no recorder for 16 kHz mono PCM")
        val recorder = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, maxOf(minimum, CHUNK_BYTES * 4))
        } catch (e: IllegalArgumentException) {
            throw MicrophoneUnavailableException("the recorder refused the format", e)
        } catch (e: SecurityException) {
            throw MicrophoneUnavailableException("no permission to record", e)
        }
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw MicrophoneUnavailableException("the recorder did not start")
            }
            try {
                recorder.startRecording()
            } catch (e: IllegalStateException) {
                throw MicrophoneUnavailableException("the recorder could not start", e)
            }
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw MicrophoneUnavailableException("the microphone is in use")
            }
            val buffer = ByteArray(CHUNK_BYTES)
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read < 0) throw MicrophoneUnavailableException("the microphone stopped ($read)")
                if (read > 0) emit(buffer.copyOf(read))
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 40 ms: 640 samples of 2 bytes. */
        const val CHUNK_BYTES = 1280
    }
}

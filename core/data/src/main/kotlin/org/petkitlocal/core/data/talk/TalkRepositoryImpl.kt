package org.petkitlocal.core.data.talk

import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.talk.TalkEvent
import org.petkitlocal.core.domain.talk.TalkFailure
import org.petkitlocal.core.domain.talk.TalkRepository
import org.petkitlocal.core.domain.talk.TalkSession
import org.petkitlocal.core.network.TalkConnection
import org.petkitlocal.core.network.TalkSocket
import org.petkitlocal.core.network.TalkSocketEvent

/**
 * A talk: the socket first, then the microphone only once the camera said `ready` (nothing is
 * recorded for a talk that may yet be refused), each chunk sent as it is recorded. A stop ends
 * the recording, asks the server to finish, and gives it [stopTimeout] to play what it received
 * and close; past that the socket is closed anyway. However the talk ends, the microphone is
 * released and the socket closed by the time [TalkEvent.Ended] arrives.
 */
class TalkRepositoryImpl internal constructor(
    private val socket: TalkSocket,
    private val microphone: Microphone,
    private val stopTimeout: Duration,
) : TalkRepository {
    @Inject constructor(socket: TalkSocket, microphone: Microphone) : this(socket, microphone, STOP_TIMEOUT)

    override fun open(deviceId: Long): TalkSession = Session(deviceId)

    private inner class Session(private val deviceId: Long) : TalkSession {
        private val stopRequested = MutableStateFlow(false)

        override fun stop() {
            stopRequested.value = true
        }

        override val events: Flow<TalkEvent> = channelFlow {
            val connection = socket.open(deviceId)
            var recording: Job? = null
            val stopper = launch {
                stopRequested.first { it }
                val wasRecording = recording
                if (wasRecording == null) {
                    // Not accepted yet: nothing to play, nothing to wait for.
                    connection.close()
                } else {
                    wasRecording.cancelAndJoin()
                    connection.stop()
                    delay(stopTimeout)
                    connection.close()
                }
            }
            try {
                connection.events.collect { event ->
                    when (event) {
                        TalkSocketEvent.Ready -> if (!stopRequested.value && recording == null) {
                            send(TalkEvent.Talking)
                            recording = launch { record(connection) }
                        }

                        TalkSocketEvent.Stopped -> Unit

                        is TalkSocketEvent.Ended -> send(TalkEvent.Ended(event.failure))
                    }
                }
            } finally {
                recording?.cancel()
                stopper.cancel()
                connection.close()
            }
        }

        private suspend fun record(connection: TalkConnection) {
            try {
                microphone.record().collect { connection.send(it) }
            } catch (_: MicrophoneUnavailableException) {
                connection.close(TalkFailure.MicrophoneUnavailable)
            }
        }
    }

    companion object {
        /** How long the server gets to play what it received and close after a stop. */
        val STOP_TIMEOUT: Duration = 10.seconds
    }
}

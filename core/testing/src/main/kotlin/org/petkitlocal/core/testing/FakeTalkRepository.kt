package org.petkitlocal.core.testing

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.petkitlocal.core.domain.talk.MicrophoneAccess
import org.petkitlocal.core.domain.talk.TalkEvent
import org.petkitlocal.core.domain.talk.TalkFailure
import org.petkitlocal.core.domain.talk.TalkRepository
import org.petkitlocal.core.domain.talk.TalkSession

/** A [TalkRepository] whose talks a test drives: it says when the camera is ready and how each ends. */
class FakeTalkRepository : TalkRepository {
    val sessions = mutableListOf<FakeTalkSession>()

    /** The talk opened last. */
    val last: FakeTalkSession get() = sessions.last()

    override fun open(deviceId: Long): TalkSession = FakeTalkSession(deviceId).also { sessions += it }
}

/** One talk: [ready], [end], and what the app did to it ([stopRequested], [collecting], [cancelled]). */
class FakeTalkSession(val deviceId: Long) : TalkSession {
    private val channel = Channel<TalkEvent>(Channel.UNLIMITED)

    /** The app asked for a clean end. */
    var stopRequested = false
        private set

    /** Its events are being collected: the socket would be open. */
    var collecting = false
        private set

    /** The collection was cancelled before the talk ended: the socket was closed at once. */
    var cancelled = false
        private set

    override val events: Flow<TalkEvent> = flow {
        collecting = true
        var ended = false
        try {
            for (event in channel) {
                emit(event)
                if (event is TalkEvent.Ended) {
                    ended = true
                    return@flow
                }
            }
        } finally {
            collecting = false
            if (!ended) cancelled = true
        }
    }

    /** The camera accepted. */
    fun ready() {
        channel.trySend(TalkEvent.Talking)
    }

    /** The talk ends, with [failure] or cleanly. */
    fun end(failure: TalkFailure? = null) {
        channel.trySend(TalkEvent.Ended(failure))
    }

    override fun stop() {
        stopRequested = true
    }
}

/** A [MicrophoneAccess] a test grants or not. */
class FakeMicrophoneAccess(var granted: Boolean = true) : MicrophoneAccess {
    override fun granted(): Boolean = granted
}

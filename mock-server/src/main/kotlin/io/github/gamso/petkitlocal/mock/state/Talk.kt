package io.github.gamso.petkitlocal.mock.state

import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicLong

/** What a talk socket's writer takes from its queue: a text message, or the close after it. */
sealed interface TalkOut {
    data class Text(
        val text: String,
    ) : TalkOut

    data class Close(
        val code: Short,
        val reason: String,
    ) : TalkOut
}

/** Why a talk could not start or go on: the `code` and `message` of an `error` message. */
data class TalkRefusal(
    val code: String,
    val message: String,
)

/**
 * One open `/devices/{id}/talk` socket. Messages and the close go through [outbox], so the
 * control API can end a talk from another thread.
 */
class Talker(
    val session: AppSession,
    val deviceId: Int,
) {
    val outbox = Channel<TalkOut>(Channel.UNLIMITED)

    /** Audio bytes this socket received. */
    val received = AtomicLong(0)

    @Volatile var closeCode: Short? = null
        private set

    fun text(text: String) {
        if (closeCode == null) outbox.trySend(TalkOut.Text(text))
    }

    /** Sends [refusal] as an `error` message, then closes with 4000. */
    fun fail(refusal: TalkRefusal) =
        synchronized(this) {
            text(TalkHub.errorMessage(refusal))
            end(CLOSE_ERROR, refusal.code)
        }

    /** Hands the writer the close; what is queued before it is still sent. */
    fun end(
        code: Short,
        reason: String,
    ) = synchronized(this) {
        if (closeCode != null) return@synchronized
        closeCode = code
        outbox.trySend(TalkOut.Close(code, reason))
    }

    companion object {
        const val CLOSE_NORMAL: Short = 1000
        const val CLOSE_GOING_AWAY: Short = 1001
        const val CLOSE_TOO_BIG: Short = 1009
        const val CLOSE_ERROR: Short = 4000
        const val CLOSE_SESSION_ENDED: Short = 4401
    }
}

/**
 * Talking through the cameras, as petkit-local's `media/talk.py` does it with one talk slot per
 * device: a second socket on a device that is talking is refused with `busy`, and the first goes
 * on. There is no camera behind it; what the mock keeps is how many audio bytes arrived
 * ([bytes], `GET /mock`'s `talk_bytes`), so a test can see a phone's microphone reached it.
 * Thread-safe on its own lock.
 */
class TalkHub(
    /** A talk with no audio for this long ends with `idle_timeout`, as the server's 15 s. */
    val idleMillis: Long = IDLE_MILLIS,
) {
    private val lock = Any()
    private val talkers = LinkedHashSet<Talker>()

    /** `error`s the next opens answer instead of `ready` (control API). */
    private val pendingRefusals = ArrayDeque<TalkRefusal>()
    private val total = AtomicLong(0)

    val sockets: Int get() = synchronized(lock) { talkers.size }

    /** Audio bytes received since the last reset, every socket together. */
    val bytes: Long get() = total.get()

    /**
     * Takes [deviceId]'s talk slot for [talker], or the refusal: one the control API queued, then
     * [availability] (the device's `talk`) when it is not `available`, then `busy`.
     */
    fun open(
        talker: Talker,
        availability: String,
    ): TalkRefusal? =
        synchronized(lock) {
            pendingRefusals.removeFirstOrNull()?.let { return@synchronized it }
            refusalOf(availability)?.let { return@synchronized it }
            if (talkers.any { it.deviceId == talker.deviceId }) {
                return@synchronized TalkRefusal(BUSY, "someone is already talking through this device; try again when they stop")
            }
            talkers += talker
            null
        }

    /** [talker] received [count] bytes of audio. */
    fun received(
        talker: Talker,
        count: Int,
    ) {
        talker.received.addAndGet(count.toLong())
        total.addAndGet(count.toLong())
    }

    fun remove(talker: Talker) {
        synchronized(lock) { talkers.remove(talker) }
    }

    /** The next [times] opens answer [refusal] instead of `ready` (control API). */
    fun failNextOpens(
        times: Int,
        refusal: TalkRefusal,
    ) = synchronized(lock) { repeat(times) { pendingRefusals.addLast(refusal) } }

    /** Ends every open talk with [refusal]; returns how many. */
    fun failAll(refusal: TalkRefusal): Int =
        synchronized(lock) {
            talkers.forEach { it.fail(refusal) }
            talkers.size
        }

    /** Closes the talks [predicate] picks with [code]; returns how many. */
    fun end(
        code: Short,
        reason: String,
        predicate: (Talker) -> Boolean = { true },
    ): Int =
        synchronized(lock) {
            val matching = talkers.filter(predicate)
            matching.forEach { it.end(code, reason) }
            matching.size
        }

    /** Sessions that ended: their talks close with 4401. */
    fun endSessions(ended: (AppSession) -> Boolean) {
        end(Talker.CLOSE_SESSION_ENDED, "session ended") { ended(it.session) }
    }

    /** Back to no talks, no queued refusals and no bytes counted. */
    fun reset() =
        synchronized(lock) {
            pendingRefusals.clear()
            talkers.toList().forEach { it.end(Talker.CLOSE_GOING_AWAY, "mock reset") }
            total.set(0)
        }

    companion object {
        const val IDLE_MILLIS = 15_000L

        /** The largest binary frame taken: two seconds of audio. A bigger one closes with 1009. */
        const val MAX_FRAME = 64 * 1024

        const val NOT_A_CAMERA = "not_a_camera"
        const val BUSY = "busy"
        const val TRANSCODER_FAILED = "transcoder_failed"
        const val IDLE_TIMEOUT = "idle_timeout"

        /** Every `code` an `error` message may carry (api/openapi.yaml). */
        val CODES = setOf(NOT_A_CAMERA, TALK_NEEDS_PATCHER, TALK_DEVICE_UNREACHABLE, BUSY, TRANSCODER_FAILED, IDLE_TIMEOUT)

        /** What a device whose `talk` is [availability] refuses a talk with; null when it takes one. */
        fun refusalOf(availability: String): TalkRefusal? =
            when (availability) {
                TALK_AVAILABLE -> {
                    null
                }

                TALK_NEEDS_PATCHER -> {
                    TalkRefusal(
                        TALK_NEEDS_PATCHER,
                        "the Two-Way Talk patcher is not applied to this device (petkit-local panel, Patchers tab)",
                    )
                }

                TALK_DEVICE_UNREACHABLE -> {
                    TalkRefusal(TALK_DEVICE_UNREACHABLE, "the device has not reported its network address yet; wait for it to come online")
                }

                else -> {
                    TalkRefusal(NOT_A_CAMERA, "this device has no camera speaker to talk through")
                }
            }

        /** The default `message` of an `error` [code] the control API sends. */
        fun defaultMessage(code: String): String =
            when (code) {
                BUSY -> "someone is already talking through this device; try again when they stop"
                TRANSCODER_FAILED -> "ffmpeg exited: forced by the mock's control API"
                IDLE_TIMEOUT -> "no audio for 15 seconds"
                else -> refusalOf(if (code == NOT_A_CAMERA) TALK_UNSUPPORTED else code)?.message ?: "forced by the mock's control API"
            }

        fun errorMessage(refusal: TalkRefusal): String =
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("error"),
                    "code" to JsonPrimitive(refusal.code),
                    "message" to JsonPrimitive(refusal.message),
                ),
            ).toString()

        /** `ready`, restating the format the phone must send. */
        val READY: String =
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("ready"),
                    "format" to JsonPrimitive("s16le"),
                    "sample_rate" to JsonPrimitive(16000),
                    "channels" to JsonPrimitive(1),
                ),
            ).toString()

        val STOPPED: String = JsonObject(mapOf("type" to JsonPrimitive("stopped"))).toString()
    }
}

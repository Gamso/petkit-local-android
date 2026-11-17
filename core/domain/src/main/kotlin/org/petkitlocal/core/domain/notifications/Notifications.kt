package org.petkitlocal.core.domain.notifications

import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import org.petkitlocal.core.domain.DomainError

/*
 * Notifications over the app's own connection to petkit-local (SPEC.md decision 4, ADR 0007): a
 * foreground service holds the server's notification socket while the phone is signed in and
 * notifications are on, and writes each notification itself, in the phone's language.
 */

/**
 * One notification the server sent (`PushMessage`, with the socket's `id`): stable keys only,
 * never text. The app writes the title and the text from them.
 *
 * @property id monotonic; the highest one received is passed back on reconnection.
 * @property replay sent as catch-up, for something that happened while the phone was away.
 * @property kind a `NotificationKind` ([org.petkitlocal.core.domain.settings.NotificationKinds]),
 *   or one a newer server added.
 * @property code what precisely happened, read with the device's type: an event code for an
 *   event, the state key that triggered a condition (`boxFull`, `food1`...), null for `offline`.
 */
data class ServerNotification(
    val id: Long,
    val replay: Boolean,
    val kind: String,
    val deviceId: Long?,
    val petId: Long?,
    val code: String?,
    val time: Instant,
)

/**
 * The notification socket, held for as long as [run] is collected (ARCHITECTURE.md §5): it
 * reconnects with backoff, and at once when [networkBack] emits; it passes the last id received
 * back to the server, which replays what was missed, so each notification is shown once.
 */
interface NotificationFeed {
    /**
     * Emits where the connection stands, and each notification to show, once. The flow completes
     * when the connection must not be reopened: the server ended the session (close code 4401,
     * which signs out), or this session opened another socket that replaced this one (4409).
     */
    fun run(networkBack: Flow<Unit>): Flow<NotificationFeedEvent>
}

sealed interface NotificationFeedEvent {
    data class Status(val status: NotificationConnection) : NotificationFeedEvent

    /** A notification to post. The feed counts it as received once the collector returns. */
    data class Show(val notification: ServerNotification) : NotificationFeedEvent
}

/** Where the notification socket stands. */
sealed interface NotificationConnection {
    /** Opening the socket, before the server's `hello`. */
    data object Connecting : NotificationConnection

    /** The server is sending this phone its notifications. [gap]: some missed ones were lost. */
    data class Connected(val gap: Boolean) : NotificationConnection

    /** The socket closed or could not open, because of [error]; the next attempt is in [retryIn]. */
    data class Waiting(val error: DomainError, val retryIn: Duration) : NotificationConnection

    /** For good: the session ended (the app signs out), or another socket of it replaced this one. */
    data class Ended(val sessionEnded: Boolean) : NotificationConnection
}

/**
 * What is kept on this phone about its notifications: whether they are on (the server keeps the
 * kinds), and whether the user was already asked, once, for what they need from Android.
 */
interface NotificationPreferencesRepository {
    /** Notifications on this phone; on until turned off. */
    val enabled: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)

    /** What the user was already asked for. */
    val asked: Flow<NotificationPrompts>

    suspend fun markPermissionAsked()

    suspend fun markBatteryAsked()
}

/**
 * @property permission Android's notification permission was asked for (Android 13+): asked
 *   again, a refused one gets no prompt, so the way to it is then Android's own settings.
 * @property battery the battery-optimisation exemption was asked for, which is asked once.
 */
data class NotificationPrompts(val permission: Boolean = false, val battery: Boolean = false)

/**
 * What Android lets the notifications do, read again each time it is asked: the user may change
 * either in Android's settings at any moment.
 */
interface NotificationAccess {
    /** Android 13+ and the notification permission is not granted: nothing would show. */
    fun permissionNeeded(): Boolean

    /** Battery optimisation applies to the app, so Doze delays the alerts while the phone sleeps. */
    fun batteryOptimised(): Boolean
}

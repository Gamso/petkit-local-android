package org.petkitlocal.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import org.petkitlocal.core.domain.notifications.NotificationConnection
import org.petkitlocal.core.domain.notifications.NotificationFeed
import org.petkitlocal.core.domain.notifications.NotificationFeedEvent
import org.petkitlocal.core.domain.notifications.NotificationSubject
import org.petkitlocal.core.domain.notifications.ServerNotification

/** What the service shows: its own ongoing notification, and the alerts. */
interface NotificationSurface {
    /** The ongoing notification, which says where the connection stands. */
    fun connection(status: NotificationConnection)

    /** One alert; [silent] for a replayed one after the first of its batch. */
    fun show(notification: ServerNotification, subject: NotificationSubject, silent: Boolean)
}

/**
 * The service's work, without Android: hold the [feed] while [wanted] says so, show what it
 * delivers, and return when the service should stop: no longer wanted (signed out, notifications
 * turned off), or the feed ended for good (4401, 4409).
 *
 * Alerts missed while away arrive together on reconnection: only the first of them makes a sound.
 */
class NotificationServiceRunner(
    private val wanted: Flow<Boolean>,
    private val feed: NotificationFeed,
    private val describe: suspend (ServerNotification) -> NotificationSubject,
    private val surface: NotificationSurface,
) {
    suspend fun run(networkBack: Flow<Unit>) {
        if (!wanted.first()) return
        coroutineScope {
            val unwanted = async { wanted.first { !it } }
            val held = async { hold(networkBack) }
            select {
                unwanted.onAwait {}
                held.onAwait {}
            }
            coroutineContext.cancelChildren()
        }
    }

    private suspend fun hold(networkBack: Flow<Unit>) {
        var replayAlerted = false
        feed.run(networkBack).collect { event ->
            when (event) {
                is NotificationFeedEvent.Status -> {
                    if (event.status == NotificationConnection.Connecting) replayAlerted = false
                    surface.connection(event.status)
                }

                is NotificationFeedEvent.Show -> {
                    val notification = event.notification
                    val silent = notification.replay && replayAlerted
                    if (notification.replay) replayAlerted = true
                    surface.show(notification, subjectOf(notification), silent)
                }
            }
        }
    }

    /** The device and pet by name; a lookup that fails still shows the alert, unnamed. */
    @Suppress("TooGenericExceptionCaught") // Whatever failed, the alert is shown.
    private suspend fun subjectOf(notification: ServerNotification): NotificationSubject = try {
        describe(notification)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        NotificationSubject(device = null, petName = null)
    }
}

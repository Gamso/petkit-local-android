package org.petkitlocal.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.notifications.ObserveNotificationServiceWantedUseCase

/**
 * Starts the notification service when it becomes wanted (a sign-in, notifications turned on) and
 * stops it when it no longer is (a sign-out, a session the server ended, notifications turned
 * off). The app calls [start] once, at its start, and [ensureRunning] each time it comes to the
 * front, which brings back a service Android or a replaced socket stopped meanwhile.
 */
@Singleton
class NotificationServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wanted: ObserveNotificationServiceWantedUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun start() {
        scope.launch {
            wanted().collect { on -> if (on) NotificationService.start(context) else NotificationService.stop(context) }
        }
    }

    fun ensureRunning() {
        scope.launch { if (wanted().first()) NotificationService.start(context) }
    }
}

/**
 * At boot and after an app update, Android lets an app start a foreground service: the service
 * starts, reads the session, and stops at once if the phone is signed out or notifications are off.
 */
class StartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in STARTING_ACTIONS) NotificationService.start(context)
    }

    private companion object {
        val STARTING_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}

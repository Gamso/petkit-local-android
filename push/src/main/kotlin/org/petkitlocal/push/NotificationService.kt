package org.petkitlocal.push

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.notifications.DescribeNotificationUseCase
import org.petkitlocal.core.domain.notifications.NotificationConnection
import org.petkitlocal.core.domain.notifications.NotificationFeed
import org.petkitlocal.core.domain.notifications.ObserveNotificationServiceWantedUseCase

/**
 * The foreground service holding the notification socket (ARCHITECTURE.md §5, ADR 0007) while
 * the phone is signed in and notifications are on: the only thing the app runs in the background.
 * It goes to the foreground at once, as Android requires of a service started that way, then runs
 * [NotificationServiceRunner] and stops itself when that returns: signed out, notifications turned
 * off, the session ended (4401) or replaced (4409). [NotificationServiceController] starts it.
 */
@AndroidEntryPoint
class NotificationService : Service() {
    @Inject lateinit var feed: NotificationFeed

    @Inject lateinit var wanted: ObserveNotificationServiceWantedUseCase

    @Inject lateinit var describe: DescribeNotificationUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private lateinit var poster: NotificationPoster

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        poster = NotificationPoster(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) {
            job = scope.launch {
                NotificationServiceRunner(
                    wanted(),
                    feed,
                    describe::invoke,
                    poster,
                ).run(NetworkReturns.of(this@NotificationService))
                stopSelf()
            }
        }
        // Killed for memory, Android starts it again, and it decides again whether it is wanted.
        return START_STICKY
    }

    /** False when Android refuses: started from the background where that is not allowed. */
    @Suppress("TooGenericExceptionCaught") // ForegroundServiceStartNotAllowedException, and others.
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            NotificationPoster.ONGOING_ID,
            poster.ongoing(NotificationConnection.Connecting),
            // Before Android 14 a service has no type to give: the manifest's is ignored there.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        true
    } catch (_: RuntimeException) {
        false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /**
         * Starts the service, which stops at once when it is not wanted. Android refuses it from
         * the background except where it allows it (boot, an app update, the app in front); a
         * refusal is ignored: the next of those starts it.
         */
        @Suppress("TooGenericExceptionCaught") // ForegroundServiceStartNotAllowedException, SecurityException.
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, NotificationService::class.java))
            } catch (_: RuntimeException) {
                // Not allowed now; it is started again at the next sign-in, boot, update or app start.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NotificationService::class.java))
        }
    }
}

package org.petkitlocal.push

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.time.ZoneId
import org.petkitlocal.core.domain.notifications.NotificationConnection
import org.petkitlocal.core.domain.notifications.NotificationSubject
import org.petkitlocal.core.domain.notifications.NotificationTarget
import org.petkitlocal.core.domain.notifications.ServerNotification

/**
 * Posts the service's notifications: the ongoing one on [NotificationChannels.CONNECTION], each
 * alert on its kind's channel with its text in the phone's language ([NotificationTexts]), opening
 * its device page or History entry ([NotificationTarget]). An alert's Android id is the server's
 * id, so one delivered twice replaces itself rather than showing twice.
 */
class NotificationPoster(private val context: Context, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) :
    NotificationSurface {
    private val manager = NotificationManagerCompat.from(context)

    /** The ongoing notification for [status], for `startForeground` and its updates. */
    fun ongoing(status: NotificationConnection): Notification {
        val (title, text) = when (status) {
            NotificationConnection.Connecting ->
                R.string.push_ongoing_connecting to
                    R.string.push_ongoing_connecting_text

            is NotificationConnection.Connected ->
                R.string.push_ongoing_connected to
                    if (status.gap) R.string.push_ongoing_gap_text else R.string.push_ongoing_connected_text

            is NotificationConnection.Waiting, is NotificationConnection.Ended ->
                R.string.push_ongoing_waiting to R.string.push_ongoing_waiting_text
        }
        return NotificationCompat.Builder(context, NotificationChannels.CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text)))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open(NotificationTarget.Home, ONGOING_ID))
            .build()
    }

    override fun connection(status: NotificationConnection) {
        if (status is NotificationConnection.Ended || !allowed()) return
        @Suppress("MissingPermission") // Checked by allowed().
        manager.notify(ONGOING_ID, ongoing(status))
    }

    override fun show(notification: ServerNotification, subject: NotificationSubject, silent: Boolean) {
        if (!allowed()) return
        @Suppress("MissingPermission") // Checked by allowed().
        manager.notify(TAG, androidId(notification.id), build(notification, subject, silent))
    }

    /** The alert for [notification], without posting it. */
    fun build(notification: ServerNotification, subject: NotificationSubject, silent: Boolean): Notification {
        val text = NotificationTexts(context.resources).of(notification, subject)
        val target = NotificationTarget.of(notification, zone())
        return NotificationCompat.Builder(context, NotificationChannels.forKind(notification.kind))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.text))
            .setWhen(notification.time.toEpochMilli())
            .setShowWhen(true)
            .setSilent(silent)
            .setAutoCancel(true)
            .setContentIntent(open(target, androidId(notification.id)))
            .build()
    }

    /** Opens the app at [target]: its own launcher activity, named explicitly. */
    private fun open(target: NotificationTarget, requestCode: Int): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setPackage(context.packageName)
            .setData(NotificationLinks.uri(target).toUri())
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Android 13+ needs the permission to post; before, the user can only block the app's notifications. */
    private fun allowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        /** The ongoing notification's id, which `startForeground` takes too. */
        const val ONGOING_ID = 1

        /** Alerts are tagged, so their ids, the server's, never meet [ONGOING_ID]. */
        const val TAG = "alert"

        /** The server's id as an Android id: ids are monotonic and far below 2^31 in practice. */
        fun androidId(id: Long): Int = (id and Int.MAX_VALUE.toLong()).toInt()
    }
}

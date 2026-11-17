package org.petkitlocal.push

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import org.petkitlocal.core.domain.settings.NotificationKinds

/**
 * One Android channel per kind (SPEC.md §6), so each can be muted in Android's settings too, and
 * the connection's ongoing notification on its own minimum-importance channel, which the user can
 * hide. Importance follows petkit-local's own priorities (`notify/rules.py`): low for what happens
 * every day, high for what went wrong.
 */
object NotificationChannels {
    const val CONNECTION = "connection"

    /** The channel of a kind this app does not know yet. */
    const val OTHER = "kind_other"

    private val KINDS: List<Kind> = listOf(
        Kind(NotificationKinds.TOILET, R.string.push_channel_toilet, NotificationManagerCompat.IMPORTANCE_LOW),
        Kind(
            NotificationKinds.FEED_SERVED,
            R.string.push_channel_feed_served,
            NotificationManagerCompat.IMPORTANCE_LOW,
        ),
        Kind(
            NotificationKinds.FEED_FAILED,
            R.string.push_channel_feed_failed,
            NotificationManagerCompat.IMPORTANCE_HIGH,
        ),
        Kind(NotificationKinds.ERROR, R.string.push_channel_error, NotificationManagerCompat.IMPORTANCE_HIGH),
        Kind(NotificationKinds.FOOD_LOW, R.string.push_channel_food_low, NotificationManagerCompat.IMPORTANCE_DEFAULT),
        Kind(
            NotificationKinds.CONSUMABLE,
            R.string.push_channel_consumable,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        ),
        Kind(NotificationKinds.OFFLINE, R.string.push_channel_offline, NotificationManagerCompat.IMPORTANCE_HIGH),
    )

    private class Kind(val key: String, val name: Int, val importance: Int)

    /** The channel an alert of [kind] goes to. */
    fun forKind(kind: String): String = if (KINDS.any { it.key == kind }) "kind_$kind" else OTHER

    /**
     * Creates the channels, or renames them in the phone's language: called each time the service
     * starts, so a change of language reaches them. Importance set by the user stays theirs.
     */
    fun ensure(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val connection = NotificationChannelCompat.Builder(CONNECTION, NotificationManagerCompat.IMPORTANCE_MIN)
            .setName(context.getString(R.string.push_channel_connection))
            .setDescription(context.getString(R.string.push_channel_connection_help))
            .setShowBadge(false)
            .build()
        val kinds = KINDS.map { kind ->
            NotificationChannelCompat.Builder(forKind(kind.key), kind.importance)
                .setName(context.getString(kind.name))
                .build()
        }
        val other = NotificationChannelCompat.Builder(OTHER, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.push_channel_other))
            .build()
        manager.createNotificationChannelsCompat(listOf(connection) + kinds + other)
    }
}

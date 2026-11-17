package org.petkitlocal.push

import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.petkitlocal.core.domain.notifications.NotificationTarget

/**
 * Where a notification leads, written into the intent that opens the app: `petkitlocal://open/
 * device/<id>`, `petkitlocal://open/history/<device id>/<yyyy-mm-dd>`, `petkitlocal://open/home`.
 * The intent names the app's own activity explicitly, so no other app can send one of these to
 * it: the activity has no intent filter for them.
 */
object NotificationLinks {
    private const val PREFIX = "petkitlocal://open/"

    fun uri(target: NotificationTarget): String = PREFIX + when (target) {
        NotificationTarget.Home -> "home"
        is NotificationTarget.Device -> "device/${target.deviceId}"
        is NotificationTarget.History -> "history/${target.deviceId}/${target.day}"
    }

    /** The target [uri] names, or null for anything that is not one of these links. */
    fun parse(uri: String?): NotificationTarget? {
        if (uri == null || !uri.startsWith(PREFIX)) return null
        val parts = uri.removePrefix(PREFIX).split('/')
        val id = parts.getOrNull(1)?.toLongOrNull()
        return when {
            parts == listOf("home") -> NotificationTarget.Home

            parts.size == DEVICE_PARTS && parts[0] == "device" -> id?.let { NotificationTarget.Device(it) }

            parts.size == HISTORY_PARTS && parts[0] == "history" -> {
                val day = dayOf(parts.last())
                if (id != null && day != null) NotificationTarget.History(id, day) else null
            }

            else -> null
        }
    }

    private fun dayOf(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    /** `device/<id>` and `history/<id>/<day>`. */
    private const val DEVICE_PARTS = 2
    private const val HISTORY_PARTS = 3
}

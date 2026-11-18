package org.petkitlocal.push

import android.content.res.Resources
import androidx.annotation.StringRes
import org.petkitlocal.core.domain.notifications.NotificationSubject
import org.petkitlocal.core.domain.notifications.ServerNotification
import org.petkitlocal.core.domain.settings.NotificationKinds

/** A notification's title and text, in the phone's language. */
data class NotificationText(val title: String, val text: String)

/**
 * Writes an alert from the server's keys (SPEC.md §6): the kind, and for a condition the state key
 * that triggered it (`PushMessage.code`), found in this app's strings, as `ServerLabels` finds the
 * server's labels. A key this app does not know yet still gives a notification, a plainer one.
 * The device and pet names are user data and are never translated.
 */
class NotificationTexts(private val resources: Resources) {
    fun of(notification: ServerNotification, subject: NotificationSubject): NotificationText {
        val device = subject.device?.modelName ?: resources.getString(R.string.push_some_device)
        val pet = subject.petName
        return when (notification.kind) {
            NotificationKinds.TOILET -> NotificationText(
                title = if (pet != null) {
                    resources.getString(R.string.push_toilet_title_pet, pet)
                } else {
                    resources.getString(R.string.push_toilet_title)
                },
                text = device,
            )

            NotificationKinds.FEED_SERVED -> text(
                R.string.push_feed_served_title,
                R.string.push_feed_served_text,
                device,
            )

            NotificationKinds.FEED_FAILED -> text(
                R.string.push_feed_failed_title,
                R.string.push_feed_failed_text,
                device,
            )

            NotificationKinds.ERROR -> text(R.string.push_error_title, R.string.push_error_text, device)

            NotificationKinds.OFFLINE -> text(R.string.push_offline_title, R.string.push_offline_text, device)

            NotificationKinds.FOOD_LOW -> text(
                R.string.push_food_low_title,
                FOOD_LOW_TEXTS[notification.code] ?: R.string.push_food_low_text,
                device,
            )

            NotificationKinds.CONSUMABLE -> {
                val (title, text) = CONSUMABLES[notification.code]
                    ?: (R.string.push_consumable_title to R.string.push_consumable_text)
                text(title, text, device)
            }

            else -> text(R.string.push_other_title, R.string.push_other_text, device)
        }
    }

    private fun text(@StringRes title: Int, @StringRes text: Int, device: String) =
        NotificationText(resources.getString(title), resources.getString(text, device))

    private companion object {
        /** `food_low` by the state key that triggered it (petkit-local `notify/rules.py`). */
        val FOOD_LOW_TEXTS: Map<String, Int> = mapOf(
            "foodLow" to R.string.push_food_low_text,
            "food1" to R.string.push_food_low_text_hopper1,
            "food2" to R.string.push_food_low_text_hopper2,
        )

        /** `consumable` by the state key that triggered it: its title and its text. */
        val CONSUMABLES: Map<String, Pair<Int, Int>> = mapOf(
            "boxFull" to (R.string.push_box_full_title to R.string.push_box_full_text),
            "sandPercent" to (R.string.push_litter_low_title to R.string.push_litter_low_text),
            "deodorantLeftDays" to (R.string.push_deodorizer_title to R.string.push_deodorizer_text),
            "sprayLeftDays" to (R.string.push_spray_title to R.string.push_spray_text),
            "desiccantLeftDays" to (R.string.push_desiccant_title to R.string.push_desiccant_text),
            "filterWarning" to (R.string.push_filter_title to R.string.push_filter_text),
            "lackWarning" to (R.string.push_water_low_title to R.string.push_water_low_text),
            "stgFullState" to (R.string.push_tray_full_title to R.string.push_tray_full_text),
            "liquidLack" to (R.string.push_liquid_low_title to R.string.push_liquid_low_text),
        )
    }
}

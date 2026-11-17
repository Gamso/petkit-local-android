package org.petkitlocal.core.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import org.petkitlocal.core.domain.activity.StepLabel

/**
 * The labels the server sends in English (entity and action names, select options, event kinds),
 * in the app's language, found by their stable keys (SPEC.md §7). A key this app does not know
 * yet shows the server's own text: a missing translation is a bug report, never a crash or a
 * blank. Device and pet names are user data and never pass through here.
 *
 * Usable outside Compose too (notifications build their text with it), from any [Resources].
 */
class ServerLabels(private val resources: Resources) {
    /** `Entity.name`, by `Entity.key`. */
    fun entity(key: String, serverName: String): String = translate(EntityLabels, key, serverName)

    /** `Action.name`, by `Action.key`. */
    fun action(key: String, serverName: String): String = translate(ActionLabels, key, serverName)

    /** A select's option or a sensor's `display` text, which is its own key. */
    fun option(value: String): String = translate(OptionLabels, value, value)

    /** A History filter chip, by its `TimelineFilter` value. */
    fun filter(key: String): String = translate(FilterLabels, key, key)

    /** `TimelineItem.event_kind`; [serverLabel] is the card's own `label`, when it has one. */
    fun eventKind(kind: String, serverLabel: String?): String = translate(EventKindLabels, kind, serverLabel ?: kind)

    /** `EventField.label`, by `EventField.key`. */
    fun eventField(key: String, serverLabel: String): String = translate(EventFieldLabels, key, serverLabel)

    /** A word an `EventField.text` is made of ("yes", "completed", "Auto"); anything else as it is. */
    fun eventFieldValue(text: String): String = EventFieldValueLabels[text]?.let(resources::getString) ?: option(text)

    /**
     * A completion's label read by [StepLabel]: "Cleaning done", "Deodorizing failed",
     * "Meal failed: bowl not empty". [serverLabel] for a label not read, or an operation this app
     * does not know; a reason it does not know is kept in the server's words.
     */
    fun step(parsed: StepLabel?, serverLabel: String): String {
        val operation = parsed?.let { StepOperationLabels[it.operation] }
        val reason = parsed?.reason
        return when {
            parsed == null || operation == null -> serverLabel

            parsed.done -> resources.getString(operation.done)

            reason == null -> resources.getString(R.string.server_step_failed, resources.getString(operation.name))

            else -> resources.getString(
                R.string.server_step_failed_because,
                resources.getString(operation.name),
                StepReasonLabels[reason]?.let(resources::getString) ?: reason,
            )
        }
    }

    /** Whether [key] of that kind has a translation: for tests and for reporting gaps. */
    fun knowsEntity(key: String): Boolean = key in EntityLabels

    /** Whether an event field [key] has a translation: the detail sheet shows those first. */
    fun knowsEventField(key: String): Boolean = key in EventFieldLabels

    private fun translate(labels: Map<String, Int>, key: String, fallback: String): String =
        labels[key]?.let(resources::getString) ?: fallback
}

/** [ServerLabels] for the current configuration; a language change gives a new one. */
@Composable
fun rememberServerLabels(): ServerLabels {
    // LocalResources follows configuration changes (a language switch) where the context does not.
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { ServerLabels(resources) }
}

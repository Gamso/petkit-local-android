package org.petkitlocal.core.domain.activity

import java.util.Locale

/**
 * A completion label read back into its parts. petkit-local words a step or a card that ends an
 * operation as `[trigger] <operation> <outcome>[ (<detail>)][ - <cause>]`
 * (`events/decode.py::event_label`): "Auto cleaning completed", "Auto deodorizing failed",
 * "Manual cleaning stopped (bin full)", "Feeding skipped (bowl not empty)", "Auto cleaning
 * terminated - hall sensor (L)". The server sends that label in English only and with no key, so
 * reading it back is the only way to translate it and to tell a failure from a success; a label of
 * any other shape stays the server's own text ([parse] answers null).
 */
data class StepLabel(
    /** The operation, lower case, as the server names it: "cleaning", "deodorizing", "light cycle". */
    val operation: String,
    /**
     * How it was started, as the server words it, which is also the key of its translation:
     * "Auto", "Periodic" or "Manual"; null when not said.
     */
    val trigger: String?,
    /** It ran to its end. */
    val done: Boolean,
    /**
     * Why it did not, lower case, as the server words it: the cause the device named ("bin
     * full"), else the outcome's detail ("bowl not empty"), else the outcome itself ("canceled",
     * "terminated"); null for a plain "failed" or a step that is [done].
     */
    val reason: String?,
) {
    companion object {
        /** `decode.py::TRIGGER_SHORT`, lower case, to the server's own spelling. */
        private val TRIGGERS = mapOf("auto" to "Auto", "periodic" to "Periodic", "manual" to "Manual")
        private val DONE = setOf("completed", "done")

        /** Every outcome word the decoder writes (`RESULT`, `FEED_RESULT`, the W7H's deep clean). */
        private val SHAPE = Regex(
            "^(.+?) (completed|done|terminated|failed|canceled|stopped|blocked|skipped|refused|aborted)" +
                "(?: \\((.+)\\))?$",
        )
        private const val CAUSE_SEPARATOR = " - "
        private const val FAILED = "failed"

        /** The parts of [label], or null when it is not a completion's label. */
        fun parse(label: String): StepLabel? {
            val text = label.trim().lowercase(Locale.ROOT)
            val head = text.substringBefore(CAUSE_SEPARATOR)
            val cause = text.substringAfter(CAUSE_SEPARATOR, missingDelimiterValue = "").trim().ifEmpty { null }
            val (subject, outcome, detail) = SHAPE.matchEntire(head)?.destructured ?: return null
            val words = subject.split(' ')
            val trigger = TRIGGERS[words.first()]?.takeIf { words.size > 1 }
            val done = outcome in DONE
            return StepLabel(
                operation = if (trigger != null) words.drop(1).joinToString(" ") else subject,
                trigger = trigger,
                done = done,
                reason = if (done) null else cause ?: detail.ifEmpty { null } ?: outcome.takeUnless { it == FAILED },
            )
        }
    }
}

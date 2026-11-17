package org.petkitlocal.core.domain.activity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** The labels are petkit-local's, as `events/decode.py::event_label` builds them. */
class StepLabelTest {
    private fun done(operation: String, trigger: String? = null) =
        StepLabel(operation, trigger, done = true, reason = null)

    private fun failed(operation: String, trigger: String?, reason: String?) =
        StepLabel(operation, trigger, done = false, reason = reason)

    @Test
    fun `a completion reads as its operation and trigger, done`() {
        assertEquals(done("cleaning", "Auto"), StepLabel.parse("Auto cleaning completed"))
        assertEquals(done("light cycle"), StepLabel.parse("Light cycle completed"))
        assertEquals(done("deep cleaning"), StepLabel.parse("Deep cleaning completed"))
        assertEquals(done("reset"), StepLabel.parse("Reset done"))
        assertEquals(done("drinking"), StepLabel.parse("Drinking done"))
        assertEquals(
            done("cleaning", "Periodic"),
            StepLabel.parse("Periodic cleaning completed"),
        )
    }

    @Test
    fun `a failure says why when the label does`() {
        // A plain failure has no reason.
        assertEquals(failed("deodorizing", "Auto", null), StepLabel.parse("Auto deodorizing failed"))
        // The outcome's own detail, in brackets.
        assertEquals(
            failed("cleaning", "Manual", "bin full"),
            StepLabel.parse("Manual cleaning stopped (bin full)"),
        )
        assertEquals(
            failed("feeding", null, "bowl not empty"),
            StepLabel.parse("Feeding skipped (bowl not empty)"),
        )
        assertEquals(
            failed("cleaning", null, "kitten mode"),
            StepLabel.parse("Cleaning canceled (kitten mode)"),
        )
        // The outcome itself, when it is more than "failed".
        assertEquals(failed("cleaning", "Auto", "canceled"), StepLabel.parse("Auto cleaning canceled"))
        assertEquals(
            failed("cleaning", "Auto", "terminated"),
            StepLabel.parse("Auto cleaning terminated"),
        )
        // A cause the device named wins over the outcome.
        assertEquals(
            failed("cleaning", "Auto", "hall sensor (l)"),
            StepLabel.parse("Auto cleaning terminated - hall sensor (L)"),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Weight check",
            "Mechanism started",
            "Error cleared",
            "Error - bin full",
            "Odor removal - mechanism started",
            "Cleaning result 9",
            "Event 99",
            "",
        ],
    )
    fun `anything else is not read, and stays the server's text`(label: String) {
        assertNull(StepLabel.parse(label))
    }
}

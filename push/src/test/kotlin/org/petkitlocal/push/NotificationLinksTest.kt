package org.petkitlocal.push

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.notifications.NotificationTarget

class NotificationLinksTest {
    @Test
    fun `each target reads back from its link`() {
        listOf(
            NotificationTarget.Home,
            NotificationTarget.Device(1002),
            NotificationTarget.History(1003, LocalDate.of(2026, 11, 16)),
        ).forEach { assertEquals(it, NotificationLinks.parse(NotificationLinks.uri(it))) }
        assertEquals(
            "petkitlocal://open/history/1003/2026-11-16",
            NotificationLinks.uri(NotificationTarget.History(1003, LocalDate.of(2026, 11, 16))),
        )
    }

    @Test
    fun `anything else is no target`() {
        listOf(
            null,
            "",
            "https://example.com/device/1",
            "petkitlocal://open/device/x",
            "petkitlocal://open/history/1/not-a-day",
            "petkitlocal://open/history/1",
            "petkitlocal://open/settings",
        ).forEach { assertNull(NotificationLinks.parse(it), it) }
    }
}

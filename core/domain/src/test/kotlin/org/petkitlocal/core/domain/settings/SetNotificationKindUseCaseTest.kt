package org.petkitlocal.core.domain.settings

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.settings.SetNotificationKindUseCase.Companion.nextKinds

class SetNotificationKindUseCaseTest {
    private val server = listOf("toilet", "feed_served", "error", "offline").map { ServerNotificationKind(it, true) }

    private fun settings(chosen: List<String>?) = NotificationSettings(chosen, server, connected = false, lastId = 0)

    @Test
    fun `turning one off from every kind lists the others, in the server's order`() {
        assertEquals(listOf("toilet", "error", "offline"), nextKinds(settings(null), "feed_served", on = false))
    }

    @Test
    fun `turning one on keeps the server's order`() {
        assertEquals(listOf("toilet", "error"), nextKinds(settings(listOf("error")), "toilet", on = true))
    }

    @Test
    fun `every kind chosen again asks for every kind, so a later kind arrives too`() {
        assertEquals(null, nextKinds(settings(listOf("toilet", "feed_served", "error")), "offline", on = true))
    }

    @Test
    fun `the last one off leaves none`() {
        assertEquals(emptyList<String>(), nextKinds(settings(listOf("error")), "error", on = false))
    }

    @Test
    fun `receives reads null as every kind`() {
        assertTrue(settings(null).receives("anything"))
        assertTrue(settings(listOf("error")).receives("error"))
        assertFalse(settings(listOf("error")).receives("toilet"))
    }

    @Test
    fun `the use case sends what nextKinds computed`() = runTest {
        val sent = mutableListOf<List<String>?>()
        val repository = object : NotificationSettingsRepository {
            override suspend fun load(): Outcome<NotificationSettings> = Outcome.Success(settings(null))

            override suspend fun setKinds(kinds: List<String>?): Outcome<NotificationSettings> {
                sent += kinds
                return Outcome.Success(settings(kinds))
            }
        }

        SetNotificationKindUseCase(repository)(settings(null), "offline", on = false)

        assertEquals(listOf(listOf("toilet", "feed_served", "error")), sent)
    }
}

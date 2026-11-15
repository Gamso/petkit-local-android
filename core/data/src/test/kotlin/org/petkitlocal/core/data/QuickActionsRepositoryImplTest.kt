package org.petkitlocal.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import app.cash.turbine.test
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.petkitlocal.core.datastore.QuickActionsStore
import org.petkitlocal.core.domain.device.EditQuickActionsUseCase
import org.petkitlocal.core.domain.device.QuickActionEdit
import org.petkitlocal.core.domain.device.ResetQuickActionsUseCase
import org.petkitlocal.core.domain.model.DeviceAction

/** Integration: the user's actions in view through the real DataStore file, and with the session. */
class QuickActionsRepositoryImplTest {
    @TempDir
    lateinit var dir: File

    private val fountain = listOf(
        DeviceAction("fountain_flush", "Drain & Flush", false),
        DeviceAction("fountain_refill", "Refill", false),
        DeviceAction("fountain_drain", "Drain", true),
    )

    private fun TestScope.store() = QuickActionsStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "quick.preferences_pb") },
    )

    @Test
    fun `a pinned action is stored, observed as it changes, and reset forgets it`() = runTest {
        val repository = QuickActionsRepositoryImpl(store())

        repository.observe(FOUNTAIN).test {
            assertNull(awaitItem())

            EditQuickActionsUseCase(repository)(FOUNTAIN, fountain, QuickActionEdit.Add("fountain_drain"))
            assertEquals(listOf("fountain_refill", "fountain_flush", "fountain_drain"), awaitItem())

            ResetQuickActionsUseCase(repository)(FOUNTAIN)
            assertNull(awaitItem())
        }
    }

    @Test
    fun `choices outlive sessions on one server and go with a sign-in on another`() = runTest {
        val store = store()
        val repository = QuickActionsRepositoryImpl(store)
        val serverCache = QuickActionsServerCache(store)
        serverCache.keepOnlyFor(HOME)
        repository.update(FOUNTAIN) { listOf("fountain_drain") }
        repository.update(T4) { listOf("level_litter") }

        // Signed out and back in to the same server (or its session revoked, then a new one).
        serverCache.keepOnlyFor(HOME)
        assertEquals(listOf("fountain_drain"), repository.observe(FOUNTAIN).first())

        serverCache.keepOnlyFor(ELSEWHERE)
        assertNull(repository.observe(FOUNTAIN).first())
        assertNull(repository.observe(T4).first())
    }

    @Test
    fun `a write the disk refuses changes nothing and says so`() = runTest {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("disk full")
        }
        val repository = QuickActionsRepositoryImpl(QuickActionsStore(broken))

        assertFalse(repository.update(FOUNTAIN) { listOf("fountain_drain") })
        assertNull(repository.observe(FOUNTAIN).first())
        assertTrue(QuickActionsRepositoryImpl(store()).update(FOUNTAIN) { listOf("fountain_drain") })
        // Nor does a sign-in crash on it.
        QuickActionsServerCache(QuickActionsStore(broken)).keepOnlyFor(HOME)
    }

    private companion object {
        const val T4 = 1001L
        const val FOUNTAIN = 3001L
        const val HOME = "http://192.168.1.20:8099"
        const val ELSEWHERE = "https://pets.example.com"
    }
}

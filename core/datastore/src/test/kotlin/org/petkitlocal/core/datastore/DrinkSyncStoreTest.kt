package org.petkitlocal.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DrinkSyncStoreTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "drink_sync.preferences_pb")

    private fun TestScope.store() = DrinkSyncStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { file })

    @Test
    fun `the last sync reads back per accessory, never synced is null`() = runTest {
        val store = store()

        store.setLastSync(CTW3, 1_763_200_000_000L)

        assertEquals(1_763_200_000_000L, store.lastSync(CTW3).first())
        assertNull(store.lastSync(OTHER).first())
    }

    @Test
    fun `another server forgets them, the same one keeps them`() = runTest {
        val store = store()
        store.keepOnlyFor("https://home.example:8443/")
        store.setLastSync(CTW3, 5L)

        store.keepOnlyFor("https://home.example:8443/")
        assertEquals(5L, store.lastSync(CTW3).first())

        store.keepOnlyFor("https://other.example/")
        assertNull(store.lastSync(CTW3).first())
    }

    private companion object {
        const val CTW3 = 3003L
        const val OTHER = 3002L
    }
}

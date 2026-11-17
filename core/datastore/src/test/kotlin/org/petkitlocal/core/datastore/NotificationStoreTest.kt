package org.petkitlocal.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NotificationStoreTest {
    @TempDir
    lateinit var dir: File

    private fun TestScope.store() =
        NotificationStore(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(dir, "n.preferences_pb") })

    @Test
    fun `a new phone has notifications on, no last id and nothing asked`() = runTest {
        val store = store()

        assertTrue(store.enabled.first())
        assertNull(store.lastId())
        assertFalse(store.permissionAsked.first())
        assertFalse(store.batteryAsked.first())
    }

    @Test
    fun `each value reads back, and clearing the last id keeps the rest`() = runTest {
        val store = store()

        store.setEnabled(false)
        store.markPermissionAsked()
        store.markBatteryAsked()
        store.saveLastId(42)
        assertEquals(42L, store.lastId())

        store.clearLastId()

        assertNull(store.lastId())
        assertFalse(store.enabled.first())
        assertTrue(store.permissionAsked.first())
        assertTrue(store.batteryAsked.first())
    }
}

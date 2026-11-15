package org.petkitlocal.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class QuickActionsStoreTest {
    @TempDir
    lateinit var dir: File

    private val file get() = File(dir, "quick_actions.preferences_pb")

    private fun TestScope.store(scope: CoroutineScope = backgroundScope) =
        QuickActionsStore(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test
    fun `keys read back in order, per device, and none chosen reads as null`() = runTest {
        val store = store()

        store.update(T4) { listOf("level_litter", "cleaning_start", "dump_litter") }

        assertEquals(listOf("level_litter", "cleaning_start", "dump_litter"), store.pinned(T4).first())
        assertNull(store.pinned(FOUNTAIN).first())
    }

    @Test
    fun `an empty choice differs from no choice, and null forgets it`() = runTest {
        val store = store()

        store.update(T4) { emptyList() }
        assertEquals(emptyList<String>(), store.pinned(T4).first())

        store.update(T4) { null }
        assertNull(store.pinned(T4).first())
    }

    @Test
    fun `an update sees the stored keys, and keys that cannot be stored are left out`() = runTest {
        val store = store()
        store.update(T4) { listOf("feed") }

        store.update(T4) { stored -> stored.orEmpty() + listOf("", "bad\nkey", "play_sound") }

        assertEquals(listOf("feed", "play_sound"), store.pinned(T4).first())
    }

    @Test
    fun `the choice survives a new instance on the same file and a sign-in on the same server`() = runTest {
        // DataStore allows one instance per file at a time: the first one's scope ends first.
        val job = Job()
        val first = store(CoroutineScope(coroutineContext + job))
        first.keepOnlyFor(SERVER)
        first.update(T4) { listOf("deodorize") }
        first.update(FOUNTAIN) { listOf("fountain_refill") }
        job.cancelAndJoin()

        val second = store()
        assertEquals(listOf("deodorize"), second.pinned(T4).first())

        second.keepOnlyFor(SERVER)
        assertEquals(listOf("deodorize"), second.pinned(T4).first())
        assertEquals(listOf("fountain_refill"), second.pinned(FOUNTAIN).first())
    }

    @Test
    fun `a sign-in on another server forgets every device's choice, and its own are kept from then on`() = runTest {
        val store = store()
        store.keepOnlyFor(SERVER)
        store.update(T4) { listOf("deodorize") }

        store.keepOnlyFor(OTHER_SERVER)
        assertNull(store.pinned(T4).first())

        store.update(T4) { listOf("pause") }
        store.keepOnlyFor(OTHER_SERVER)
        assertEquals(listOf("pause"), store.pinned(T4).first())
        // Back on the first server: its choices were dropped when the other one signed in.
        store.keepOnlyFor(SERVER)
        assertNull(store.pinned(T4).first())
    }

    @Test
    fun `choices from before any server was recorded are not trusted`() = runTest {
        val store = store()
        store.update(T4) { listOf("deodorize") }

        store.keepOnlyFor(SERVER)

        assertNull(store.pinned(T4).first())
    }

    @Test
    fun `a damaged file reads as no choice`() = runTest {
        file.writeText("not a protobuf at all")

        assertNull(store().pinned(T4).first())
    }

    private companion object {
        const val T4 = 1001L
        const val FOUNTAIN = 3001L
        const val SERVER = "http://192.168.1.20:8099"
        const val OTHER_SERVER = "https://pets.example.com"
    }
}

package org.petkitlocal.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.database.di.DatabaseModule
import org.robolectric.RobolectricTestRunner

/**
 * An installed development build holding an older schema must open, rebuilt, with the app's own
 * builder (`DatabaseModule`) — not crash on start. It did, twice: once with a schema changed under
 * the same version number, once because `fallbackToDestructiveMigrationOnDowngrade` turned
 * required migrations back on. Until the first release there is no migration (PetkitDatabase).
 */
@RunWith(RobolectricTestRunner::class)
class OlderDevDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: PetkitDatabase? = null

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(PetkitDatabase.NAME)
    }

    @Test
    fun `an older development database is rebuilt instead of crashing`() = runTest {
        if (PetkitDatabase.VERSION == 1) return@runTest // Nothing older can be installed yet.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(PetkitDatabase.NAME), null).use { old ->
            old.execSQL("CREATE TABLE devices (id INTEGER PRIMARY KEY NOT NULL, model TEXT NOT NULL)")
            old.execSQL("INSERT INTO devices (id, model) VALUES (1002, 'Pura Max')")
            old.version = PetkitDatabase.VERSION - 1
        }

        val opened = DatabaseModule.database(context).also { database = it }

        assertEquals(emptyList<Any>(), opened.deviceDao().observeDevices().first())
    }
}

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
 * An installed development build from before the first release, and a newer app's database after a
 * downgrade, must open, rebuilt, with the app's own builder (`DatabaseModule`), not crash on
 * start. It did, twice: once with a schema changed under the same version number, once because
 * `fallbackToDestructiveMigrationOnDowngrade` was added next to `fallbackToDestructiveMigration`
 * and turned required migrations back on. A released schema is migrated instead
 * (PetkitDatabaseMigrationTest).
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
    fun `a development database from before the first release is rebuilt instead of crashing`() = runTest {
        install(version = PetkitDatabase.OLDEST_SUPPORTED_VERSION - 1)

        val opened = DatabaseModule.database(context).also { database = it }

        assertEquals(emptyList<Any>(), opened.deviceDao().observeDevices().first())
    }

    @Test
    fun `a newer app's database is rebuilt after a downgrade`() = runTest {
        install(version = PetkitDatabase.VERSION + 1)

        val opened = DatabaseModule.database(context).also { database = it }

        assertEquals(emptyList<Any>(), opened.deviceDao().observeDevices().first())
    }

    /** A database file of [version] that is not this app's schema at all. */
    private fun install(version: Int) {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(PetkitDatabase.NAME), null).use { old ->
            old.execSQL("CREATE TABLE devices (id INTEGER PRIMARY KEY NOT NULL, model TEXT NOT NULL)")
            old.execSQL("INSERT INTO devices (id, model) VALUES (1002, 'Pura Max')")
            old.version = version
        }
    }
}

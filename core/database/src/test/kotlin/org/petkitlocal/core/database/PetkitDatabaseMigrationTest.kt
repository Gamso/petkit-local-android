package org.petkitlocal.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The migrations, against the schemas exported to core/database/schemas (read here as test
 * assets): every supported version must open, migrated, as the current one, with its data. A
 * schema change that forgets its migration, or its export, fails here. Before the first release
 * the only supported version is the current one (PetkitDatabase).
 */
@RunWith(RobolectricTestRunner::class)
class PetkitDatabaseMigrationTest {
    private val file: File = ApplicationProvider.getApplicationContext<Context>()
        .getDatabasePath("migration-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = file,
        driver = AndroidSQLiteDriver(),
        databaseClass = PetkitDatabase::class,
    )

    @Test
    fun `the exported schemas are every supported version up to the current one`() {
        val exported = InstrumentationRegistry.getInstrumentation().context.assets
            .list(PetkitDatabase::class.java.name)
            .orEmpty()
            .map { it.removeSuffix(".json").toInt() }
            .sorted()

        assertEquals((PetkitDatabase.OLDEST_SUPPORTED_VERSION..PetkitDatabase.VERSION).toList(), exported)
    }

    @Test
    fun `every exported version migrates to the current schema`() {
        for (version in PetkitDatabase.OLDEST_SUPPORTED_VERSION..PetkitDatabase.VERSION) {
            file.delete()
            helper.createDatabase(version).close()
            helper.runMigrationsAndValidate(PetkitDatabase.VERSION, PetkitDatabase.MIGRATIONS.toList()).close()
        }
    }

    @Test
    fun `the oldest supported database keeps its data when the app opens it`() = runTest {
        helper.createDatabase(PetkitDatabase.OLDEST_SUPPORTED_VERSION).apply {
            execSQL(
                "INSERT INTO devices (id, position, type, model, model_name, generation, kind, online, " +
                    "transport, last_seen, firmware, is_camera, serial, connection, parent_id, accessories_json) " +
                    "VALUES (1002, 0, 't4', 'Pura Max', 'Pura Max 2', 2, 'litter_box', 1, 'http', NULL, NULL, 0, " +
                    "NULL, 'wifi', NULL, '[]')",
            )
            execSQL(
                "INSERT INTO pets (id, position, name, device_ids_json, entered_weight_g, weight_g, weight_learned, " +
                    "photo) VALUES (1, 0, 'Mochi', '[1002]', 4200.0, 4333.0, 1, NULL)",
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, PetkitDatabase::class.java, file.path)
            .addMigrations(*PetkitDatabase.MIGRATIONS)
            .build()
        try {
            val device = db.deviceDao().observeDevices().first().single().device
            assertEquals(listOf("Pura Max", "Pura Max 2", 2), listOf(device.model, device.modelName, device.generation))
            assertEquals(listOf("Mochi"), db.petDao().observePets().first().map { it.name })
        } finally {
            db.close()
        }
    }
}

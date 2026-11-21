package org.petkitlocal.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import org.petkitlocal.core.database.dao.DeviceDao
import org.petkitlocal.core.database.dao.PetDao
import org.petkitlocal.core.database.dao.TimelineDao
import org.petkitlocal.core.database.model.ActionRow
import org.petkitlocal.core.database.model.DeviceRow
import org.petkitlocal.core.database.model.EntityRow
import org.petkitlocal.core.database.model.PetActivityRow
import org.petkitlocal.core.database.model.PetRow
import org.petkitlocal.core.database.model.PetWeightRow
import org.petkitlocal.core.database.model.TimelineItemRow

/**
 * The app's cache (ADR 0002).
 *
 * Since the first release (v0.1.0, schema 10): [OLDEST_SUPPORTED_VERSION] stays at the first
 * released version, every schema change raises [VERSION], keeps its export in
 * `core/database/schemas/` and adds its step to [MIGRATIONS], and `PetkitDatabaseMigrationTest`
 * checks that every supported version migrates to the current one, with its data. There is no
 * destructive fallback for a released version: losing the cache on an upgrade would empty the
 * screens of a phone that is offline. Only a development install from before the first release
 * (older than [OLDEST_SUPPORTED_VERSION]) is rebuilt, and a downgrade (`DatabaseModule`).
 */
@Database(
    entities = [
        DeviceRow::class,
        EntityRow::class,
        ActionRow::class,
        PetRow::class,
        PetActivityRow::class,
        PetWeightRow::class,
        TimelineItemRow::class,
    ],
    version = PetkitDatabase.VERSION,
    exportSchema = true,
)
abstract class PetkitDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    abstract fun petDao(): PetDao

    abstract fun timelineDao(): TimelineDao

    companion object {
        /**
         * 2: devices gained their connection, parent and accessories (Bluetooth accessories).
         * 3: timeline cards gained their media (History).
         * 4: pets gained their statistics, recent visits and weighed visits (Pets).
         * 5: timeline cards say whether they hold a clip (a camera's recent clips).
         * 6: timeline cards keep their steps (a device's activity by visit).
         * 7: a feeder's meals keep the portions they served (the feeder's day).
         * 8: timeline cards keep what a feed served, which the server now sends on the card; the
         *    portions read from each meal's event, and their table, are gone.
         * 9: devices keep their full product name and their generation (a Pura Max 2).
         * 10: devices keep whether they can be talked through. First released (v0.1.0).
         * 11: devices keep whether they recognise pets by their face, and pets their face photos
         *     (managing pets from the app).
         */
        const val VERSION = 11

        /** The oldest schema an installed app may hold: the first released one (v0.1.0). */
        const val OLDEST_SUPPORTED_VERSION = 10

        const val NAME = "petkit.db"

        /**
         * 10 to 11: two columns with their defaults. A device reads as not recognising pets, and a
         * pet as having no face photo, until the next refresh or `hello` says otherwise.
         */
        val MIGRATION_10_11: Migration = SqlMigration(
            10,
            11,
            "ALTER TABLE devices ADD COLUMN supports_ai INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE pets ADD COLUMN faces_json TEXT NOT NULL DEFAULT '[]'",
        )

        /** Every step from a supported version to the next one. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_10_11)
    }
}

/**
 * A migration made of plain SQL statements, run in order. Both entry points run them: Room calls
 * the [SupportSQLiteDatabase] one with the app's own open helper, the [SQLiteConnection] one with
 * a driver (the migration tests use one).
 */
private class SqlMigration(from: Int, to: Int, private vararg val statements: String) : Migration(from, to) {
    override fun migrate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)

    override fun migrate(connection: SQLiteConnection) = statements.forEach(connection::execSQL)
}

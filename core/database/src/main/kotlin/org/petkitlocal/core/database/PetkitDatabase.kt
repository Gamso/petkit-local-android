package org.petkitlocal.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import org.petkitlocal.core.database.dao.DeviceDao
import org.petkitlocal.core.database.dao.PetDao
import org.petkitlocal.core.database.dao.TimelineDao
import org.petkitlocal.core.database.model.ActionRow
import org.petkitlocal.core.database.model.DeviceRow
import org.petkitlocal.core.database.model.EntityRow
import org.petkitlocal.core.database.model.PetRow
import org.petkitlocal.core.database.model.TimelineItemRow

/**
 * The app's cache (ADR 0002).
 *
 * Until the first release: every schema change still raises [VERSION] — Room recognises a schema
 * only by its version, so a changed schema under the same number makes an installed app crash on
 * start — but writes no migration. [OLDEST_SUPPORTED_VERSION] follows [VERSION], only the current
 * schema is exported to `core/database/schemas/`, and a development install holding an older one
 * is rebuilt (`DatabaseModule`): it is only a cache, refetched from the server.
 *
 * From the first release on: [OLDEST_SUPPORTED_VERSION] stays at the first released version,
 * every schema change raises [VERSION], keeps its export and adds its step to [MIGRATIONS], and
 * `PetkitDatabaseMigrationTest` checks that every supported version migrates to the current one.
 * Then there is no destructive fallback: losing the cache on an upgrade would empty the screens of
 * a phone that is offline.
 */
@Database(
    entities = [DeviceRow::class, EntityRow::class, ActionRow::class, PetRow::class, TimelineItemRow::class],
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
         */
        const val VERSION = 3

        /** The oldest schema an installed app may hold; [VERSION] until the first release. */
        const val OLDEST_SUPPORTED_VERSION = VERSION

        const val NAME = "petkit.db"

        /** Every step from a supported version to the next one. None before the first release. */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}

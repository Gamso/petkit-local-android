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
 * The app's cache (ADR 0002). Every schema change raises [version], exports its schema to
 * `core/database/schemas/` and adds its step to [MIGRATIONS]; `PetkitDatabaseMigrationTest`
 * checks that every exported version migrates to the current one. There is no destructive
 * fallback: losing the cache on an upgrade would empty the screens of a phone that is offline.
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
        const val VERSION = 1
        const val NAME = "petkit.db"

        /** Every step from an exported version to the next one. Version 1 is the first. */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}

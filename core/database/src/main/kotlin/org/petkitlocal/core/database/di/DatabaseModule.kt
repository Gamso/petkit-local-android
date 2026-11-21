package org.petkitlocal.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.petkitlocal.core.database.PetkitDatabase
import org.petkitlocal.core.database.dao.DeviceDao
import org.petkitlocal.core.database.dao.PetDao
import org.petkitlocal.core.database.dao.TimelineDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): PetkitDatabase =
        Room.databaseBuilder(context, PetkitDatabase::class.java, PetkitDatabase.NAME)
            .addMigrations(*PetkitDatabase.MIGRATIONS)
            // A released schema is migrated, never dropped (PetkitDatabase). Only a development
            // install from before the first release, whose schema no migration starts from, is
            // rebuilt: it is only a cache, refetched from the server.
            .fallbackToDestructiveMigrationFrom(dropAllTables = true, *PRE_RELEASE_VERSIONS)
            // A downgrade keeps nothing it cannot read. Unlike `fallbackToDestructiveMigration`,
            // this leaves the migrations of an upgrade required.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    /** The schemas of development builds from before the first release. */
    private val PRE_RELEASE_VERSIONS: IntArray = (1 until PetkitDatabase.OLDEST_SUPPORTED_VERSION).toList().toIntArray()

    @Provides
    fun deviceDao(database: PetkitDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun petDao(database: PetkitDatabase): PetDao = database.petDao()

    @Provides
    fun timelineDao(database: PetkitDatabase): TimelineDao = database.timelineDao()
}

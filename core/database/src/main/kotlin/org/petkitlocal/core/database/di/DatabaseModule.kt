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
            // Nothing is released yet: a schema change raises the version without a migration, and
            // a development install holding an older one is rebuilt here. It is only a cache,
            // refetched from the server. This must go with the first release, from which every
            // change is a real migration (PetkitDatabase).
            // It also covers a downgrade. Do not add `fallbackToDestructiveMigrationOnDowngrade`
            // after it: that call turns required migrations back on for upgrades, and an
            // installed app with an older schema then crashes on start. From the first release,
            // it replaces this line (a downgrade keeps nothing it cannot read).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun deviceDao(database: PetkitDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun petDao(database: PetkitDatabase): PetDao = database.petDao()

    @Provides
    fun timelineDao(database: PetkitDatabase): TimelineDao = database.timelineDao()
}

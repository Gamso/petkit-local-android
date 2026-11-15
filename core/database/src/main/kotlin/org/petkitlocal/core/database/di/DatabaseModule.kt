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
            // Going back to an older app keeps nothing it cannot read; the server has it all.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun deviceDao(database: PetkitDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun petDao(database: PetkitDatabase): PetDao = database.petDao()

    @Provides
    fun timelineDao(database: PetkitDatabase): TimelineDao = database.timelineDao()
}

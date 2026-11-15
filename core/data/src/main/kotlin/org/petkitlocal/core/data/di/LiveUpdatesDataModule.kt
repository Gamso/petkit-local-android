package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.petkitlocal.core.data.LiveUpdatesRepositoryImpl
import org.petkitlocal.core.domain.repository.LiveUpdatesRepository

/**
 * The scope the shared `/ws` socket lives in: it outlives the screens that collect it, so moving
 * from one screen to the next does not reopen it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LiveUpdatesCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveUpdatesDataModule {
    @Binds
    abstract fun liveUpdatesRepository(impl: LiveUpdatesRepositoryImpl): LiveUpdatesRepository

    companion object {
        @Provides
        @Singleton
        @LiveUpdatesCoroutineScope
        fun liveUpdatesCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

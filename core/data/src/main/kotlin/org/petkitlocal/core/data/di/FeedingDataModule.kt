package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.petkitlocal.core.data.FeedingRepositoryImpl
import org.petkitlocal.core.data.FeedingStore
import org.petkitlocal.core.domain.auth.SessionScopedCache
import org.petkitlocal.core.domain.repository.FeedingRepository

/** Feeding plans and one-off feeds, cleared with the session like every other cache. */
@Module
@InstallIn(SingletonComponent::class)
interface FeedingDataModule {
    @Binds
    fun feedingRepository(impl: FeedingRepositoryImpl): FeedingRepository

    @Binds
    @IntoSet
    fun feedingSessionCache(impl: FeedingStore): SessionScopedCache
}

package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.petkitlocal.core.data.DeviceRepositoryImpl
import org.petkitlocal.core.data.DeviceSessionCache
import org.petkitlocal.core.data.PetRepositoryImpl
import org.petkitlocal.core.data.TimelineRepositoryImpl
import org.petkitlocal.core.domain.auth.SessionScopedCache
import org.petkitlocal.core.domain.repository.DeviceRepository
import org.petkitlocal.core.domain.repository.PetRepository
import org.petkitlocal.core.domain.repository.TimelineRepository

/** The cache-backed repositories of devices, pets and the timeline, and the cache the session clears. */
@Module
@InstallIn(SingletonComponent::class)
interface DeviceDataModule {
    @Binds
    fun deviceRepository(impl: DeviceRepositoryImpl): DeviceRepository

    @Binds
    fun petRepository(impl: PetRepositoryImpl): PetRepository

    @Binds
    fun timelineRepository(impl: TimelineRepositoryImpl): TimelineRepository

    @Binds
    @IntoSet
    fun deviceSessionCache(impl: DeviceSessionCache): SessionScopedCache
}

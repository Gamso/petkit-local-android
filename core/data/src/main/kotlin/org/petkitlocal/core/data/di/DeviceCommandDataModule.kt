package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.core.data.DeviceCommandRepositoryImpl
import org.petkitlocal.core.domain.repository.DeviceCommandRepository

/** A device's buttons and settings, written to the server first (ADR 0002). */
@Module
@InstallIn(SingletonComponent::class)
interface DeviceCommandDataModule {
    @Binds
    fun deviceCommandRepository(impl: DeviceCommandRepositoryImpl): DeviceCommandRepository
}

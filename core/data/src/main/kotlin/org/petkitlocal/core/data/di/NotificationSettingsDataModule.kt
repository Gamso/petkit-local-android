package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.petkitlocal.core.data.NotificationSettingsRepositoryImpl
import org.petkitlocal.core.domain.settings.NotificationSettingsRepository

/** The phone's notification kinds, held by the server with the session. */
@Module
@InstallIn(SingletonComponent::class)
interface NotificationSettingsDataModule {
    @Binds
    fun notificationSettingsRepository(impl: NotificationSettingsRepositoryImpl): NotificationSettingsRepository
}

package org.petkitlocal.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.petkitlocal.core.data.NotificationCursorCache
import org.petkitlocal.core.data.NotificationFeedImpl
import org.petkitlocal.core.data.NotificationPreferencesRepositoryImpl
import org.petkitlocal.core.domain.auth.SessionScopedCache
import org.petkitlocal.core.domain.notifications.NotificationFeed
import org.petkitlocal.core.domain.notifications.NotificationPreferencesRepository

/** The notification socket and the phone's own notification preferences (ADR 0007). */
@Module
@InstallIn(SingletonComponent::class)
interface NotificationFeedDataModule {
    @Binds
    fun notificationFeed(impl: NotificationFeedImpl): NotificationFeed

    @Binds
    fun notificationPreferencesRepository(
        impl: NotificationPreferencesRepositoryImpl,
    ): NotificationPreferencesRepository

    @Binds
    @IntoSet
    fun notificationCursorCache(impl: NotificationCursorCache): SessionScopedCache
}

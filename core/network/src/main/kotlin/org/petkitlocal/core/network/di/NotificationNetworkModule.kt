package org.petkitlocal.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.petkitlocal.core.network.NotificationSocket
import org.petkitlocal.core.network.NotificationSocketClient

/** The notification socket, on the shared client. */
@Module
@InstallIn(SingletonComponent::class)
object NotificationNetworkModule {
    @Provides
    fun notificationSocket(client: OkHttpClient): NotificationSocket = NotificationSocketClient(client)
}

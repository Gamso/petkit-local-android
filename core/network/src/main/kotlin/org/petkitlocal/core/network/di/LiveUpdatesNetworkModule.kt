package org.petkitlocal.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.petkitlocal.core.network.LiveUpdatesClient
import org.petkitlocal.core.network.LiveUpdatesSocket

/** The `/ws` socket, on the shared client. */
@Module
@InstallIn(SingletonComponent::class)
object LiveUpdatesNetworkModule {
    @Provides
    fun liveUpdatesSocket(client: OkHttpClient): LiveUpdatesSocket = LiveUpdatesClient(client)
}

package org.petkitlocal.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient
import org.petkitlocal.core.network.ServerAddressProvider
import org.petkitlocal.core.network.auth.SessionEndListener
import org.petkitlocal.core.network.auth.SessionTokenProvider

/**
 * The shared HTTP stack. [ServerAddressProvider], [SessionTokenProvider] and
 * [SessionEndListener] are bound by core:data, on top of the session store.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(
        serverAddress: ServerAddressProvider,
        tokenProvider: SessionTokenProvider,
        sessionEnded: SessionEndListener,
    ): OkHttpClient = PetkitHttpClient.okHttpClient(serverAddress, tokenProvider, sessionEnded)

    @Provides
    @Singleton
    fun petkitApi(client: OkHttpClient): PetkitApi = PetkitHttpClient.api(client)
}

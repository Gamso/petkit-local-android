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
import org.petkitlocal.core.network.auth.SessionTokenProvider

/**
 * The shared HTTP stack. [ServerAddressProvider] and [SessionTokenProvider] are bound by
 * core:data once the session store exists (M1); until something asks for the client, Hilt does
 * not need them.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun okHttpClient(serverAddress: ServerAddressProvider, tokenProvider: SessionTokenProvider): OkHttpClient =
        PetkitHttpClient.okHttpClient(serverAddress, tokenProvider)

    @Provides
    @Singleton
    fun petkitApi(client: OkHttpClient): PetkitApi = PetkitHttpClient.api(client)
}

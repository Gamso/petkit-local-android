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
import org.petkitlocal.core.network.tls.CertificatePinStore
import org.petkitlocal.core.network.tls.ServerTrust

/**
 * The shared HTTP stack. [ServerAddressProvider], [SessionTokenProvider], [SessionEndListener]
 * and [CertificatePinStore] are bound by core:data, on top of the session store.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun serverTrust(pins: CertificatePinStore): ServerTrust = ServerTrust(pins)

    @Provides
    @Singleton
    fun okHttpClient(
        serverAddress: ServerAddressProvider,
        tokenProvider: SessionTokenProvider,
        sessionEnded: SessionEndListener,
        trust: ServerTrust,
    ): OkHttpClient = PetkitHttpClient.okHttpClient(serverAddress, tokenProvider, sessionEnded, trust = trust)

    @Provides
    @Singleton
    fun petkitApi(client: OkHttpClient): PetkitApi = PetkitHttpClient.api(client)
}

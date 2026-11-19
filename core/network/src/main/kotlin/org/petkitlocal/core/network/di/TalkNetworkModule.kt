package org.petkitlocal.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.petkitlocal.core.network.TalkSocket
import org.petkitlocal.core.network.TalkSocketClient
import org.petkitlocal.core.network.auth.SessionEndListener

/** The talk socket, on the shared client. */
@Module
@InstallIn(SingletonComponent::class)
object TalkNetworkModule {
    @Provides
    fun talkSocket(client: OkHttpClient, sessionEnded: SessionEndListener): TalkSocket =
        TalkSocketClient(client, sessionEnded)
}

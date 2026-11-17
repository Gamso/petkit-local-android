package org.petkitlocal.core.datastore.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.petkitlocal.core.datastore.KeystoreTokenCipher
import org.petkitlocal.core.datastore.NotificationStore
import org.petkitlocal.core.datastore.QuickActionsStore
import org.petkitlocal.core.datastore.SessionStore
import org.petkitlocal.core.datastore.TokenCipher

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /** The session file's name under `files/datastore/` (`session.preferences_pb`). */
    const val SESSION_FILE = "session"

    /** The actions each device keeps in view (`quick_actions.preferences_pb`). */
    const val QUICK_ACTIONS_FILE = "quick_actions"

    /** Notifications on this phone, and the last one received (`notifications.preferences_pb`). */
    const val NOTIFICATIONS_FILE = "notifications"

    @Provides
    @Singleton
    fun tokenCipher(): TokenCipher = KeystoreTokenCipher()

    /** One instance per file and process: DataStore refuses two on the same file. */
    @Provides
    @Singleton
    fun sessionStore(@ApplicationContext context: Context, cipher: TokenCipher): SessionStore = SessionStore(
        dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SESSION_FILE) },
        cipher = cipher,
    )

    @Provides
    @Singleton
    fun quickActionsStore(@ApplicationContext context: Context): QuickActionsStore = QuickActionsStore(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(QUICK_ACTIONS_FILE) },
    )

    @Provides
    @Singleton
    fun notificationStore(@ApplicationContext context: Context): NotificationStore = NotificationStore(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(NOTIFICATIONS_FILE) },
    )
}

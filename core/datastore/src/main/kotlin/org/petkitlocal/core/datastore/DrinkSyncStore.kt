package org.petkitlocal.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * When each fountain's drink history was last synced over Bluetooth from this phone (issue #104):
 * one Unix-milliseconds entry per accessory id. Only that time: the pairing secret a sync uses is
 * never written anywhere.
 *
 * Accessory ids are the server's, so the file names the server they belong to ([keepOnlyFor]), as
 * [QuickActionsStore] does.
 */
class DrinkSyncStore(private val dataStore: DataStore<Preferences>) {
    /** [accessoryId]'s last sync, in Unix milliseconds; null if never. An unreadable file reads as never. */
    fun lastSync(accessoryId: Long): Flow<Long?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[key(accessoryId)] }
        .distinctUntilChanged()

    /** Throws [IOException] when the file cannot be written. */
    suspend fun setLastSync(accessoryId: Long, epochMillis: Long) {
        dataStore.edit { it[key(accessoryId)] = epochMillis }
    }

    /** Forgets every entry unless [serverBaseUrl] is the server they were made on. Throws [IOException]. */
    suspend fun keepOnlyFor(serverBaseUrl: String) {
        dataStore.edit { preferences ->
            if (preferences[SERVER] != serverBaseUrl) {
                preferences.clear()
                preferences[SERVER] = serverBaseUrl
            }
        }
    }

    private fun key(accessoryId: Long) = longPreferencesKey("accessory_$accessoryId")

    private companion object {
        val SERVER = stringPreferencesKey("server_base_url")
    }
}

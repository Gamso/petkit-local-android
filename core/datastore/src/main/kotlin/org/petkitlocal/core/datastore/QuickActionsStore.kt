package org.petkitlocal.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The actions each device keeps in view on its collapsed sheet, as the user chose them: one
 * entry per device id, the action keys in order. No entry means the app chooses; an empty one
 * means the user chose none. Nothing here is secret, so nothing is encrypted.
 *
 * Device ids are the server's, so the file also names the server its choices were made on
 * ([keepOnlyFor]); they outlive any session on that server.
 */
class QuickActionsStore(private val dataStore: DataStore<Preferences>) {
    /** [deviceId]'s keys in order, or null when none were chosen. An unreadable file reads as none. */
    fun pinned(deviceId: Long): Flow<List<String>?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { preferences -> preferences[key(deviceId)]?.let(::decode) }
        .distinctUntilChanged()

    /**
     * Replaces [deviceId]'s keys with [transform] of the stored ones, in one transaction; null
     * removes the entry. A key that could not be stored as it is (empty, or holding the
     * separator) is left out. Throws [IOException] when the file cannot be written.
     */
    suspend fun update(deviceId: Long, transform: (List<String>?) -> List<String>?) {
        dataStore.edit { preferences ->
            val key = key(deviceId)
            when (val next = transform(preferences[key]?.let(::decode))) {
                null -> preferences.remove(key)
                else -> preferences[key] = encode(next)
            }
        }
    }

    /**
     * Keeps the choices when [serverBaseUrl] (normalised) is the server they were made on, and
     * forgets them all otherwise, then records it as theirs. Called when a sign-in succeeds.
     * Throws [IOException] when the file cannot be written.
     */
    suspend fun keepOnlyFor(serverBaseUrl: String) {
        dataStore.edit { preferences ->
            if (preferences[SERVER] != serverBaseUrl) {
                preferences.clear()
                preferences[SERVER] = serverBaseUrl
            }
        }
    }

    private fun key(deviceId: Long) = stringPreferencesKey("device_$deviceId")

    private fun encode(keys: List<String>): String =
        keys.filter { it.isNotEmpty() && SEPARATOR !in it }.joinToString(SEPARATOR)

    private fun decode(text: String): List<String> = text.split(SEPARATOR).filter { it.isNotEmpty() }

    private companion object {
        /** The server the choices were made on; device entries are `device_<id>`. */
        val SERVER = stringPreferencesKey("server_base_url")

        /** Action keys are snake_case words (`cleaning_start`): a line break is never one of them. */
        const val SEPARATOR = "\n"
    }
}

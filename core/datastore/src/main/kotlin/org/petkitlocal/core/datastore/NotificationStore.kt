package org.petkitlocal.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The phone's side of its notifications (ARCHITECTURE.md §5): whether they are on, the id of the
 * last one received from the session's socket (the `after` of the next connection), and whether
 * the user was already asked for Android's notification permission and the battery exemption.
 * Nothing here is secret. An unreadable file reads as the defaults: on, no id, nothing asked.
 */
class NotificationStore(private val dataStore: DataStore<Preferences>) {
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val enabled: Flow<Boolean> = preferences.map { it[ENABLED] ?: true }.distinctUntilChanged()

    val permissionAsked: Flow<Boolean> = preferences.map { it[PERMISSION_ASKED] ?: false }.distinctUntilChanged()

    val batteryAsked: Flow<Boolean> = preferences.map { it[BATTERY_ASKED] ?: false }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) = edit { it[ENABLED] = enabled }

    suspend fun markPermissionAsked() = edit { it[PERMISSION_ASKED] = true }

    suspend fun markBatteryAsked() = edit { it[BATTERY_ASKED] = true }

    /** The last notification id received, or null before the session's first connection. */
    suspend fun lastId(): Long? = preferences.first()[LAST_ID]

    suspend fun saveLastId(id: Long) = edit { it[LAST_ID] = id }

    /** Forgets the last id: the next session starts from its first connection. */
    suspend fun clearLastId() = edit { it.remove(LAST_ID) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LAST_ID = longPreferencesKey("last_id")
        val PERMISSION_ASKED = booleanPreferencesKey("permission_asked")
        val BATTERY_ASKED = booleanPreferencesKey("battery_asked")
    }
}

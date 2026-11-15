package org.petkitlocal.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What is kept on disk about the session.
 *
 * @property serverBaseUrl the server of the last session; kept after signing out, to prefill
 *   the sign-in form.
 * @property token null when signed out, or when the stored one can no longer be decrypted.
 * @property endedReason why the server ended the last session, until the next sign-in, so the
 *   sign-in screen can still say it after the app was closed.
 */
data class StoredSession(
    val serverBaseUrl: String? = null,
    val token: String? = null,
    val sessionId: String? = null,
    val endedReason: String? = null,
)

/**
 * The session in DataStore, its token sealed by [cipher] (ADR 0004). The password is never
 * given to this class.
 */
class SessionStore(private val dataStore: DataStore<Preferences>, private val cipher: TokenCipher) {
    val session: Flow<StoredSession> = dataStore.data
        // An unreadable file reads as empty, that is signed out; the next write replaces it.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)

    suspend fun current(): StoredSession = session.first()

    /** Stores a new session; the token is encrypted before it reaches the disk. */
    suspend fun saveSignIn(serverBaseUrl: String, token: String, sessionId: String) {
        val sealed = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
        dataStore.edit {
            it[SERVER] = serverBaseUrl
            it[TOKEN] = encode(sealed.ciphertext)
            it[TOKEN_IV] = encode(sealed.iv)
            it[SESSION_ID] = sessionId
            it.remove(ENDED_REASON)
        }
    }

    /** Forgets the session but not the server's address; [endedReason] stays for the screen. */
    suspend fun clearSession(endedReason: String?) {
        dataStore.edit {
            it.remove(TOKEN)
            it.remove(TOKEN_IV)
            it.remove(SESSION_ID)
            if (endedReason == null) it.remove(ENDED_REASON) else it[ENDED_REASON] = endedReason
        }
    }

    private fun read(preferences: Preferences): StoredSession {
        val ciphertext = preferences[TOKEN]?.let(::decode)
        val iv = preferences[TOKEN_IV]?.let(::decode)
        val token = if (ciphertext != null && iv != null) {
            cipher.decrypt(SealedSecret(iv, ciphertext))?.toString(Charsets.UTF_8)
        } else {
            null
        }
        return StoredSession(
            serverBaseUrl = preferences[SERVER],
            token = token,
            sessionId = preferences[SESSION_ID]?.takeIf { token != null },
            endedReason = preferences[ENDED_REASON],
        )
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray? = try {
        Base64.getDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        val SERVER = stringPreferencesKey("server_base_url")
        val TOKEN = stringPreferencesKey("token_ciphertext")
        val TOKEN_IV = stringPreferencesKey("token_iv")
        val SESSION_ID = stringPreferencesKey("session_id")
        val ENDED_REASON = stringPreferencesKey("session_ended_reason")
    }
}

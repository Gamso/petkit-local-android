package org.petkitlocal.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SessionStoreTest {
    @TempDir
    lateinit var dir: File

    /** The same AES-GCM as the Keystore cipher, with a software key: the JVM has no Keystore. */
    private class SoftwareCipher(var key: SecretKey = newKey()) : TokenCipher {
        override fun encrypt(plain: ByteArray): SealedSecret {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            return SealedSecret(iv, cipher.doFinal(plain))
        }

        override fun decrypt(sealed: SealedSecret): ByteArray? = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed.iv))
                doFinal(sealed.ciphertext)
            }
        } catch (_: GeneralSecurityException) {
            null
        }

        companion object {
            fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
    }

    private val file get() = File(dir, "session.preferences_pb")

    private fun TestScope.store(cipher: TokenCipher, scope: CoroutineScope = backgroundScope) =
        SessionStore(PreferenceDataStoreFactory.create(scope = scope) { file }, cipher)

    @Test
    fun `a saved session reads back, and the token is not on disk in clear`() = runTest {
        val store = store(SoftwareCipher())

        store.saveSignIn("https://pets.example.com", "s3cret-token-value", "abc123")

        assertEquals(
            StoredSession("https://pets.example.com", "s3cret-token-value", "abc123", null),
            store.current(),
        )
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("s3cret-token-value"))
    }

    @Test
    fun `clearing keeps the server, for the next sign-in, and the reason, for the screen`() = runTest {
        val store = store(SoftwareCipher())
        store.saveSignIn("https://pets.example.com", "token", "abc123")

        store.clearSession(endedReason = "InvalidToken")

        assertEquals(StoredSession("https://pets.example.com", null, null, "InvalidToken"), store.current())
    }

    @Test
    fun `a new sign-in forgets why the last session ended`() = runTest {
        val store = store(SoftwareCipher())
        store.clearSession(endedReason = "InvalidToken")

        store.saveSignIn("https://pets.example.com", "token", "abc123")

        assertNull(store.current().endedReason)
    }

    @Test
    fun `a token sealed with a lost key reads as signed out, not as an error`() = runTest {
        val cipher = SoftwareCipher()
        val store = store(cipher)
        store.saveSignIn("https://pets.example.com", "token", "abc123")

        cipher.key = SoftwareCipher.newKey()

        assertEquals(StoredSession(serverBaseUrl = "https://pets.example.com"), store.current())
    }

    @Test
    fun `a damaged file reads as signed out`() = runTest {
        file.writeText("not a protobuf at all")

        assertEquals(StoredSession(), store(SoftwareCipher()).current())
    }
}

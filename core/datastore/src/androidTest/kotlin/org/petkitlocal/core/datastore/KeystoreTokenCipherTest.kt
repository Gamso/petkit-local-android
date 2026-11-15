package org.petkitlocal.core.datastore

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** On a real device or emulator: the Android Keystore does not exist on the JVM. */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
    private val cipher = KeystoreTokenCipher(alias = "petkit_local_test_key")
    private val token = "a-session-token".toByteArray()

    @After
    fun deleteKey() = cipher.deleteKey()

    @Test
    fun sealsAndOpensWithAKeystoreKey() {
        val sealed = cipher.encrypt(token)

        assertFalse(sealed.ciphertext.contentEquals(token))
        assertArrayEquals(token, cipher.decrypt(sealed))
    }

    @Test
    fun eachSealUsesAFreshNonce() {
        assertFalse(cipher.encrypt(token).iv.contentEquals(cipher.encrypt(token).iv))
    }

    @Test
    fun anAlteredCiphertextDoesNotOpen() {
        val sealed = cipher.encrypt(token)
        sealed.ciphertext[0] = (sealed.ciphertext[0].toInt() xor 1).toByte()

        assertNull(cipher.decrypt(sealed))
    }

    @Test
    fun aDeletedKeyMeansSignedOutNotACrash() {
        val sealed = cipher.encrypt(token)
        cipher.deleteKey()

        assertNull(cipher.decrypt(sealed))
    }
}

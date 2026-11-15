package org.petkitlocal.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A secret sealed by a [TokenCipher]: the AES-GCM nonce and the ciphertext with its tag. */
class SealedSecret(val iv: ByteArray, val ciphertext: ByteArray)

/** Seals the session token before it is written to disk (ADR 0004). */
interface TokenCipher {
    /** @throws GeneralSecurityException when the key cannot be made or used. */
    fun encrypt(plain: ByteArray): SealedSecret

    /**
     * Null when [sealed] cannot be opened: the key is gone (a lock-screen reset invalidates it on
     * some phones), or the data was altered. The caller treats that as "signed out".
     */
    fun decrypt(sealed: SealedSecret): ByteArray?
}

/**
 * AES-256-GCM with a key generated inside the Android Keystore: it never leaves the secure
 * hardware where the phone has it, and cannot be exported or backed up.
 */
class KeystoreTokenCipher(private val alias: String = DEFAULT_ALIAS) : TokenCipher {
    override fun encrypt(plain: ByteArray): SealedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: newKey())
        return SealedSecret(iv = cipher.iv, ciphertext = cipher.doFinal(plain))
    }

    override fun decrypt(sealed: SealedSecret): ByteArray? = try {
        existingKey()?.let { key ->
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed.iv))
                doFinal(sealed.ciphertext)
            }
        }
    } catch (_: GeneralSecurityException) { // AEADBadTagException, KeyPermanentlyInvalidatedException...
        null
    } catch (_: IllegalArgumentException) { // an IV of the wrong length
        null
    }

    /** Forgets the key: everything sealed with it becomes unreadable. */
    fun deleteKey() {
        keyStore().deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun newKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "petkit_local_session_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
    }
}

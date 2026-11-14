package com.onenotes.app.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the body of the notes protected with the fingerprint / PIN lock.
 *
 * The AES key lives inside the Android Keystore, so it never enters the process memory and it
 * cannot be extracted from a backup of the app data. The ciphertext (IV + encrypted bytes) is
 * stored Base64 encoded in the database with the [PREFIX] marker, which makes it possible to
 * tell an encrypted body from a plain one.
 *
 * If the KeyStore is not available (very old or heavily customised devices) the text is kept in
 * clear text rather than lost: the note stays readable, only the extra protection is missing.
 */
object CryptoManager {

    private const val TAG = "CryptoManager"
    private const val ALIAS = "onenotes_note_content"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc1:"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, payload, 0, iv.size)
            System.arraycopy(encrypted, 0, payload, iv.size, encrypted.size)
            PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to encrypt the note, keeping it in clear text", t)
            plain
        }
    }

    fun decrypt(value: String): String {
        if (!isEncrypted(value)) return value
        return try {
            val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to decrypt the note", t)
            ""
        }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(ALIAS, null)?.let { return it as SecretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}

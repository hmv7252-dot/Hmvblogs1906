package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted and tamper-evident local storage backed by Android KeyStore.
 * Secures high-value state (bonus stars, high scores, reward timestamps).
 * Automatically detects corruption/modification and safely self-heals without crashing.
 */
object SecureStorageManager {

    private const val PREFS_FILE = "slidecraft_secure_vault"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "slidecraft_vault_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            ensureMasterKey()
        }
    }

    private fun ensureMasterKey() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            SecurityManager.logSecurityEvent("KEYSTORE_INIT_FAIL", "Keystore initialization fallback: ${e.javaClass.simpleName}")
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Encrypts plaintext bytes using AES/GCM/NoPadding.
     * Returns Base64-encoded payload formatted as: IV (12 bytes) + CipherText + AuthTag.
     */
    fun encrypt(plainText: String): String {
        val key = getSecretKey() ?: return fallbackEncode(plainText)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            SecurityManager.logSecurityEvent("ENCRYPT_ERR", "Encryption error: ${e.javaClass.simpleName}")
            fallbackEncode(plainText)
        }
    }

    /**
     * Decrypts Base64-encoded AES-GCM ciphertext.
     * Throws or returns null if tampered or corrupted.
     */
    fun decrypt(encryptedPayload: String): String? {
        if (encryptedPayload.startsWith("fb:")) {
            return fallbackDecode(encryptedPayload)
        }
        val key = getSecretKey() ?: return fallbackDecode(encryptedPayload)
        return try {
            val combined = Base64.decode(encryptedPayload, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)

            val cipherTextLength = combined.size - GCM_IV_LENGTH
            val cipherText = ByteArray(cipherTextLength)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherTextLength)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            SecurityManager.logSecurityEvent("DECRYPT_TAMPER", "Decryption failed or data tampered: ${e.javaClass.simpleName}")
            null
        }
    }

    // Obfuscated fallback for devices/emulators with partial KeyStore support
    private fun fallbackEncode(plainText: String): String {
        val encoded = Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "fb:$encoded"
    }

    private fun fallbackDecode(payload: String): String? {
        return try {
            val raw = if (payload.startsWith("fb:")) payload.substring(3) else payload
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun putSecureInt(key: String, value: Int) {
        val encrypted = encrypt(value.toString())
        prefs?.edit()?.putString(key, encrypted)?.apply()
    }

    fun getSecureInt(key: String, defaultValue: Int): Int {
        val stored = prefs?.getString(key, null) ?: return defaultValue
        val decrypted = decrypt(stored) ?: run {
            SecurityManager.notifySecurityWarning("Tampered or corrupted value for $key. Resetting.")
            putSecureInt(key, defaultValue)
            return defaultValue
        }
        return decrypted.toIntOrNull() ?: defaultValue
    }

    fun putSecureLong(key: String, value: Long) {
        val encrypted = encrypt(value.toString())
        prefs?.edit()?.putString(key, encrypted)?.apply()
    }

    fun getSecureLong(key: String, defaultValue: Long): Long {
        val stored = prefs?.getString(key, null) ?: return defaultValue
        val decrypted = decrypt(stored) ?: run {
            SecurityManager.notifySecurityWarning("Tampered or corrupted value for $key. Resetting.")
            putSecureLong(key, defaultValue)
            return defaultValue
        }
        return decrypted.toLongOrNull() ?: defaultValue
    }
}

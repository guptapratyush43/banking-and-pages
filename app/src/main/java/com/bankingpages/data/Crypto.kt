package com.bankingpages.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Everything the vault writes to disk goes through here: AES-256-GCM with a key
 * that lives in the phone's hardware keystore and never leaves it. Output is
 * a 12-byte IV followed by the ciphertext.
 */
object Crypto {
    private const val ALIAS = "banking_pages_vault"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private val key: SecretKey by lazy {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: run {
            val gen = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            gen.generateKey()
        }
    }

    fun encrypt(plain: ByteArray): ByteArray = seal(key, plain)
    fun decrypt(data: ByteArray): ByteArray = open(key, data)

    fun seal(k: SecretKey, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        if (k is SecretKeySpec) c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, random(12))) else c.init(Cipher.ENCRYPT_MODE, k)
        return c.iv + c.doFinal(plain)
    }

    fun open(k: SecretKey, data: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }

    /** A key from the user's backup password, so a backup opens on any phone that knows it. */
    fun passwordKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 150_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun random(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
}

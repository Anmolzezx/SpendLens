package com.spendlens.core.database.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * The passphrase SQLCipher encrypts the database with, and whether it had to be created.
 *
 * [isNew] tells the caller there is no point trying to open an existing encrypted file: whatever key
 * it was written with is gone.
 */
class DatabaseKey(
    val passphrase: ByteArray,
    val isNew: Boolean,
)

interface DatabasePassphrase {
    fun getOrCreate(): DatabaseKey
}

/**
 * A random 256-bit passphrase, itself encrypted with a key that never leaves the device's keystore.
 *
 * The keystore key is hardware-backed wherever the device offers it, and cannot be exported — so a copy
 * of the database file taken off the device (a backup, a rooted file pull) is unreadable without that
 * particular phone.
 *
 * **No user authentication is required to use the key.** Tying it to a fingerprint would be stronger,
 * but background sync runs while nobody is holding the phone, and it would have nothing to open. App
 * lock covers the case this does not: someone holding the unlocked device.
 *
 * The passphrase is stored as **hex text**: SQLCipher's `ATTACH … KEY ?` takes a string, and raw random
 * bytes are not valid text. Hex of 32 random bytes is 256 bits either way.
 */
internal class KeystoreDatabasePassphrase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DatabasePassphrase {
        @Synchronized
        override fun getOrCreate(): DatabaseKey =
            when (val existing = readPassphrase()) {
                null -> DatabaseKey(createPassphrase(), isNew = true)
                else -> DatabaseKey(existing, isNew = false)
            }

        private fun readPassphrase(): ByteArray? {
            val file = passphraseFile()
            if (!file.exists()) return null
            return try {
                val stored = file.readBytes()
                val key = keystoreKey() ?: return null
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(GCM_TAG_BITS, stored.copyOfRange(0, GCM_IV_BYTES)),
                )
                cipher.doFinal(stored.copyOfRange(GCM_IV_BYTES, stored.size))
            } catch (e: GeneralSecurityException) {
                // The keystore key is gone or no longer usable — after some device-level changes it is
                // permanently invalidated. The database it protected can no longer be read by anyone.
                Log.w(TAG, "Stored database passphrase could not be decrypted; starting a new one", e)
                null
            } catch (e: IOException) {
                Log.w(TAG, "Stored database passphrase could not be read; starting a new one", e)
                null
            }
        }

        private fun createPassphrase(): ByteArray {
            val random = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
            val passphrase = random.joinToString(separator = "") { "%02x".format(it) }.toByteArray(Charsets.US_ASCII)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, createKeystoreKey())
            val encrypted = cipher.doFinal(passphrase)
            passphraseFile().writeBytes(cipher.iv + encrypted)
            return passphrase
        }

        private fun keystoreKey(): SecretKey? {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        }

        private fun createKeystoreKey(): SecretKey =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                init(
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(AES_KEY_BITS)
                        .build(),
                )
                generateKey()
            }

        /**
         * `noBackupFilesDir`, so this file is never copied into a cloud backup. It would be useless
         * elsewhere — the keystore key that decrypts it cannot leave this device — and a restored copy
         * would only look like a key that ought to work.
         */
        private fun passphraseFile() = File(context.noBackupFilesDir, PASSPHRASE_FILE)

        private companion object {
            const val TAG = "DatabasePassphrase"
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val KEY_ALIAS = "spendlens_database_passphrase"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val PASSPHRASE_FILE = "database_passphrase.bin"
            const val PASSPHRASE_BYTES = 32
            const val AES_KEY_BITS = 256
            const val GCM_IV_BYTES = 12
            const val GCM_TAG_BITS = 128
        }
    }

package com.robloxvault.app.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Optional app-lock. Stores only a salted SHA-256 hash of the user's PIN in an
 * encrypted prefs file — the PIN itself is never persisted. Guards access to
 * the credential list when the app is opened.
 */
class LockManager(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "vault_lock",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun biometricEnabled(): Boolean = prefs.getBoolean(KEY_BIO, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIO, enabled).apply()
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash(pin, salt))
            .apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_BIO).apply()
    }

    fun setupSkipped(): Boolean = prefs.getBoolean(KEY_SKIPPED, false)

    fun markSetupSkipped() {
        prefs.edit().putBoolean(KEY_SKIPPED, true).apply()
    }

    fun verify(pin: String): Boolean {
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        return hash(pin, saltHex.fromHex()).equals(expected, ignoreCase = true)
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        return md.digest(pin.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_BIO = "biometric_enabled"
        private const val KEY_SKIPPED = "setup_skipped"
    }
}

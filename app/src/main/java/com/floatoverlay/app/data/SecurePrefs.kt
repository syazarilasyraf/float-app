package com.floatoverlay.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for sensitive values like API keys.
 *
 * Uses AndroidX Security with AES-256. Keys are never written to plain text.
 * If the device cannot create an encrypted store, it falls back to a regular
 * private SharedPreferences as a last resort.
 */
object SecurePrefs {

    private const val FILE_NAME = "float_secure_prefs"
    private const val FALLBACK_FILE_NAME = "float_secure_fallback_prefs"

    private fun getPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback if Keystore/encryption is unavailable on this device.
            context.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun putString(context: Context, key: String, value: String?) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    fun getString(context: Context, key: String, default: String? = null): String? {
        return getPrefs(context).getString(key, default)
    }

    fun remove(context: Context, key: String) {
        getPrefs(context).edit().remove(key).apply()
    }
}

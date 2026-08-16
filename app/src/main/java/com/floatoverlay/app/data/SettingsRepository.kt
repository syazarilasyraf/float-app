package com.floatoverlay.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * User settings for the AI assistant and workspace.
 *
 * For v1 this only stores the selected provider name. In the future it can
 * hold secure provider configuration references, default overlay sizes, etc.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedProviderName(): String {
        return prefs.getString(KEY_PROVIDER, "mock") ?: "mock"
    }

    fun setSelectedProviderName(name: String) {
        prefs.edit().putString(KEY_PROVIDER, name).apply()
    }

    companion object {
        private const val PREFS_NAME = "FloatSettingsPrefs"
        private const val KEY_PROVIDER = "ai_provider"
    }
}

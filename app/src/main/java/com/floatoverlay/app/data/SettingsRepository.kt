package com.floatoverlay.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * User settings for the AI assistant and workspace.
 *
 * API keys are not stored here; they live in [SecurePrefs]. This file only
 * keeps non-sensitive configuration like the selected provider and model.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedProviderName(): String {
        return prefs.getString(KEY_PROVIDER, PROVIDER_MOCK) ?: PROVIDER_MOCK
    }

    fun setSelectedProviderName(name: String) {
        prefs.edit().putString(KEY_PROVIDER, name).apply()
    }

    fun getKimiModel(): String {
        return prefs.getString(KEY_KIMI_MODEL, "moonshot-v1-8k") ?: "moonshot-v1-8k"
    }

    fun setKimiModel(model: String) {
        prefs.edit().putString(KEY_KIMI_MODEL, model).apply()
    }

    companion object {
        private const val PREFS_NAME = "FloatSettingsPrefs"
        private const val KEY_PROVIDER = "ai_provider"
        private const val KEY_KIMI_MODEL = "kimi_model"

        const val PROVIDER_MOCK = "mock"
        const val PROVIDER_KIMI = "kimi"
    }
}

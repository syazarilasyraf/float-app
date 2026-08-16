package com.floatoverlay.app.ai

import android.content.Context
import com.floatoverlay.app.ai.provider.MockAIProvider
import com.floatoverlay.app.ai.provider.OpenAICompatibleProvider
import com.floatoverlay.app.data.SecurePrefs
import com.floatoverlay.app.data.SettingsRepository

object AIProviderFactory {

    private const val KEY_KIMI_API_KEY = "kimi_api_key"

    /**
     * Build the currently selected AI provider.
     *
     * Returns MockAIProvider if no real credentials are configured.
     */
    fun create(context: Context): AIProvider {
        val settings = SettingsRepository(context)
        return when (settings.getSelectedProviderName()) {
            SettingsRepository.PROVIDER_KIMI -> createKimiProvider(context, settings)
            else -> MockAIProvider()
        }
    }

    fun createKimiProvider(context: Context, settings: SettingsRepository): AIProvider {
        val apiKey = SecurePrefs.getString(context, KEY_KIMI_API_KEY) ?: ""
        if (apiKey.isBlank()) {
            return MockAIProvider()
        }
        return OpenAICompatibleProvider(
            name = "Kimi Code",
            baseUrl = "https://api.kimi.com/coding/v1",
            apiKey = apiKey,
            model = settings.getKimiModel()
        )
    }

    fun saveKimiApiKey(context: Context, apiKey: String?) {
        if (apiKey.isNullOrBlank()) {
            SecurePrefs.remove(context, KEY_KIMI_API_KEY)
        } else {
            SecurePrefs.putString(context, KEY_KIMI_API_KEY, apiKey)
        }
    }

    fun getKimiApiKey(context: Context): String? {
        return SecurePrefs.getString(context, KEY_KIMI_API_KEY)
    }
}

package com.floatoverlay.app

import android.content.Context
import android.content.SharedPreferences
import com.floatoverlay.app.model.OverlayConfig
import org.json.JSONArray
import org.json.JSONObject

class OverlayRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOverlays(): List<OverlayConfig> {
        val json = prefs.getString(KEY_OVERLAYS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                OverlayConfig.fromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveOverlays(overlays: List<OverlayConfig>) {
        val array = JSONArray()
        overlays.forEach { overlay ->
            array.put(overlay.toJson())
        }
        prefs.edit().putString(KEY_OVERLAYS, array.toString()).apply()
    }

    fun addOrUpdate(overlay: OverlayConfig) {
        val overlays = getOverlays().toMutableList()
        val index = overlays.indexOfFirst { it.id == overlay.id }
        if (index >= 0) {
            overlays[index] = overlay
        } else {
            overlays.add(overlay)
        }
        saveOverlays(overlays)
    }

    fun delete(id: String) {
        val overlays = getOverlays().filterNot { it.id == id }
        saveOverlays(overlays)
    }

    fun getEnabledOverlays(): List<OverlayConfig> {
        return getOverlays().filter { it.enabled }
    }

    fun getOverlay(id: String): OverlayConfig? {
        return getOverlays().find { it.id == id }
    }

    fun saveCounterState(state: String) {
        prefs.edit().putString(KEY_COUNTER_STATE, state).apply()
    }

    fun loadCounterState(): String {
        return prefs.getString(KEY_COUNTER_STATE, "") ?: ""
    }

    fun isAutoShowEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_SHOW, true)
    }

    fun setAutoShowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SHOW, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "FloatOverlayPrefs"
        private const val KEY_OVERLAYS = "overlays"
        private const val KEY_COUNTER_STATE = "counter_state"
        private const val KEY_AUTO_SHOW = "auto_show_overlays"
    }
}

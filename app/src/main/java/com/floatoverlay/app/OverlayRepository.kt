package com.floatoverlay.app

import android.content.Context
import android.content.SharedPreferences
import com.floatoverlay.app.model.HudSettings
import com.floatoverlay.app.model.LayoutPreset
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

    fun getPresets(): List<LayoutPreset> {
        val json = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                LayoutPreset.fromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePresets(presets: List<LayoutPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(preset.toJson())
        }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    fun addPreset(preset: LayoutPreset) {
        val presets = getPresets().toMutableList()
        val index = presets.indexOfFirst { it.name == preset.name }
        if (index >= 0) {
            presets[index] = preset
        } else {
            presets.add(preset)
        }
        savePresets(presets)
    }

    fun deletePreset(name: String) {
        val presets = getPresets().filterNot { it.name == name }
        savePresets(presets)
    }

    fun ensureDefaultPreset() {
        val presets = getPresets()
        if (presets.none { it.name == LayoutPreset.createDefaultCrPortraitHud().name }) {
            addPreset(LayoutPreset.createDefaultCrPortraitHud())
        }
    }

    fun ensureDefaultHudOverlay() {
        if (getOverlays().isNotEmpty()) return
        val hud = OverlayConfig(
            id = "hud_default",
            name = "HUD",
            url = "",
            overlayType = OverlayConfig.TYPE_LOCAL,
            assetName = "stream_hud.html",
            enabled = true,
            widthDp = 360,
            heightDp = 260,
            transparentBackground = true,
            touchThrough = true,
            locked = false,
            showResizeHandle = true,
            posXPercent = 0.05f,
            posYPercent = 0.05f
        )
        addOrUpdate(hud)
    }

    fun saveHudSettings(goalRM: Int, minutesPerRM: Int, capHours: Int) {
        val json = org.json.JSONObject().apply {
            put("goalRM", goalRM)
            put("minutesPerRM", minutesPerRM)
            put("capHours", capHours)
        }
        prefs.edit().putString(KEY_HUD_SETTINGS, json.toString()).apply()
    }

    fun loadHudSettings(): HudSettings {
        val jsonString = prefs.getString(KEY_HUD_SETTINGS, "") ?: ""
        return try {
            HudSettings.fromJson(JSONObject(jsonString))
        } catch (e: Exception) {
            HudSettings.default()
        }
    }

    companion object {
        private const val PREFS_NAME = "FloatOverlayPrefs"
        private const val KEY_OVERLAYS = "overlays"
        private const val KEY_COUNTER_STATE = "counter_state"
        private const val KEY_PRESETS = "layout_presets"
        private const val KEY_HUD_SETTINGS = "hud_settings"
    }
}

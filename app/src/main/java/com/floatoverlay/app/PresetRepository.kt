package com.floatoverlay.app

import android.content.Context
import android.content.SharedPreferences
import com.floatoverlay.app.model.WindowPreset
import org.json.JSONArray
import org.json.JSONObject

class PresetRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPresets(): List<WindowPreset> {
        val json = prefs.getString(KEY_PRESETS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                WindowPreset.fromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePresets(presets: List<WindowPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(preset.toJson())
        }
        prefs.edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    fun addOrUpdate(preset: WindowPreset) {
        val presets = getPresets().toMutableList()
        val index = presets.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            presets[index] = preset
        } else {
            presets.add(preset)
        }
        savePresets(presets)
    }

    fun delete(id: String) {
        val presets = getPresets().filterNot { it.id == id }
        savePresets(presets)
    }

    companion object {
        private const val PREFS_NAME = "FloatOverlayWindowPresets"
        private const val KEY_PRESETS = "window_presets"
    }
}

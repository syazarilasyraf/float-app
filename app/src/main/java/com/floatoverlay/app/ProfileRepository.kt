package com.floatoverlay.app

import android.content.Context
import android.content.SharedPreferences
import com.floatoverlay.app.model.OverlayProfile
import org.json.JSONArray
import org.json.JSONObject

class ProfileRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProfiles(): List<OverlayProfile> {
        val json = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            List(array.length()) { i ->
                OverlayProfile.fromJson(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProfiles(profiles: List<OverlayProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(profile.toJson())
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun addOrUpdate(profile: OverlayProfile) {
        val profiles = getProfiles().toMutableList()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }
        saveProfiles(profiles)
    }

    fun delete(id: String) {
        val profiles = getProfiles().filterNot { it.id == id }
        saveProfiles(profiles)
    }

    fun getProfile(id: String): OverlayProfile? {
        return getProfiles().find { it.id == id }
    }

    fun findByName(name: String): OverlayProfile? {
        return getProfiles().find { it.name == name }
    }

    fun isAutoApplyOnRotationEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_APPLY_ON_ROTATION, false)
    }

    fun setAutoApplyOnRotationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_APPLY_ON_ROTATION, enabled).apply()
    }

    fun recordManualApplyTime() {
        prefs.edit().putLong(KEY_LAST_MANUAL_APPLY_TIME, System.currentTimeMillis()).apply()
    }

    fun getLastManualApplyTime(): Long {
        return prefs.getLong(KEY_LAST_MANUAL_APPLY_TIME, 0L)
    }

    companion object {
        private const val PREFS_NAME = "FloatOverlayProfiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_AUTO_APPLY_ON_ROTATION = "auto_apply_profile_on_rotation"
        private const val KEY_LAST_MANUAL_APPLY_TIME = "last_manual_apply_time"
    }
}

package com.floatoverlay.app.model

import org.json.JSONObject
import java.util.UUID

data class WindowPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val widthPercent: Int,
    val heightPercent: Int,
    val xPercent: Int,
    val yPercent: Int,
    val isFullscreen: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("widthPercent", widthPercent)
        put("heightPercent", heightPercent)
        put("xPercent", xPercent)
        put("yPercent", yPercent)
        put("isFullscreen", isFullscreen)
    }

    companion object {
        fun fromJson(json: JSONObject): WindowPreset = WindowPreset(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", ""),
            widthPercent = json.optInt("widthPercent", 100),
            heightPercent = json.optInt("heightPercent", 100),
            xPercent = json.optInt("xPercent", 0),
            yPercent = json.optInt("yPercent", 0),
            isFullscreen = json.optBoolean("isFullscreen", false)
        )
    }
}

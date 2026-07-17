package com.floatoverlay.app.model

import org.json.JSONObject

data class OverlayConfig(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val widthDp: Int = 240,
    val heightDp: Int = 160,
    val opacityPercent: Int = 100,
    val backgroundColor: Int = 0xCC000000.toInt(),
    val cornerRadiusDp: Int = 16,
    val transparentBackground: Boolean = false
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("url", url)
            put("enabled", enabled)
            put("widthDp", widthDp)
            put("heightDp", heightDp)
            put("opacityPercent", opacityPercent)
            put("backgroundColor", backgroundColor)
            put("cornerRadiusDp", cornerRadiusDp)
            put("transparentBackground", transparentBackground)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): OverlayConfig {
            return OverlayConfig(
                id = json.optString("id", System.currentTimeMillis().toString()),
                name = json.optString("name", "Untitled"),
                url = json.optString("url", ""),
                enabled = json.optBoolean("enabled", true),
                widthDp = json.optInt("widthDp", 240),
                heightDp = json.optInt("heightDp", 160),
                opacityPercent = json.optInt("opacityPercent", 100),
                backgroundColor = json.optInt("backgroundColor", 0xCC000000.toInt()),
                cornerRadiusDp = json.optInt("cornerRadiusDp", 16),
                transparentBackground = json.optBoolean("transparentBackground", false)
            )
        }
    }
}

package com.floatoverlay.app.model

import org.json.JSONObject

data class PresetSlot(
    val posXPercent: Float,
    val posYPercent: Float,
    val widthDp: Int,
    val heightDp: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("posXPercent", posXPercent)
            put("posYPercent", posYPercent)
            put("widthDp", widthDp)
            put("heightDp", heightDp)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): PresetSlot {
            return PresetSlot(
                posXPercent = json.optDouble("posXPercent", 0.0).toFloat(),
                posYPercent = json.optDouble("posYPercent", 0.0).toFloat(),
                widthDp = json.optInt("widthDp", 240),
                heightDp = json.optInt("heightDp", 160)
            )
        }
    }
}

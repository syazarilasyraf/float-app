package com.floatoverlay.app.model

import org.json.JSONObject

data class HudSettings(
    val goalRM: Int,
    val minutesPerRM: Int,
    val capHours: Int
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("goalRM", goalRM)
            put("minutesPerRM", minutesPerRM)
            put("capHours", capHours)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): HudSettings {
            return HudSettings(
                goalRM = json.optInt("goalRM", 50),
                minutesPerRM = json.optInt("minutesPerRM", 5),
                capHours = json.optInt("capHours", 3)
            )
        }

        fun default(): HudSettings = HudSettings(50, 5, 3)
    }
}

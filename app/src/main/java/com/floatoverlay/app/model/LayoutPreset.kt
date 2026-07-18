package com.floatoverlay.app.model

import org.json.JSONObject

data class LayoutPreset(
    val name: String,
    val slots: Map<String, PresetSlot>
) {
    fun toJson(): JSONObject {
        val slotsJson = JSONObject()
        slots.forEach { (key, slot) ->
            slotsJson.put(key, slot.toJson())
        }
        return JSONObject().apply {
            put("name", name)
            put("slots", slotsJson)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): LayoutPreset {
            val name = json.optString("name", "Unnamed")
            val slotsJson = json.optJSONObject("slots") ?: JSONObject()
            val slots = mutableMapOf<String, PresetSlot>()
            slotsJson.keys().forEach { key ->
                slots[key] = PresetSlot.fromJson(slotsJson.getJSONObject(key))
            }
            return LayoutPreset(name, slots)
        }

        fun createDefaultCrPortraitHud(): LayoutPreset {
            return LayoutPreset(
                name = "CR Portrait HUD",
                slots = mapOf(
                    "timer" to PresetSlot(0.21f, 0.115f, 360, 90),
                    "ticker" to PresetSlot(0.02f, 0.035f, 300, 45),
                    "likes" to PresetSlot(0.70f, 0.035f, 170, 45),
                    "board" to PresetSlot(0.64f, 0.52f, 220, 180),
                    "alert" to PresetSlot(0.17f, 0.27f, 400, 240)
                )
            )
        }
    }
}

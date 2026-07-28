package com.floatoverlay.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class OverlayProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val orientation: String = "any",
    val overlays: List<OverlayConfig> = emptyList()
) {
    init {
        require(orientation in ORIENTATIONS) { "Invalid orientation: $orientation" }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("orientation", orientation)
            put("overlays", JSONArray().apply {
                overlays.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        val ORIENTATIONS = setOf("portrait", "landscape", "any")

        fun fromJson(json: JSONObject): OverlayProfile {
            val overlaysArray = json.optJSONArray("overlays")
            val overlays = mutableListOf<OverlayConfig>()
            if (overlaysArray != null) {
                for (i in 0 until overlaysArray.length()) {
                    overlays.add(OverlayConfig.fromJson(overlaysArray.getJSONObject(i)))
                }
            }
            return OverlayProfile(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Untitled"),
                orientation = json.optString("orientation", "any"),
                overlays = overlays
            )
        }
    }
}

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
    val transparentBackground: Boolean = false,
    val showResizeHandle: Boolean = true,
    val locked: Boolean = false,
    val touchThrough: Boolean = true,
    val posXPercent: Float = -1f,
    val posYPercent: Float = -1f,
    val scalePercent: Int = 100,
    val zoomMode: String = "layout",
    val contentOffsetX: Int = 0,
    val contentOffsetY: Int = 0,
    val zIndex: Int = 0,
    val cameraShape: String = "square",
    val cameraFilter: String = "normal",
    val cameraFlip: Boolean = true
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
            put("showResizeHandle", showResizeHandle)
            put("locked", locked)
            put("touchThrough", touchThrough)
            put("posXPercent", posXPercent)
            put("posYPercent", posYPercent)
            put("scalePercent", scalePercent)
            put("zoomMode", zoomMode)
            put("contentOffsetX", contentOffsetX)
            put("contentOffsetY", contentOffsetY)
            put("zIndex", zIndex)
            put("cameraShape", cameraShape)
            put("cameraFilter", cameraFilter)
            put("cameraFlip", cameraFlip)
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
                transparentBackground = json.optBoolean("transparentBackground", false),
                showResizeHandle = json.optBoolean("showResizeHandle", true),
                locked = json.optBoolean("locked", false),
                touchThrough = json.optBoolean("touchThrough", true),
                posXPercent = json.optDouble("posXPercent", -1.0).toFloat(),
                posYPercent = json.optDouble("posYPercent", -1.0).toFloat(),
                scalePercent = json.optInt("scalePercent", 100),
                zoomMode = json.optString("zoomMode", "layout"),
                contentOffsetX = json.optInt("contentOffsetX", 0),
                contentOffsetY = json.optInt("contentOffsetY", 0),
                zIndex = json.optInt("zIndex", 0),
                cameraShape = json.optString("cameraShape", "square"),
                cameraFilter = json.optString("cameraFilter", "normal"),
                cameraFlip = json.optBoolean("cameraFlip", true)
            )
        }
    }
}

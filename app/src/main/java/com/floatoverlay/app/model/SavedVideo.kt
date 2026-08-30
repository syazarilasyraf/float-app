package com.floatoverlay.app.model

import org.json.JSONObject
import java.util.UUID

/**
 * A video shared from another app (TikTok, YouTube) to be played in a floating overlay.
 */
data class SavedVideo(
    val id: String = UUID.randomUUID().toString(),
    val source: Source,
    val videoId: String,
    val originalUrl: String,
    val title: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    enum class Source {
        TIKTOK,
        YOUTUBE
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("source", source.name)
        put("videoId", videoId)
        put("originalUrl", originalUrl)
        put("title", title)
        put("addedAt", addedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): SavedVideo = SavedVideo(
            id = json.optString("id", UUID.randomUUID().toString()),
            source = try {
                Source.valueOf(json.optString("source", "TIKTOK"))
            } catch (e: Exception) {
                Source.TIKTOK
            },
            videoId = json.optString("videoId", ""),
            originalUrl = json.optString("originalUrl", ""),
            title = json.optString("title", ""),
            addedAt = json.optLong("addedAt", System.currentTimeMillis())
        )
    }
}

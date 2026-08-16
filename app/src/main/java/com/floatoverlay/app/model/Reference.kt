package com.floatoverlay.app.model

import org.json.JSONObject
import java.util.UUID

/**
 * A reference attached to a build project.
 *
 * References can come from many sources (web, image search, gallery,
 * screenshots, AI-generated resources, URLs). This model is source-agnostic.
 */
data class Reference(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val imageUri: String = "",
    val sourceUrl: String = "",
    val notes: String = "",
    val source: Source = Source.LOCAL
) {
    enum class Source {
        LOCAL,
        WEB,
        IMAGE_SEARCH,
        GALLERY,
        SCREENSHOT,
        AI_GENERATED,
        URL
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("imageUri", imageUri)
        put("sourceUrl", sourceUrl)
        put("notes", notes)
        put("source", source.name)
    }

    companion object {
        fun fromJson(json: JSONObject): Reference = Reference(
            id = json.optString("id", UUID.randomUUID().toString()),
            title = json.optString("title", ""),
            description = json.optString("description", ""),
            imageUri = json.optString("imageUri", ""),
            sourceUrl = json.optString("sourceUrl", ""),
            notes = json.optString("notes", ""),
            source = try {
                Source.valueOf(json.optString("source", "LOCAL"))
            } catch (e: Exception) {
                Source.LOCAL
            }
        )
    }
}

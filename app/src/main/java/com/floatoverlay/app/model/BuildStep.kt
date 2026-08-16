package com.floatoverlay.app.model

import org.json.JSONObject
import java.util.UUID

data class BuildStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val imageUri: String = "",
    val completed: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("description", description)
        put("imageUri", imageUri)
        put("completed", completed)
    }

    companion object {
        fun fromJson(json: JSONObject): BuildStep = BuildStep(
            id = json.optString("id", UUID.randomUUID().toString()),
            title = json.optString("title", ""),
            description = json.optString("description", ""),
            imageUri = json.optString("imageUri", ""),
            completed = json.optBoolean("completed", false)
        )
    }
}

package com.floatoverlay.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A Minecraft build project.
 *
 * Kept generic enough that AI tools can create and modify it.
 * The model is intentionally simple: it is a personal workspace item,
 * not a full project-management entity.
 */
data class BuildProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val coverImageUri: String = "",
    val references: List<Reference> = emptyList(),
    val materials: List<Material> = emptyList(),
    val steps: List<BuildStep> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val completedSteps: Int
        get() = steps.count { it.completed }

    val totalSteps: Int
        get() = steps.size

    val stepProgressPercent: Int
        get() = if (steps.isEmpty()) 0 else (completedSteps * 100 / steps.size)

    val materialProgressPercent: Int
        get() {
            val total = materials.size
            return if (total == 0) 0 else materials.count { it.collected } * 100 / total
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("coverImageUri", coverImageUri)
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("references", JSONArray().apply { references.forEach { put(it.toJson()) } })
        put("materials", JSONArray().apply { materials.forEach { put(it.toJson()) } })
        put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): BuildProject = BuildProject(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "Untitled"),
            description = json.optString("description", ""),
            coverImageUri = json.optString("coverImageUri", ""),
            notes = json.optString("notes", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            references = json.optJSONArray("references")?.let { array ->
                List(array.length()) { i -> Reference.fromJson(array.getJSONObject(i)) }
            } ?: emptyList(),
            materials = json.optJSONArray("materials")?.let { array ->
                List(array.length()) { i -> Material.fromJson(array.getJSONObject(i)) }
            } ?: emptyList(),
            steps = json.optJSONArray("steps")?.let { array ->
                List(array.length()) { i -> BuildStep.fromJson(array.getJSONObject(i)) }
            } ?: emptyList()
        )
    }
}

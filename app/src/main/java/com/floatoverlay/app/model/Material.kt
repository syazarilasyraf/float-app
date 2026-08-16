package com.floatoverlay.app.model

import org.json.JSONObject
import java.util.UUID

data class Material(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int = 1,
    val collected: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("quantity", quantity)
        put("collected", collected)
    }

    companion object {
        fun fromJson(json: JSONObject): Material = Material(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", ""),
            quantity = json.optInt("quantity", 1),
            collected = json.optBoolean("collected", false)
        )
    }
}

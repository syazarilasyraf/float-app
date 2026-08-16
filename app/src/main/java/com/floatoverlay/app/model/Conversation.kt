package com.floatoverlay.app.model

import org.json.JSONObject
import java.util.UUID

/**
 * A persisted AI conversation.
 *
 * One conversation is active at a time for the built-in assistant.
 * The architecture supports multiple conversations if needed later.
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Float Assistant",
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        val array = org.json.JSONArray()
        messages.forEach { array.put(it.toJson()) }
        put("messages", array)
    }

    companion object {
        fun fromJson(json: JSONObject): Conversation = Conversation(
            id = json.optString("id", UUID.randomUUID().toString()),
            title = json.optString("title", "Float Assistant"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            messages = json.optJSONArray("messages")?.let { array ->
                List(array.length()) { i -> Message.fromJson(array.getJSONObject(i)) }
            } ?: emptyList()
        )
    }
}

package com.floatoverlay.app.model

import org.json.JSONObject
import java.util.UUID

/**
 * A single message in an AI conversation.
 *
 * Messages are persisted as plain JSON through [ConversationRepository].
 * Tool calls are stored as simple maps so the architecture can evolve without
 * requiring a database migration.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val toolCall: ToolCall? = null,
    val toolResult: ToolResult? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class Role {
        USER,
        ASSISTANT,
        TOOL,
        SYSTEM
    }

    data class ToolCall(
        val toolName: String,
        val arguments: Map<String, String>,
        val toolCallId: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("toolName", toolName)
            put("toolCallId", toolCallId)
            val args = JSONObject()
            arguments.forEach { (key, value) -> args.put(key, value) }
            put("arguments", args)
        }

        companion object {
            fun fromJson(json: JSONObject): ToolCall = ToolCall(
                toolName = json.optString("toolName", ""),
                arguments = json.optJSONObject("arguments")?.let { args ->
                    val map = mutableMapOf<String, String>()
                    for (key in args.keys()) {
                        map[key] = args.optString(key, "")
                    }
                    map
                } ?: emptyMap(),
                toolCallId = json.optString("toolCallId", "")
            )
        }
    }

    data class ToolResult(
        val toolName: String,
        val success: Boolean,
        val message: String,
        val toolCallId: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("toolName", toolName)
            put("success", success)
            put("message", message)
            put("toolCallId", toolCallId)
        }

        companion object {
            fun fromJson(json: JSONObject): ToolResult = ToolResult(
                toolName = json.optString("toolName", ""),
                success = json.optBoolean("success", false),
                message = json.optString("message", ""),
                toolCallId = json.optString("toolCallId", "")
            )
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role.name)
        put("content", content)
        put("createdAt", createdAt)
        toolCall?.let { put("toolCall", it.toJson()) }
        toolResult?.let { put("toolResult", it.toJson()) }
    }

    companion object {
        fun fromJson(json: JSONObject): Message = Message(
            id = json.optString("id", UUID.randomUUID().toString()),
            role = try {
                Role.valueOf(json.optString("role", "ASSISTANT"))
            } catch (e: Exception) {
                Role.ASSISTANT
            },
            content = json.optString("content", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            toolCall = json.optJSONObject("toolCall")?.let { ToolCall.fromJson(it) },
            toolResult = json.optJSONObject("toolResult")?.let { ToolResult.fromJson(it) }
        )
    }
}

package com.floatoverlay.app.data

import android.content.Context
import android.content.SharedPreferences
import com.floatoverlay.app.model.Conversation
import com.floatoverlay.app.model.Message
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence for AI conversations.
 *
 * Uses the same SharedPreferences + org.json pattern as the existing
 * overlay and preset repositories.
 */
class ConversationRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConversation(): Conversation {
        val json = prefs.getString(KEY_CONVERSATION, null) ?: return defaultConversation()
        return try {
            Conversation.fromJson(JSONObject(json))
        } catch (e: Exception) {
            defaultConversation()
        }
    }

    fun saveConversation(conversation: Conversation) {
        prefs.edit().putString(KEY_CONVERSATION, conversation.toJson().toString()).apply()
    }

    fun addMessage(message: Message): Conversation {
        val conversation = getConversation()
        val updated = conversation.copy(
            messages = conversation.messages + message,
            updatedAt = System.currentTimeMillis()
        )
        saveConversation(updated)
        return updated
    }

    fun clearConversation(): Conversation {
        val fresh = defaultConversation()
        saveConversation(fresh)
        return fresh
    }

    private fun defaultConversation(): Conversation = Conversation(
        title = "Float Assistant",
        messages = listOf(
            Message(
                role = Message.Role.SYSTEM,
                content = "You are Float, a personal AI assistant inside an Android floating overlay app. " +
                        "You help the user with Minecraft builds, coding, research, and other tasks. " +
                        "When the user asks to create or modify a Minecraft project, use the available tools."
            )
        )
    )

    companion object {
        private const val PREFS_NAME = "FloatAssistantPrefs"
        private const val KEY_CONVERSATION = "conversation"
    }
}

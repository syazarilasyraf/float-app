package com.floatoverlay.app.ai.provider

import android.os.Handler
import android.os.Looper
import com.floatoverlay.app.ai.AIProvider
import com.floatoverlay.app.ai.AITool
import com.floatoverlay.app.ai.asText
import com.floatoverlay.app.model.Message
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Generic OpenAI-compatible chat provider.
 *
 * Works with any API that exposes `/chat/completions` using the OpenAI schema,
 * including Kimi (Moonshot AI).
 *
 * The API key is provided at construction time; it must be loaded from secure
 * storage, never hardcoded.
 */
class OpenAICompatibleProvider(
    override val name: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val toolsEnabled: Boolean = true
) : AIProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var activeCall: Call? = null

    override fun sendMessage(
        conversation: List<Message>,
        tools: List<AITool>,
        callback: AIProvider.AIResponseCallback
    ) {
        callback.onLoading()

        val messagesArray = JSONArray()
        conversation.forEach { msg ->
            val obj = JSONObject()
            obj.put("role", mapRole(msg.role))
            when {
                msg.toolCall != null -> {
                    obj.put("content", msg.content)
                    val calls = JSONArray()
                    val callObj = JSONObject()
                    callObj.put("id", msg.toolCall.toolCallId.takeIf { it.isNotBlank() } ?: "call_${System.currentTimeMillis()}")
                    callObj.put("type", "function")
                    callObj.put("function", JSONObject().apply {
                        put("name", msg.toolCall.toolName)
                        put("arguments", JSONObject(msg.toolCall.arguments).toString())
                    })
                    calls.put(callObj)
                    obj.put("tool_calls", calls)
                }
                msg.toolResult != null -> {
                    obj.put("content", msg.content)
                    obj.put("tool_call_id", msg.toolResult.toolCallId.takeIf { it.isNotBlank() } ?: "call_${msg.toolResult.toolName}")
                }
                else -> obj.put("content", msg.content)
            }
            messagesArray.put(obj)
        }

        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", messagesArray)
            if (toolsEnabled && tools.isNotEmpty()) {
                put("tools", tools.toToolsSchema())
            }
        }

        val request = Request.Builder()
            .url("${baseUrl.removeSuffix("/")}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        activeCall = client.newCall(request)
        activeCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                post { callback.onError(e) }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrBlank()) {
                    post { callback.onError(IOException("HTTP ${response.code}: ${body ?: "empty"}")) }
                    return
                }
                try {
                    val message = parseResponse(body, tools)
                    post { callback.onResult(message) }
                } catch (e: Exception) {
                    post { callback.onError(e) }
                }
            }
        })
    }

    override fun cancel() {
        activeCall?.cancel()
        activeCall = null
    }

    private fun parseResponse(body: String, tools: List<AITool>): Message {
        val json = JSONObject(body)
        val choice = json.optJSONArray("choices")?.optJSONObject(0)
            ?: return Message(role = Message.Role.ASSISTANT, content = "No response from AI.")
        val messageObj = choice.optJSONObject("message") ?: return Message(
            role = Message.Role.ASSISTANT,
            content = "No message in AI response."
        )
        val content = messageObj.optString("content", "")
        val toolCalls = messageObj.optJSONArray("tool_calls")

        if (toolCalls != null && toolCalls.length() > 0) {
            val call = toolCalls.getJSONObject(0)
            val function = call.optJSONObject("function") ?: return Message(
                role = Message.Role.ASSISTANT,
                content = content
            )
            val toolName = function.optString("name", "")
            val toolCallId = call.optString("id", "call_${System.currentTimeMillis()}")
            val argumentsString = function.optString("arguments", "{}")
            val argumentsJson = JSONObject(argumentsString)
            val arguments = mutableMapOf<String, String>()
            for (key in argumentsJson.keys()) {
                arguments[key] = argumentsJson.optString(key, "")
            }
            return Message(
                role = Message.Role.ASSISTANT,
                content = content.takeIf { it.isNotBlank() } ?: "I'll use the $toolName tool.",
                toolCall = Message.ToolCall(
                    toolName = toolName,
                    arguments = arguments,
                    toolCallId = toolCallId
                )
            )
        }

        return Message(role = Message.Role.ASSISTANT, content = content)
    }

    private fun List<AITool>.toToolsSchema(): JSONArray {
        val array = JSONArray()
        forEach { tool ->
            array.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        val props = JSONObject()
                        val required = JSONArray()
                        tool.parameters.forEach { param ->
                            val (name, rest) = param.split(":", limit = 2).let {
                                it[0].trim() to (it.getOrNull(1) ?: "")
                            }
                            val isRequired = rest.contains("required")
                            if (isRequired) required.put(name)
                            props.put(name, JSONObject().apply {
                                put("type", "string")
                                put("description", rest.trim())
                            })
                        }
                        put("properties", props)
                        put("required", required)
                    })
                })
            })
        }
        return array
    }

    private fun mapRole(role: Message.Role): String = when (role) {
        Message.Role.USER -> "user"
        Message.Role.ASSISTANT -> "assistant"
        Message.Role.SYSTEM -> "system"
        Message.Role.TOOL -> "tool"
    }

    private fun post(action: () -> Unit) {
        handler.post(action)
    }
}

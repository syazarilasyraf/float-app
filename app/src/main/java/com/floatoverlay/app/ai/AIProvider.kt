package com.floatoverlay.app.ai

import com.floatoverlay.app.model.Message

/**
 * Abstraction over any AI backend.
 *
 * Float must not be tied to a single provider. Implementations can call
 * OpenAI, Gemini, local models, or mock responses for testing.
 *
 * Real providers should fetch credentials from secure storage, not from source code.
 */
interface AIProvider {

    val name: String

    /**
     * Send a conversation to the provider and receive a response.
     *
     * The [conversation] includes system/user/assistant/tool messages.
     * The [tools] list describes available tools; the provider may choose to
     * request a tool call in its response.
     *
     * The callback is always invoked on the main thread.
     */
    fun sendMessage(
        conversation: List<Message>,
        tools: List<AITool> = emptyList(),
        callback: AIResponseCallback
    )

    fun cancel()

    interface AIResponseCallback {
        fun onLoading()
        fun onResult(message: Message)
        fun onError(error: Throwable)
    }
}

package com.floatoverlay.app.ai.provider

import android.os.Handler
import android.os.Looper
import com.floatoverlay.app.ai.AIProvider
import com.floatoverlay.app.ai.AITool
import com.floatoverlay.app.model.Message

/**
 * Mock AI provider for offline development and testing.
 *
 * It demonstrates the tool architecture by parsing natural-language input and
 * emitting tool-call messages. No API key is required.
 */
class MockAIProvider : AIProvider {

    override val name: String = "Mock AI"

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null

    override fun sendMessage(
        conversation: List<Message>,
        tools: List<AITool>,
        callback: AIProvider.AIResponseCallback
    ) {
        callback.onLoading()

        val lastUserMessage = conversation.findLast { it.role == Message.Role.USER }?.content ?: ""

        pendingRunnable = Runnable {
            val response = generateResponse(lastUserMessage, tools)
            callback.onResult(response)
        }

        // Simulate a short network delay.
        handler.postDelayed(pendingRunnable!!, 600)
    }

    override fun cancel() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
    }

    private fun generateResponse(input: String, tools: List<AITool>): Message {
        val lower = input.lowercase()

        // Demonstrate the tool architecture: if the user asks to create a project,
        // return a tool-call message so the app can execute it.
        val createProjectTool = tools.find { it.name == "create_build_project" }
        if (createProjectTool != null && (
                    lower.contains("create") || lower.contains("build") || lower.contains("project")
                    ) && lower.contains("minecraft")
        ) {
            val projectName = extractProjectName(input)
            return Message(
                role = Message.Role.ASSISTANT,
                content = "I'll create a Minecraft build project for you.",
                toolCall = Message.ToolCall(
                    toolName = createProjectTool.name,
                    arguments = mapOf("name" to projectName)
                )
            )
        }

        // Demonstrate adding a material.
        val addMaterialTool = tools.find { it.name == "add_material" }
        if (addMaterialTool != null && lower.contains("material") && lower.contains("add")) {
            val (projectHint, materialHint) = extractMaterialHints(input)
            return Message(
                role = Message.Role.ASSISTANT,
                content = "I'll add that material to the project.",
                toolCall = Message.ToolCall(
                    toolName = addMaterialTool.name,
                    arguments = mapOf(
                        "projectName" to projectHint,
                        "name" to materialHint,
                        "quantity" to "1"
                    )
                )
            )
        }

        // Demonstrate adding a step.
        val addStepTool = tools.find { it.name == "add_build_step" }
        if (addStepTool != null && lower.contains("step") && lower.contains("add")) {
            val (projectHint, stepHint) = extractStepHints(input)
            return Message(
                role = Message.Role.ASSISTANT,
                content = "I'll add that build step.",
                toolCall = Message.ToolCall(
                    toolName = addStepTool.name,
                    arguments = mapOf(
                        "projectName" to projectHint,
                        "title" to stepHint
                    )
                )
            )
        }

        // Generic demo response.
        return when {
            lower.contains("japanese house") -> Message(
                role = Message.Role.ASSISTANT,
                content = "A Japanese house is a great Minecraft project. " +
                        "Try using dark oak, spruce, and cobblestone with a curved roof. " +
                        "Say 'create a Minecraft project called Japanese House' and I'll set it up for you."
            )
            lower.contains("hello") || lower.contains("hi") -> Message(
                role = Message.Role.ASSISTANT,
                content = "Hello! I'm Float, your personal AI assistant. What would you like to build today?"
            )
            else -> Message(
                role = Message.Role.ASSISTANT,
                content = "I'm a mock AI, so I can't search the web yet. " +
                        "You can ask me to create a Minecraft build project, add materials, or add build steps to see the tool system in action."
            )
        }
    }

    private fun extractProjectName(input: String): String {
        val regex = Regex("(?:called|named)?\\s*[\"']?([^\"']+?)[\"']?(?:\\s+project)?\\s*$", RegexOption.IGNORE_CASE)
        val match = regex.find(input)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: input.replace(Regex("(?i)create|build|project|minecraft|a\\s+|called|named"), "")
                .trim()
                .takeIf { it.isNotBlank() }
            ?: "New Build"
    }

    private fun extractMaterialHints(input: String): Pair<String, String> {
        val projectRegex = Regex("(?:project|for)\\s+[\"']?([^\"']+?)[\"']?(?:\\s+add|\\s+material|\\s*$)", RegexOption.IGNORE_CASE)
        val project = projectRegex.find(input)?.groupValues?.get(1)?.trim() ?: ""
        val material = input.replace(Regex("(?i)add|material|to|project|for|called|named"), "")
            .replace(projectRegex, "")
            .trim()
            .takeIf { it.isNotBlank() } ?: "Material"
        return project to material
    }

    private fun extractStepHints(input: String): Pair<String, String> {
        val projectRegex = Regex("(?:project|for)\\s+[\"']?([^\"']+?)[\"']?(?:\\s+add|\\s+step|\\s*$)", RegexOption.IGNORE_CASE)
        val project = projectRegex.find(input)?.groupValues?.get(1)?.trim() ?: ""
        val step = input.replace(Regex("(?i)add|step|to|project|for|called|named"), "")
            .replace(projectRegex, "")
            .trim()
            .takeIf { it.isNotBlank() } ?: "Build step"
        return project to step
    }
}

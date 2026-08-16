package com.floatoverlay.app.ai

/**
 * Registry of all tools available to Float's AI assistant.
 *
 * Modules register their tools here. For v1 the registry is populated at
 * application start with mock/Minecraft tools; future modules can add their
 * own tools (e.g. coding, streaming, notes) without changing the AI core.
 */
object ToolRegistry {

    private val tools = mutableMapOf<String, AITool>()

    fun register(tool: AITool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun get(name: String): AITool? = tools[name]

    fun all(): List<AITool> = tools.values.toList()

    fun clear() = tools.clear()
}

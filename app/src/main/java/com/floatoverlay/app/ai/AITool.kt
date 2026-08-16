package com.floatoverlay.app.ai

/**
 * Describes a tool that an AI provider can request Float to execute.
 *
 * The architecture is intentionally simple: tools receive a map of string
 * arguments and return a string result. This keeps v1 small while making it
 * easy to add richer schemas later.
 */
interface AITool {

    val name: String

    val description: String

    /**
     * Human-readable parameter hints for the provider.
     * Example: "name: string (required)"
     */
    val parameters: List<String>

    /**
     * Execute the tool with the provided arguments.
     *
     * @return a result string that will be shown back to the AI as a tool result.
     */
    fun execute(arguments: Map<String, String>): ToolExecutionResult
}

sealed class ToolExecutionResult {
    data class Success(val message: String) : ToolExecutionResult()
    data class Error(val message: String) : ToolExecutionResult()
}

fun ToolExecutionResult.asText(): String = when (this) {
    is ToolExecutionResult.Success -> message
    is ToolExecutionResult.Error -> "Error: $message"
}

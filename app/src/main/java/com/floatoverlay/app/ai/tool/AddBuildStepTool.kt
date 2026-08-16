package com.floatoverlay.app.ai.tool

import com.floatoverlay.app.ai.AITool
import com.floatoverlay.app.ai.ToolExecutionResult
import com.floatoverlay.app.data.ProjectRepository
import com.floatoverlay.app.model.BuildStep

/**
 * Tool: add a build step to a Minecraft build project.
 */
class AddBuildStepTool(private val repository: ProjectRepository) : AITool {

    override val name: String = "add_build_step"

    override val description: String =
        "Add a build step to a Minecraft build project."

    override val parameters: List<String> = listOf(
        "projectName: string — name or id of the project",
        "title: string (required) — short step title",
        "description: string — longer step description"
    )

    override fun execute(arguments: Map<String, String>): ToolExecutionResult {
        val projectName = arguments["projectName"] ?: ""
        val title = arguments["title"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return ToolExecutionResult.Error("Step title is required")
        val description = arguments["description"]?.trim() ?: ""

        val project = findProject(repository, projectName)
            ?: return ToolExecutionResult.Error("Project not found: '$projectName'")

        val step = BuildStep(title = title, description = description)
        val updated = project.copy(
            steps = project.steps + step,
            updatedAt = System.currentTimeMillis()
        )
        repository.saveProject(updated)
        return ToolExecutionResult.Success(
            "Added step '${step.title}' to '${updated.name}'."
        )
    }
}

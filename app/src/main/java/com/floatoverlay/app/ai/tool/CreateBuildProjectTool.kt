package com.floatoverlay.app.ai.tool

import com.floatoverlay.app.ai.AITool
import com.floatoverlay.app.ai.ToolExecutionResult
import com.floatoverlay.app.data.ProjectRepository
import com.floatoverlay.app.model.BuildProject

/**
 * Tool: create a new Minecraft build project.
 */
class CreateBuildProjectTool(private val repository: ProjectRepository) : AITool {

    override val name: String = "create_build_project"

    override val description: String =
        "Create a new Minecraft build project with a given name."

    override val parameters: List<String> = listOf(
        "name: string (required) — the project name"
    )

    override fun execute(arguments: Map<String, String>): ToolExecutionResult {
        val name = arguments["name"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return ToolExecutionResult.Error("Project name is required")

        val project = BuildProject(name = name)
        repository.saveProject(project)
        return ToolExecutionResult.Success(
            "Created project '${project.name}' (${project.id})."
        )
    }
}

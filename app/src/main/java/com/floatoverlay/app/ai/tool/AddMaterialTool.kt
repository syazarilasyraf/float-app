package com.floatoverlay.app.ai.tool

import com.floatoverlay.app.ai.AITool
import com.floatoverlay.app.ai.ToolExecutionResult
import com.floatoverlay.app.data.ProjectRepository
import com.floatoverlay.app.model.Material

/**
 * Tool: add a material to a Minecraft build project.
 */
class AddMaterialTool(private val repository: ProjectRepository) : AITool {

    override val name: String = "add_material"

    override val description: String =
        "Add a material with a quantity to a Minecraft build project."

    override val parameters: List<String> = listOf(
        "projectName: string — name or id of the project",
        "name: string (required) — material name",
        "quantity: number — default 1"
    )

    override fun execute(arguments: Map<String, String>): ToolExecutionResult {
        val projectName = arguments["projectName"] ?: ""
        val materialName = arguments["name"]?.trim()?.takeIf { it.isNotBlank() }
            ?: return ToolExecutionResult.Error("Material name is required")
        val quantity = arguments["quantity"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

        val project = findProject(repository, projectName)
            ?: return ToolExecutionResult.Error("Project not found: '$projectName'")

        val material = Material(name = materialName, quantity = quantity)
        val updated = project.copy(
            materials = project.materials + material,
            updatedAt = System.currentTimeMillis()
        )
        repository.saveProject(updated)
        return ToolExecutionResult.Success(
            "Added ${material.quantity}x ${material.name} to '${updated.name}'."
        )
    }
}

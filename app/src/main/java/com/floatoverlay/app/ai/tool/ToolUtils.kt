package com.floatoverlay.app.ai.tool

import com.floatoverlay.app.data.ProjectRepository
import com.floatoverlay.app.model.BuildProject

internal fun findProject(repository: ProjectRepository, query: String): BuildProject? {
    val trimmed = query.trim()
    if (trimmed.isBlank()) {
        // If no project is specified, use the most recently updated one.
        return repository.getProjects().maxByOrNull { it.updatedAt }
    }

    // First try exact id match.
    repository.getProject(trimmed)?.let { return it }

    // Then try exact name match (case-insensitive).
    repository.getProjects().find { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

    // Finally try substring match.
    return repository.getProjects().find { it.name.contains(trimmed, ignoreCase = true) }
}

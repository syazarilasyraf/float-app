package com.floatoverlay.app.data

import android.content.Context
import android.content.SharedPreferences
import com.floatoverlay.app.model.BuildProject
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence for Minecraft build projects.
 *
 * Projects, materials, steps, references, and notes are all stored as JSON
 * in SharedPreferences. This keeps v1 simple while remaining easy to migrate
 * to a database later.
 */
class ProjectRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProjects(): List<BuildProject> {
        val json = prefs.getString(KEY_PROJECTS, "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            List(array.length()) { i -> BuildProject.fromJson(array.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveProjects(projects: List<BuildProject>) {
        val array = JSONArray()
        projects.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROJECTS, array.toString()).apply()
    }

    fun saveProject(project: BuildProject) {
        val projects = getProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == project.id }
        if (index >= 0) {
            projects[index] = project
        } else {
            projects.add(project)
        }
        saveProjects(projects)
    }

    fun getProject(id: String): BuildProject? {
        return getProjects().find { it.id == id }
    }

    fun deleteProject(id: String) {
        val projects = getProjects().filterNot { it.id == id }
        saveProjects(projects)
    }

    companion object {
        private const val PREFS_NAME = "FloatProjectPrefs"
        private const val KEY_PROJECTS = "build_projects"
    }
}

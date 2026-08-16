package com.floatoverlay.app.ui.minecraft

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.data.ProjectRepository
import com.floatoverlay.app.model.BuildProject
import com.google.android.material.textfield.TextInputEditText

class MinecraftProjectsFragment : Fragment(), ProjectAdapter.ProjectListener {

    private lateinit var repository: ProjectRepository
    private lateinit var adapter: ProjectAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ProjectRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_minecraft_projects, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.projectRecyclerView)
        addButton = view.findViewById(R.id.addProjectButton)

        adapter = ProjectAdapter(getProjects(), this)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        addButton.setOnClickListener { showCreateDialog() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onProjectClick(project: BuildProject) {
        val intent = Intent(requireContext(), ProjectDetailActivity::class.java).apply {
            putExtra(ProjectDetailActivity.EXTRA_PROJECT_ID, project.id)
        }
        startActivity(intent)
    }

    override fun onDelete(project: BuildProject) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete project")
            .setMessage("Delete \"${project.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                repository.deleteProject(project.id)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreateDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_project_create, null)
        val input = view.findViewById<TextInputEditText>(R.id.projectNameInput)

        AlertDialog.Builder(requireContext())
            .setTitle("New Build Project")
            .setView(view)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    repository.saveProject(BuildProject(name = name))
                    refresh()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refresh() {
        adapter.updateData(getProjects())
    }

    private fun getProjects(): List<BuildProject> {
        return repository.getProjects().sortedByDescending { it.updatedAt }
    }
}

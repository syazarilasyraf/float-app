package com.floatoverlay.app.ui.minecraft

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.BuildProject

class ProjectAdapter(
    private var projects: List<BuildProject>,
    private val listener: ProjectListener
) : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {

    interface ProjectListener {
        fun onProjectClick(project: BuildProject)
        fun onDelete(project: BuildProject)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.projectName)
        val progress: TextView = itemView.findViewById(R.id.projectProgress)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteProjectButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val project = projects[position]
        holder.name.text = project.name
        holder.progress.text = "${project.completedSteps}/${project.totalSteps} steps · ${project.stepProgressPercent}%"
        holder.itemView.setOnClickListener { listener.onProjectClick(project) }
        holder.deleteButton.setOnClickListener { listener.onDelete(project) }
    }

    override fun getItemCount(): Int = projects.size

    fun updateData(newProjects: List<BuildProject>) {
        projects = newProjects
        notifyDataSetChanged()
    }
}

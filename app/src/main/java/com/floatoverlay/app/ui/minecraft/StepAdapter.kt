package com.floatoverlay.app.ui.minecraft

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.BuildStep

class StepAdapter(
    private var steps: List<BuildStep>,
    private val listener: StepListener
) : RecyclerView.Adapter<StepAdapter.ViewHolder>() {

    interface StepListener {
        fun onToggle(step: BuildStep, completed: Boolean)
        fun onDelete(step: BuildStep)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.stepCheckBox)
        val title: TextView = itemView.findViewById(R.id.stepTitle)
        val description: TextView = itemView.findViewById(R.id.stepDescription)
        val delete: ImageButton = itemView.findViewById(R.id.deleteStepButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_step, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val step = steps[position]
        holder.title.text = step.title
        holder.description.text = step.description.takeIf { it.isNotBlank() }
        holder.description.visibility = if (holder.description.text.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = step.completed
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            listener.onToggle(step, isChecked)
        }
        holder.delete.setOnClickListener { listener.onDelete(step) }
    }

    override fun getItemCount(): Int = steps.size

    fun updateData(newSteps: List<BuildStep>) {
        steps = newSteps
        notifyDataSetChanged()
    }
}

package com.floatoverlay.app.ui.game

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.WindowPreset

class PresetAdapter(
    private var presets: List<WindowPreset>,
    private val listener: PresetListener
) : RecyclerView.Adapter<PresetAdapter.ViewHolder>() {

    interface PresetListener {
        fun onLaunch(preset: WindowPreset)
        fun onEdit(preset: WindowPreset)
        fun onDelete(preset: WindowPreset)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.presetName)
        val dimensions: TextView = itemView.findViewById(R.id.presetDimensions)
        val launchButton: Button = itemView.findViewById(R.id.launchPresetButton)
        val editButton: ImageButton = itemView.findViewById(R.id.editPresetButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deletePresetButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preset, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val preset = presets[position]
        holder.name.text = preset.name
        holder.dimensions.text = if (preset.isFullscreen) {
            "Fullscreen"
        } else {
            "${preset.widthPercent}% × ${preset.heightPercent}% @ (${preset.xPercent}%, ${preset.yPercent}%)"
        }

        val isBuiltin = preset.id == BUILTIN_FULLSCREEN_ID
        holder.editButton.visibility = if (isBuiltin) View.GONE else View.VISIBLE
        holder.deleteButton.visibility = if (isBuiltin) View.GONE else View.VISIBLE

        holder.launchButton.setOnClickListener { listener.onLaunch(preset) }
        holder.editButton.setOnClickListener { listener.onEdit(preset) }
        holder.deleteButton.setOnClickListener { listener.onDelete(preset) }
    }

    override fun getItemCount(): Int = presets.size

    fun updateData(newPresets: List<WindowPreset>) {
        presets = newPresets
        notifyDataSetChanged()
    }

    companion object {
        const val BUILTIN_FULLSCREEN_ID = "fullscreen_builtin"
    }
}

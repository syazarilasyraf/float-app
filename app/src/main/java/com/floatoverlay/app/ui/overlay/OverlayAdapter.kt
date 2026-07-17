package com.floatoverlay.app.ui.overlay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.OverlayConfig

class OverlayAdapter(
    private var overlays: List<OverlayConfig>,
    private val listener: OverlayListener
) : RecyclerView.Adapter<OverlayAdapter.ViewHolder>() {

    interface OverlayListener {
        fun onToggle(overlay: OverlayConfig, enabled: Boolean)
        fun onEdit(overlay: OverlayConfig)
        fun onDelete(overlay: OverlayConfig)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.overlayName)
        val url: TextView = itemView.findViewById(R.id.overlayUrl)
        val enabled: Switch = itemView.findViewById(R.id.overlayEnabled)
        val editButton: ImageButton = itemView.findViewById(R.id.editOverlayButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteOverlayButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_overlay, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val overlay = overlays[position]
        holder.name.text = overlay.name
        holder.url.text = overlay.url
        holder.enabled.isChecked = overlay.enabled

        holder.enabled.setOnCheckedChangeListener { _, isChecked ->
            listener.onToggle(overlay, isChecked)
        }

        holder.editButton.setOnClickListener {
            listener.onEdit(overlay)
        }

        holder.deleteButton.setOnClickListener {
            listener.onDelete(overlay)
        }
    }

    override fun getItemCount(): Int = overlays.size

    fun updateData(newOverlays: List<OverlayConfig>) {
        overlays = newOverlays
        notifyDataSetChanged()
    }
}

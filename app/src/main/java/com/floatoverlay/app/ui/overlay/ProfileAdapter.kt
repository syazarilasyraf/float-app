package com.floatoverlay.app.ui.overlay

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.OverlayProfile

class ProfileAdapter(
    private var profiles: List<OverlayProfile>,
    private val listener: ProfileListener
) : RecyclerView.Adapter<ProfileAdapter.ViewHolder>() {

    interface ProfileListener {
        fun onApply(profile: OverlayProfile)
        fun onRename(profile: OverlayProfile)
        fun onDelete(profile: OverlayProfile)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.profileName)
        val orientation: TextView = itemView.findViewById(R.id.profileOrientation)
        val applyButton: Button = itemView.findViewById(R.id.applyProfileButton)
        val renameButton: ImageButton = itemView.findViewById(R.id.renameProfileButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteProfileButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = profiles[position]
        holder.name.text = profile.name
        holder.orientation.text = buildOrientationLabel(profile)
        holder.applyButton.setOnClickListener { listener.onApply(profile) }
        holder.renameButton.setOnClickListener { listener.onRename(profile) }
        holder.deleteButton.setOnClickListener { listener.onDelete(profile) }
    }

    override fun getItemCount(): Int = profiles.size

    fun updateData(newProfiles: List<OverlayProfile>) {
        profiles = newProfiles
        notifyDataSetChanged()
    }

    private fun buildOrientationLabel(profile: OverlayProfile): String {
        val orientation = profile.orientation.replaceFirstChar { it.uppercase() }
        val count = profile.overlays.size
        val overlayLabel = if (count == 1) "1 overlay" else "$count overlays"
        return "$orientation • $overlayLabel"
    }
}

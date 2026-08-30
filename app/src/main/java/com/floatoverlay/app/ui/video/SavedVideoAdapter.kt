package com.floatoverlay.app.ui.video

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.R
import com.floatoverlay.app.model.SavedVideo

class SavedVideoAdapter(
    private var videos: List<SavedVideo>,
    private val listener: SavedVideoListener
) : RecyclerView.Adapter<SavedVideoAdapter.ViewHolder>() {

    interface SavedVideoListener {
        fun onPlay(video: SavedVideo)
        fun onDelete(video: SavedVideo)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sourceLabel: TextView = view.findViewById(R.id.videoSourceLabel)
        val titleText: TextView = view.findViewById(R.id.videoTitleText)
        val playButton: Button = view.findViewById(R.id.videoPlayButton)
        val deleteButton: Button = view.findViewById(R.id.videoDeleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_video, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.sourceLabel.text = video.source.name
        holder.titleText.text = video.title.ifBlank { video.originalUrl }
        holder.playButton.setOnClickListener { listener.onPlay(video) }
        holder.deleteButton.setOnClickListener { listener.onDelete(video) }
    }

    override fun getItemCount(): Int = videos.size

    fun updateData(newVideos: List<SavedVideo>) {
        videos = newVideos
        notifyDataSetChanged()
    }
}

package com.floatoverlay.app.ui.saved

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floatoverlay.app.FloatOverlayService
import com.floatoverlay.app.R
import com.floatoverlay.app.data.SavedVideoRepository
import com.floatoverlay.app.model.SavedVideo
import com.floatoverlay.app.ui.video.SavedVideoAdapter

class SavedFragment : Fragment() {

    private lateinit var savedVideoRepository: SavedVideoRepository
    private lateinit var savedVideoAdapter: SavedVideoAdapter
    private lateinit var savedVideosRecyclerView: RecyclerView
    private lateinit var savedVideosEmptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedVideoRepository = SavedVideoRepository(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_saved, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedVideosRecyclerView = view.findViewById(R.id.savedVideosRecyclerView)
        savedVideosEmptyText = view.findViewById(R.id.savedVideosEmptyText)

        savedVideoAdapter = SavedVideoAdapter(getSavedVideos(), object : SavedVideoAdapter.SavedVideoListener {
            override fun onPlay(video: SavedVideo) {
                FloatOverlayService.openFloatingVideo(requireContext(), video)
            }

            override fun onDelete(video: SavedVideo) {
                savedVideoRepository.delete(video.id)
                FloatOverlayService.closeFloatingVideo(requireContext(), video.videoId)
                refreshSavedVideos()
            }
        })
        savedVideosRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        savedVideosRecyclerView.adapter = savedVideoAdapter
    }

    override fun onResume() {
        super.onResume()
        refreshSavedVideos()
    }

    private fun refreshSavedVideos() {
        val videos = getSavedVideos()
        savedVideoAdapter.updateData(videos)
        if (videos.isEmpty()) {
            savedVideosRecyclerView.visibility = View.GONE
            savedVideosEmptyText.visibility = View.VISIBLE
        } else {
            savedVideosRecyclerView.visibility = View.VISIBLE
            savedVideosEmptyText.visibility = View.GONE
        }
    }

    private fun getSavedVideos(): List<SavedVideo> {
        return savedVideoRepository.getVideos().sortedByDescending { it.addedAt }
    }
}

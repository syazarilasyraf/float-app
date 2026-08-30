package com.floatoverlay.app.data

import android.content.Context
import android.content.SharedPreferences
import com.floatoverlay.app.model.SavedVideo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Local persistence for videos shared to the app.
 *
 * Stored as a JSON list in SharedPreferences using Gson.
 */
class SavedVideoRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getVideos(): List<SavedVideo> {
        val json = prefs.getString(KEY_VIDEOS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<SavedVideo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveVideos(videos: List<SavedVideo>) {
        prefs.edit().putString(KEY_VIDEOS, gson.toJson(videos)).apply()
    }

    fun saveVideo(video: SavedVideo) {
        val videos = getVideos().toMutableList()
        val index = videos.indexOfFirst { it.id == video.id }
        if (index >= 0) {
            videos[index] = video
        } else {
            videos.add(video)
        }
        saveVideos(videos)
    }

    fun delete(id: String) {
        val videos = getVideos().filterNot { it.id == id }
        saveVideos(videos)
    }

    fun deleteByVideoId(videoId: String) {
        val videos = getVideos().filterNot { it.videoId == videoId }
        saveVideos(videos)
    }

    companion object {
        private const val PREFS_NAME = "FloatSavedVideos"
        private const val KEY_VIDEOS = "saved_videos"
    }
}

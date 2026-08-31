package com.floatoverlay.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight persistence for the streaming feature.
 *
 * Stores server URL, current stream credentials, and a simple "is streaming"
 * flag so the UI can reflect the service state without binding to it.
 */
class StreamRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getServerUrl(): String {
        return if (isInternetMode()) {
            prefs.getString(KEY_PUBLIC_SERVER_URL, DEFAULT_PUBLIC_SERVER_URL) ?: DEFAULT_PUBLIC_SERVER_URL
        } else {
            prefs.getString(KEY_LOCAL_SERVER_URL, DEFAULT_LOCAL_SERVER_URL) ?: DEFAULT_LOCAL_SERVER_URL
        }
    }

    fun setServerUrl(url: String) {
        if (isInternetMode()) {
            prefs.edit().putString(KEY_PUBLIC_SERVER_URL, url).apply()
        } else {
            prefs.edit().putString(KEY_LOCAL_SERVER_URL, url).apply()
        }
    }

    fun getLocalServerUrl(): String {
        return prefs.getString(KEY_LOCAL_SERVER_URL, DEFAULT_LOCAL_SERVER_URL) ?: DEFAULT_LOCAL_SERVER_URL
    }

    fun getPublicServerUrl(): String {
        return prefs.getString(KEY_PUBLIC_SERVER_URL, DEFAULT_PUBLIC_SERVER_URL) ?: DEFAULT_PUBLIC_SERVER_URL
    }

    fun isInternetMode(): Boolean {
        return prefs.getBoolean(KEY_INTERNET_MODE, false)
    }

    fun setInternetMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTERNET_MODE, enabled).apply()
    }

    fun setStreaming(isStreaming: Boolean) {
        prefs.edit().putBoolean(KEY_IS_STREAMING, isStreaming).apply()
    }

    fun isStreaming(): Boolean {
        return prefs.getBoolean(KEY_IS_STREAMING, false)
    }

    fun setStreamCredentials(streamId: String, token: String, viewerUrl: String) {
        prefs.edit()
            .putString(KEY_STREAM_ID, streamId)
            .putString(KEY_STREAM_TOKEN, token)
            .putString(KEY_VIEWER_URL, viewerUrl)
            .apply()
    }

    fun getStreamId(): String {
        return prefs.getString(KEY_STREAM_ID, "") ?: ""
    }

    fun getStreamToken(): String {
        return prefs.getString(KEY_STREAM_TOKEN, "") ?: ""
    }

    fun getViewerUrl(): String {
        return prefs.getString(KEY_VIEWER_URL, "") ?: ""
    }

    fun clearStreamCredentials() {
        prefs.edit()
            .remove(KEY_STREAM_ID)
            .remove(KEY_STREAM_TOKEN)
            .remove(KEY_VIEWER_URL)
            .apply()
    }

    fun getVideoWidth(): Int {
        return prefs.getInt(KEY_VIDEO_WIDTH, DEFAULT_VIDEO_WIDTH)
    }

    fun getVideoHeight(): Int {
        return prefs.getInt(KEY_VIDEO_HEIGHT, DEFAULT_VIDEO_HEIGHT)
    }

    fun getVideoFps(): Int {
        return prefs.getInt(KEY_VIDEO_FPS, DEFAULT_VIDEO_FPS)
    }

    fun setVideoResolution(width: Int, height: Int) {
        prefs.edit()
            .putInt(KEY_VIDEO_WIDTH, width)
            .putInt(KEY_VIDEO_HEIGHT, height)
            .apply()
    }

    fun setVideoFps(fps: Int) {
        prefs.edit().putInt(KEY_VIDEO_FPS, fps).apply()
    }

    fun isAudioEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUDIO_ENABLED, DEFAULT_AUDIO_ENABLED)
    }

    fun setAudioEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_ENABLED, enabled).apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "FloatStreamPrefs"
        const val KEY_LOCAL_SERVER_URL = "local_server_url"
        const val KEY_PUBLIC_SERVER_URL = "public_server_url"
        const val KEY_INTERNET_MODE = "internet_mode"
        const val KEY_IS_STREAMING = "is_streaming"
        const val KEY_STREAM_ID = "stream_id"
        const val KEY_STREAM_TOKEN = "stream_token"
        const val KEY_VIEWER_URL = "viewer_url"
        const val KEY_VIDEO_WIDTH = "video_width"
        const val KEY_VIDEO_HEIGHT = "video_height"
        const val KEY_VIDEO_FPS = "video_fps"
        const val KEY_AUDIO_ENABLED = "audio_enabled"

        private const val DEFAULT_LOCAL_SERVER_URL = "http://192.168.1.100:3000"
        private const val DEFAULT_PUBLIC_SERVER_URL = "https://stream.example.com"

        const val DEFAULT_VIDEO_WIDTH = 1280
        const val DEFAULT_VIDEO_HEIGHT = 720
        const val DEFAULT_VIDEO_FPS = 30
        const val DEFAULT_AUDIO_ENABLED = false

        val QUALITY_480P = Pair(854, 480)
        val QUALITY_720P = Pair(1280, 720)
        val QUALITY_1080P = Pair(1920, 1080)
    }
}

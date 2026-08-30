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
        return prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
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

    companion object {
        private const val PREFS_NAME = "FloatStreamPrefs"
        private const val KEY_SERVER_URL = "stream_server_url"
        private const val KEY_IS_STREAMING = "is_streaming"
        private const val KEY_STREAM_ID = "stream_id"
        private const val KEY_STREAM_TOKEN = "stream_token"
        private const val KEY_VIEWER_URL = "viewer_url"

        private const val DEFAULT_SERVER_URL = "http://192.168.1.100:3000"
    }
}

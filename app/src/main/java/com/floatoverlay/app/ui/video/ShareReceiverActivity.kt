package com.floatoverlay.app.ui.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.floatoverlay.app.R
import com.floatoverlay.app.data.SavedVideoRepository
import com.floatoverlay.app.model.SavedVideo
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Invisible share receiver for TikTok/YouTube video links.
 *
 * Parses the shared URL, resolves TikTok short links, and stores the video
 * so the user can play it in a floating overlay later.
 */
class ShareReceiverActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_receiver)

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val appContext = applicationContext
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            Thread {
                val message = handleSharedText(appContext, sharedText)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this@ShareReceiverActivity, message, Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            }.start()
        } else {
            Toast.makeText(this, "Unsupported share", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun handleSharedText(context: Context, text: String?): String {
        if (text.isNullOrBlank()) {
            return "No link found"
        }

        val url = extractFirstUrl(text)
        if (url == null) {
            return "No link found"
        }

        val result = parseVideoUrl(url)
        if (result == null) {
            return "Could not save video"
        }

        SavedVideoRepository(context).saveVideo(
            SavedVideo(
                source = result.source,
                videoId = result.videoId,
                originalUrl = url
            )
        )
        return "Video saved"
    }

    private fun extractFirstUrl(text: String): String? {
        val regex = Regex("https?://\\S+")
        return regex.find(text)?.value?.trim()?.trimEnd(',', '.', ')')
    }

    private data class ParsedVideo(
        val source: SavedVideo.Source,
        val videoId: String
    )

    private fun parseVideoUrl(input: String): ParsedVideo? {
        val url = input.lowercase()

        // YouTube
        when {
            url.contains("youtube.com/watch") -> {
                val id = extractQueryParam(input, "v") ?: return null
                if (isValidYouTubeId(id)) return ParsedVideo(SavedVideo.Source.YOUTUBE, id)
            }
            url.contains("youtu.be/") -> {
                val id = input.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                if (isValidYouTubeId(id)) return ParsedVideo(SavedVideo.Source.YOUTUBE, id)
            }
            url.contains("youtube.com/shorts/") -> {
                val id = input.substringAfter("youtube.com/shorts/").substringBefore("?").substringBefore("&")
                if (isValidYouTubeId(id)) return ParsedVideo(SavedVideo.Source.YOUTUBE, id)
            }
        }

        // TikTok canonical
        val tiktokCanonicalRegex = Regex("tiktok\\.com/@[^/]+/video/(\\d+)", RegexOption.IGNORE_CASE)
        tiktokCanonicalRegex.find(input)?.groupValues?.get(1)?.let {
            return ParsedVideo(SavedVideo.Source.TIKTOK, it)
        }

        // TikTok mobile canonical
        val tiktokMobileRegex = Regex("m\\.tiktok\\.com/.*/video/(\\d+)", RegexOption.IGNORE_CASE)
        tiktokMobileRegex.find(input)?.groupValues?.get(1)?.let {
            return ParsedVideo(SavedVideo.Source.TIKTOK, it)
        }

        // TikTok short link - resolve redirect
        if (url.contains("vm.tiktok.com/") ||
            url.contains("vt.tiktok.com/") ||
            url.contains("t.tiktok.com/") ||
            url.contains("tiktok.com/t/")
        ) {
            val resolved = resolveRedirect(input)
            if (resolved != null && resolved != input) {
                return parseVideoUrl(resolved)
            }
        }

        return null
    }

    private fun extractQueryParam(url: String, key: String): String? {
        val start = url.indexOf("$key=")
        if (start == -1) return null
        val from = start + key.length + 1
        val end = url.indexOf("&", from)
        return if (end == -1) url.substring(from) else url.substring(from, end)
    }

    private fun isValidYouTubeId(id: String): Boolean {
        return id.matches(Regex("[a-zA-Z0-9_-]{11}"))
    }

    private fun resolveRedirect(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
                )
                .build()
            client.newCall(request).execute().use { response ->
                // If the server returned a non-2xx status, the body may still contain a redirect.
                response.request.url.toString()
            }
        } catch (e: Exception) {
            null
        }
    }
}

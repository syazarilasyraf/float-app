package com.floatoverlay.app.ui.stream

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.floatoverlay.app.R
import com.floatoverlay.app.data.StreamRepository
import com.floatoverlay.app.stream.StreamService

/**
 * UI for the private screen-streaming feature.
 *
 * For the MVP this is a lightweight control panel:
 * - status label
 * - quality/bitrate summary
 * - viewer link
 * - start/stream buttons
 *
 * The actual capture and WebRTC work lives in [com.floatoverlay.app.stream.StreamService].
 */
class StreamFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var repository: StreamRepository

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var linkText: TextView
    private lateinit var serverUrlInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var copyLinkButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = StreamRepository(requireContext())
    }

    override fun onStart() {
        super.onStart()
        repository.registerListener(this)
    }

    override fun onStop() {
        repository.unregisterListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == StreamRepository.KEY_IS_STREAMING || key == StreamRepository.KEY_VIEWER_URL) {
            activity?.runOnUiThread { updateUi() }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stream, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.streamStatusText)
        statsText = view.findViewById(R.id.streamStatsText)
        linkText = view.findViewById(R.id.streamLinkText)
        serverUrlInput = view.findViewById(R.id.serverUrlInput)
        startButton = view.findViewById(R.id.startStreamButton)
        stopButton = view.findViewById(R.id.stopStreamButton)
        copyLinkButton = view.findViewById(R.id.copyLinkButton)

        serverUrlInput.setText(repository.getServerUrl())
        serverUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                repository.setServerUrl(serverUrlInput.text.toString().trim())
            }
        }

        startButton.setOnClickListener {
            repository.setServerUrl(serverUrlInput.text.toString().trim())
            (activity as? StreamLauncher)?.requestStartStream()
        }

        stopButton.setOnClickListener {
            StreamService.stop(requireContext())
        }

        copyLinkButton.setOnClickListener {
            val link = repository.getViewerUrl()
            if (link.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Float stream link", link))
                Toast.makeText(requireContext(), "Link copied", Toast.LENGTH_SHORT).show()
            }
        }

        updateUi()
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi() {
        val isLive = repository.isStreaming()
        val link = repository.getViewerUrl()
        if (isLive) {
            statusText.text = "Streaming \uD83D\uDD34 LIVE"
            statsText.text = "Quality: 720p / 30 FPS\nBitrate: 2.5 Mbps\nViewers: 1"
            linkText.text = link
            startButton.isEnabled = false
            stopButton.isEnabled = true
            copyLinkButton.isEnabled = link.isNotBlank()
        } else {
            statusText.text = "Status: OFFLINE"
            statsText.text = "Quality: 720p / 30 FPS\nBitrate: 2.5 Mbps"
            linkText.text = ""
            startButton.isEnabled = true
            stopButton.isEnabled = false
            copyLinkButton.isEnabled = false
        }
    }

    /**
     * Callback contract implemented by the host Activity so the fragment can ask
     * it to start the MediaProjection capture flow.
     */
    interface StreamLauncher {
        fun requestStartStream()
    }
}

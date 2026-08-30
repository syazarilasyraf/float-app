package com.floatoverlay.app.ui.stream

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.floatoverlay.app.R

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
class StreamFragment : Fragment() {

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var linkText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var copyLinkButton: Button

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
        startButton = view.findViewById(R.id.startStreamButton)
        stopButton = view.findViewById(R.id.stopStreamButton)
        copyLinkButton = view.findViewById(R.id.copyLinkButton)

        updateUi(false, null)

        startButton.setOnClickListener {
            // MediaProjection capture will be triggered from MainActivity so the
            // result Intent can be forwarded to StreamService.
            (activity as? StreamLauncher)?.requestStartStream()
        }

        stopButton.setOnClickListener {
            // TODO: stop StreamService
        }

        copyLinkButton.setOnClickListener {
            // TODO: copy viewer link to clipboard
        }
    }

    private fun updateUi(isLive: Boolean, link: String?) {
        if (isLive) {
            statusText.text = "Streaming \uD83D\uDD34 LIVE"
            statsText.text = "Quality: 720p / 30 FPS\nBitrate: 2.5 Mbps\nViewers: 1"
            linkText.text = link ?: ""
            startButton.isEnabled = false
            stopButton.isEnabled = true
            copyLinkButton.isEnabled = !link.isNullOrBlank()
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

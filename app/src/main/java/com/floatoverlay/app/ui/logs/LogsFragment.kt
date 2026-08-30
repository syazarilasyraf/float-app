package com.floatoverlay.app.ui.logs

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.floatoverlay.app.LogStore
import com.floatoverlay.app.R

class LogsFragment : Fragment() {

    private lateinit var logText: TextView
    private lateinit var clearButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val throttleMs = 1000L
    private var pendingRefresh = false

    private val logListener: () -> Unit = {
        activity?.runOnUiThread {
            postRefreshLogs()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_logs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        logText = view.findViewById(R.id.logText)
        clearButton = view.findViewById(R.id.clearLogsButton)
        clearButton.setOnClickListener {
            LogStore.clear()
        }
        postRefreshLogs()
    }

    override fun onResume() {
        super.onResume()
        LogStore.addListener(logListener)
        pendingRefresh = false
        refreshLogs()
    }

    override fun onPause() {
        super.onPause()
        LogStore.removeListener(logListener)
        pendingRefresh = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun postRefreshLogs() {
        if (pendingRefresh) return
        pendingRefresh = true
        handler.postDelayed({
            pendingRefresh = false
            refreshLogs()
        }, throttleMs)
    }

    private fun refreshLogs() {
        val text = LogStore.getLogs().joinToString("\n")
        logText.text = text.ifBlank { "No logs yet." }
    }
}

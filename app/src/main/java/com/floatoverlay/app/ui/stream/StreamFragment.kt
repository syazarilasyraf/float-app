package com.floatoverlay.app.ui.stream

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.floatoverlay.app.R
import com.floatoverlay.app.data.StreamRepository
import com.floatoverlay.app.stream.StreamService

/**
 * UI for the private screen-streaming feature.
 *
 * Lightweight control panel:
 * - status label
 * - local server URL
 * - quality / FPS selection
 * - audio toggle
 * - viewer link
 * - start/stop/copy buttons
 *
 * The actual capture and WebRTC work lives in [com.floatoverlay.app.stream.StreamService].
 */
class StreamFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var repository: StreamRepository

    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var linkText: TextView
    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var qualitySpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var audioSwitch: MaterialSwitch
    private lateinit var gameVolumeSeek: SeekBar
    private lateinit var micVolumeSeek: SeekBar
    private lateinit var gameVolumeLabel: TextView
    private lateinit var micVolumeLabel: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var copyLinkButton: Button

    private val qualityOptions = listOf(
        Triple("480p", 854, 480),
        Triple("720p", 1280, 720),
        Triple("1080p", 1920, 1080)
    )

    private val fpsOptions = listOf(30, 60)

    private var pendingStreamStart = false

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingStreamStart) {
                pendingStreamStart = false
                (activity as? StreamLauncher)?.requestStartStream()
            }
        } else {
            pendingStreamStart = false
            audioSwitch.isChecked = false
            repository.setAudioEnabled(false)
            Toast.makeText(requireContext(), "Microphone audio disabled: permission denied", Toast.LENGTH_LONG).show()
        }
    }

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
        qualitySpinner = view.findViewById(R.id.qualitySpinner)
        fpsSpinner = view.findViewById(R.id.fpsSpinner)
        audioSwitch = view.findViewById(R.id.audioSwitch)
        gameVolumeSeek = view.findViewById(R.id.gameVolumeSeek)
        micVolumeSeek = view.findViewById(R.id.micVolumeSeek)
        gameVolumeLabel = view.findViewById(R.id.gameVolumeLabel)
        micVolumeLabel = view.findViewById(R.id.micVolumeLabel)
        startButton = view.findViewById(R.id.startStreamButton)
        stopButton = view.findViewById(R.id.stopStreamButton)
        copyLinkButton = view.findViewById(R.id.copyLinkButton)

        serverUrlInput.setText(repository.getServerUrl())
        serverUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                repository.setServerUrl(serverUrlInput.text.toString().trim())
            }
        }

        setupQualitySpinner()
        setupFpsSpinner()
        setupAudioSwitch()
        setupVolumeControls()

        startButton.setOnClickListener {
            repository.setServerUrl(serverUrlInput.text.toString().trim())
            saveStreamSettings()
            startStreamWithPermissionCheck()
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

    private fun setupQualitySpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            qualityOptions.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        qualitySpinner.adapter = adapter

        val savedWidth = repository.getVideoWidth()
        val index = qualityOptions.indexOfFirst { it.second == savedWidth }.coerceAtLeast(0)
        qualitySpinner.setSelection(index)

        qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveStreamSettings()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupFpsSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            fpsOptions.map { "${it} FPS" }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fpsSpinner.adapter = adapter

        val savedFps = repository.getVideoFps()
        val index = fpsOptions.indexOf(savedFps).coerceAtLeast(0)
        fpsSpinner.setSelection(index)

        fpsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                saveStreamSettings()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupAudioSwitch() {
        audioSwitch.isChecked = repository.isAudioEnabled()
        audioSwitch.setOnCheckedChangeListener { _, isChecked ->
            repository.setAudioEnabled(isChecked)
        }
    }

    private fun setupVolumeControls() {
        gameVolumeSeek.progress = repository.getGameVolume()
        micVolumeSeek.progress = repository.getMicVolume()
        gameVolumeLabel.text = "Game audio volume: ${gameVolumeSeek.progress}%"
        micVolumeLabel.text = "Microphone volume: ${micVolumeSeek.progress}%"

        gameVolumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                gameVolumeLabel.text = "Game audio volume: $progress%"
                repository.setGameVolume(progress)
                pushVolumesToService()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        micVolumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                micVolumeLabel.text = "Microphone volume: $progress%"
                repository.setMicVolume(progress)
                pushVolumesToService()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun pushVolumesToService() {
        if (repository.isStreaming()) {
            StreamService.setVolumes(requireContext(), gameVolumeSeek.progress, micVolumeSeek.progress)
        }
    }

    private fun saveStreamSettings() {
        val quality = qualityOptions[qualitySpinner.selectedItemPosition]
        val fps = fpsOptions[fpsSpinner.selectedItemPosition]
        repository.setVideoResolution(quality.second, quality.third)
        repository.setVideoFps(fps)
        repository.setAudioEnabled(audioSwitch.isChecked)
    }

    private fun startStreamWithPermissionCheck() {
        if (!audioSwitch.isChecked) {
            (activity as? StreamLauncher)?.requestStartStream()
            return
        }

        when (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                (activity as? StreamLauncher)?.requestStartStream()
            }
            else -> {
                pendingStreamStart = true
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun updateUi() {
        val isLive = repository.isStreaming()
        val link = repository.getViewerUrl()
        val quality = qualityOptions[qualitySpinner.selectedItemPosition].first
        val fps = fpsOptions[fpsSpinner.selectedItemPosition]
        val audio = if (audioSwitch.isChecked) "Audio: on" else "Audio: off"

        if (isLive) {
            statusText.text = "Streaming \uD83D\uDD34 LIVE"
            statsText.text = "Quality: $quality / ${fps} FPS\nBitrate: ~2.5 Mbps\n$audio\nViewers: 1"
            linkText.text = link
            startButton.isEnabled = false
            stopButton.isEnabled = true
            copyLinkButton.isEnabled = link.isNotBlank()
            qualitySpinner.isEnabled = false
            fpsSpinner.isEnabled = false
            audioSwitch.isEnabled = false
            serverUrlInput.isEnabled = false
        } else {
            statusText.text = "Status: OFFLINE"
            statsText.text = "Quality: $quality / ${fps} FPS\nBitrate: ~2.5 Mbps\n$audio"
            linkText.text = ""
            startButton.isEnabled = true
            stopButton.isEnabled = false
            copyLinkButton.isEnabled = false
            qualitySpinner.isEnabled = true
            fpsSpinner.isEnabled = true
            audioSwitch.isEnabled = true
            serverUrlInput.isEnabled = true
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

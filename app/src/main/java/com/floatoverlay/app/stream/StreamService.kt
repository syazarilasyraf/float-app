package com.floatoverlay.app.stream

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.floatoverlay.app.LogStore
import com.floatoverlay.app.MainActivity
import com.floatoverlay.app.R
import com.floatoverlay.app.data.StreamRepository
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Foreground service that captures the device screen via MediaProjection and
 * streams it privately to a browser viewer over WebRTC.
 *
 * The service keeps the capture/encoding/WebRTC pipeline alive while the user
 * is in another app (e.g. Project Zomboid via ZomDroid).
 */
class StreamService : Service() {

    private var capturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var audioTrack: org.webrtc.AudioTrack? = null
    private var audioDeviceModule: org.webrtc.audio.AudioDeviceModule? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var signalingClient: SignalingClient? = null
    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var audioDataChannel: DataChannel? = null
    private var audioCaptureThread: Thread? = null
    @Volatile
    private var isAudioCapturing = false
    private var isStopping = false
    private val statsHandler = Handler(Looper.getMainLooper())
    private val statsRunnable = object : Runnable {
        override fun run() {
            logConnectionStats()
            statsHandler.postDelayed(this, 5000)
        }
    }

    private lateinit var streamRepository: StreamRepository
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        streamRepository = StreamRepository(this)
        initWebRtc()
        LogStore.log(TAG, "StreamService created")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                val width = intent.getIntExtra(EXTRA_VIDEO_WIDTH, StreamRepository.DEFAULT_VIDEO_WIDTH)
                val height = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, StreamRepository.DEFAULT_VIDEO_HEIGHT)
                val fps = intent.getIntExtra(EXTRA_VIDEO_FPS, StreamRepository.DEFAULT_VIDEO_FPS)
                if (data != null && resultCode == Activity.RESULT_OK) {
                    // Start foreground immediately to satisfy Android 12+ deadlines.
                    startForegroundWithNotification()
                    startStreaming(resultCode, data, width, height, fps)
                } else {
                    LogStore.log(TAG, "Start stream requested without MediaProjection permission")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopStreaming()
            }
        }
        return START_NOT_STICKY
    }

    private fun initWebRtc() {
        try {
            if (!factoryInitialized) {
                val options = PeerConnectionFactory.InitializationOptions.builder(this)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                factoryInitialized = true
            }

            eglBase = EglBase.createEgl14(EglBase.CONFIG_PLAIN)
            val eglContext = eglBase?.eglBaseContext

            // enableIntelVp8Encoder=true, enableH264HighProfile=false for maximum device compatibility.
            val encoderFactory = DefaultVideoEncoderFactory(eglContext, true, false)
            val decoderFactory = org.webrtc.DefaultVideoDecoderFactory(eglContext)

            audioDeviceModule = org.webrtc.audio.JavaAudioDeviceModule.builder(applicationContext)
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .createAudioDeviceModule()

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()

            surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglContext)
            LogStore.log(TAG, "WebRTC initialized")
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to initialize WebRTC", e)
        }
    }

    private fun startStreaming(resultCode: Int, data: Intent, targetWidth: Int, targetHeight: Int, targetFps: Int) {
        if (capturer != null) {
            LogStore.log(TAG, "Stream already active")
            return
        }

        if (peerConnectionFactory == null || surfaceTextureHelper == null) {
            LogStore.log(TAG, "WebRTC not initialized, cannot start stream")
            stopStreaming()
            return
        }

        val metrics = DisplayMetrics()
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        @Suppress("DEPRECATION")
        defaultDisplay.getRealMetrics(metrics)

        // Scale the phone's current landscape size to fit within the selected target resolution.
        val (width, height) = computeLandscapeSize(metrics.widthPixels, metrics.heightPixels, targetWidth, targetHeight)

        try {
            // Own the MediaProjection token for audio playback capture and lifecycle.
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)?.apply {
                registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        LogStore.log(TAG, "MediaProjection stopped by system")
                        stopStreaming()
                    }
                }, null)
            }

            val source = peerConnectionFactory!!.createVideoSource(true)
            videoSource = source

            // ScreenCapturerAndroid creates its own MediaProjection from the same consent data.
            capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    LogStore.log(TAG, "ScreenCapturer MediaProjection stopped by system")
                    stopStreaming()
                }
            }).apply {
                initialize(surfaceTextureHelper, applicationContext, source.capturerObserver)
                startCapture(width, height, targetFps)
            }

            videoTrack = peerConnectionFactory?.createVideoTrack(VIDEO_TRACK_ID, source)

            if (streamRepository.isAudioEnabled()) {
                val audioConstraints = MediaConstraints().apply {
                    optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                }
                audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
                audioTrack = peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
                // Disable the native mic track; internal audio is sent over the data channel.
                audioTrack?.setEnabled(false)
                LogStore.log(TAG, "Audio track created (microphone, disabled)")
            }

            // Start internal audio capture. Failure here is non-fatal: video keeps streaming.
            startInternalAudioCapture()

            videoTrack?.addSink(object : VideoSink {
                private var frameCount = 0
                private var lastLog = System.currentTimeMillis()
                override fun onFrame(frame: VideoFrame) {
                    frameCount++
                    val now = System.currentTimeMillis()
                    if (now - lastLog >= 5000) {
                        LogStore.log(TAG, "Local frames produced: $frameCount (${frame.buffer.width}x${frame.buffer.height})")
                        frameCount = 0
                        lastLog = now
                    }
                }
            })

            streamRepository.setStreaming(true)
            LogStore.log(TAG, "Screen capture started ${width}x${height} @ ${targetFps}fps")

            createStreamAndConnect()
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to start screen capture", e)
            stopStreaming()
        }
    }

    private fun createStreamAndConnect() {
        val serverUrl = streamRepository.getServerUrl()
        val request = Request.Builder()
            .url("$serverUrl/stream")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LogStore.logError(TAG, "Failed to create stream session", e)
                stopStreaming()
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful || body.isBlank()) {
                    LogStore.log(TAG, "Failed to create stream session: ${response.code}")
                    stopStreaming()
                    return
                }
                try {
                    val json = JSONObject(body)
                    val streamId = json.getString("streamId")
                    val token = json.getString("token")
                    val viewerUrl = json.getString("viewerUrl")

                    streamRepository.setStreamCredentials(streamId, token, viewerUrl)
                    runOnMainThread { connectPeer(streamId, token, viewerUrl) }
                } catch (e: Exception) {
                    LogStore.logError(TAG, "Failed to parse stream session", e)
                    stopStreaming()
                }
            }
        })
    }

    private fun connectPeer(streamId: String, token: String, viewerUrl: String) {
        val serverUrl = streamRepository.getServerUrl()
        val iceRequest = Request.Builder()
            .url("$serverUrl/ice-config")
            .get()
            .build()

        httpClient.newCall(iceRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                LogStore.logError(TAG, "Failed to fetch ICE config, using defaults", e)
                runOnMainThread { createPeerConnection(defaultIceServers(), streamId, token, viewerUrl) }
            }

            override fun onResponse(call: Call, response: Response) {
                val servers = try {
                    val body = response.body?.string() ?: ""
                    parseIceServers(body)
                } catch (e: Exception) {
                    LogStore.logError(TAG, "Failed to parse ICE config, using defaults", e)
                    defaultIceServers()
                }
                runOnMainThread { createPeerConnection(servers, streamId, token, viewerUrl) }
            }
        })
    }

    private fun createPeerConnection(iceServers: List<PeerConnection.IceServer>, streamId: String, token: String, viewerUrl: String) {
        try {
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, peerConnectionObserver)?.apply {
                videoTrack?.let { track ->
                    val transceiver = addTransceiver(
                        track,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                    )
                    LogStore.log(TAG, "Video transceiver added: direction=${transceiver?.direction}, mid=${transceiver?.mid}")
                }
                audioTrack?.let { track ->
                    val transceiver = addTransceiver(
                        track,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                    )
                    LogStore.log(TAG, "Audio transceiver added: direction=${transceiver?.direction}, mid=${transceiver?.mid}")
                }

                val dcInit = DataChannel.Init().apply {
                    ordered = true
                    maxRetransmits = -1
                }
                audioDataChannel = createDataChannel(DATA_CHANNEL_LABEL, dcInit)
                audioDataChannel?.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    override fun onStateChange() {
                        LogStore.log(TAG, "Audio data channel state: ${audioDataChannel?.state()}")
                    }
                    override fun onMessage(buffer: DataChannel.Buffer) {}
                })
                LogStore.log(TAG, "Audio data channel created")
            }

            signalingClient = SignalingClient(
                streamRepository.getServerUrl(),
                streamId,
                token,
                signalingListener
            ).apply { connect() }

            statsHandler.postDelayed(statsRunnable, 5000)

            LogStore.log(TAG, "Peer connection created, viewer link: $viewerUrl")
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to create peer connection", e)
            stopStreaming()
        }
    }

    private fun defaultIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }

    private fun parseIceServers(json: String): List<PeerConnection.IceServer> {
        val root = JSONObject(json)
        val array = root.getJSONArray("iceServers")
        val servers = mutableListOf<PeerConnection.IceServer>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val urls = obj.get("urls")
            val builder = when (urls) {
                is String -> PeerConnection.IceServer.builder(urls)
                is JSONArray -> {
                    val list = mutableListOf<String>()
                    for (j in 0 until urls.length()) list.add(urls.getString(j))
                    PeerConnection.IceServer.builder(list)
                }
                else -> continue
            }
            if (obj.has("username")) builder.setUsername(obj.getString("username"))
            if (obj.has("credential")) builder.setPassword(obj.getString("credential"))
            servers.add(builder.createIceServer())
        }
        return servers
    }

    private val signalingListener = object : SignalingClient.Listener {
        override fun onConnected() {
            LogStore.log(TAG, "Signaling connected, waiting for viewer")
        }

        override fun onViewerJoined() {
            LogStore.log(TAG, "Viewer joined, pc=${peerConnection != null}, track=${videoTrack != null}, signaling=${signalingClient != null}")
            runOnMainThread {
                if (peerConnection == null || videoTrack == null) {
                    LogStore.log(TAG, "Cannot create offer: peerConnection or videoTrack is null")
                    return@runOnMainThread
                }
                try {
                    createOffer()
                } catch (e: Exception) {
                    LogStore.logError(TAG, "Failed to create offer", e)
                }
            }
        }

        override fun onOfferAnswerReceived(sdpType: String, sdp: String) {
            if (sdpType == "answer") {
                LogStore.log(TAG, "Received answer, setting remote description")
                peerConnection?.setRemoteDescription(
                    sdpObserver("set-remote-answer"),
                    SessionDescription(SessionDescription.Type.ANSWER, sdp)
                )
            }
        }

        override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
            peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
        }

        override fun onDisconnected() {
            LogStore.log(TAG, "Viewer disconnected")
        }

        override fun onError(message: String) {
            LogStore.log(TAG, "Signaling error: $message")
        }
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) {
            LogStore.log(TAG, "Signaling state: $newState")
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            LogStore.log(TAG, "ICE connection state: $newState")
            if (newState == PeerConnection.IceConnectionState.FAILED) {
                stopStreaming()
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                signalingClient?.sendIceCandidate(it.sdpMid, it.sdpMLineIndex, it.sdp)
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: org.webrtc.MediaStream?) {}
        override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
        override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
        override fun onRemoveTrack(receiver: org.webrtc.RtpReceiver?) {}
        override fun onSelectedCandidatePairChanged(event: org.webrtc.CandidatePairChangeEvent?) {}
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            LogStore.log(TAG, "Peer connection state: $newState")
        }
        override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {}
    }

    private fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }
        peerConnection?.createOffer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                LogStore.log(TAG, "Offer created")
                try {
                    peerConnection?.setLocalDescription(sdpObserver("set-local-offer"), sdp)
                    signalingClient?.sendOffer(sdp.type.canonicalForm(), sdp.description)
                } catch (e: Exception) {
                    LogStore.logError(TAG, "Failed to send offer", e)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String) {
                LogStore.log(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(error: String) {
                LogStore.log(TAG, "Failed to set local offer: $error")
            }
        }, constraints)
    }

    private fun sdpObserver(label: String) = object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {
            LogStore.log(TAG, "$label succeeded")
        }
        override fun onCreateFailure(error: String) {
            LogStore.log(TAG, "$label create failure: $error")
        }
        override fun onSetFailure(error: String) {
            LogStore.log(TAG, "$label set failure: $error")
        }
    }

    private fun logConnectionStats() {
        val pc = peerConnection ?: return
        try {
            pc.getStats { report ->
                var bytesSent = 0L
                var packetsSent = 0L
                var outboundFound = false
                for (stats in report.statsMap.values) {
                    if (stats.type == "outbound-rtp" && stats.members["kind"] == "video") {
                        outboundFound = true
                        bytesSent += (stats.members["bytesSent"] as? Number)?.toLong() ?: 0L
                        packetsSent += (stats.members["packetsSent"] as? Number)?.toLong() ?: 0L
                    }
                }
                if (outboundFound) {
                    LogStore.log(TAG, "Outbound video bytesSent=$bytesSent packetsSent=$packetsSent")
                }
            }
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to get stats", e)
        }
    }

    private fun computeLandscapeSize(screenWidth: Int, screenHeight: Int, targetWidth: Int, targetHeight: Int): Pair<Int, Int> {
        val isLandscape = screenWidth >= screenHeight
        val (longer, shorter) = if (isLandscape) {
            screenWidth to screenHeight
        } else {
            screenHeight to screenWidth
        }

        val scale = minOf(targetWidth.toFloat() / longer, targetHeight.toFloat() / shorter, 1f)
        val w = (longer * scale).toInt() and 0xFFFE
        val h = (shorter * scale).toInt() and 0xFFFE
        return if (isLandscape) w to h else h to w
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()

        val stopIntent = Intent(this, StreamService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.stream_running))
            .setContentText(getString(R.string.stream_running_desc))
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stream_action_stop), stopPendingIntent)
            .build()

        startForeground(FOREGROUND_SERVICE_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.stream_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopStreaming() {
        synchronized(this) {
            if (isStopping) return
            isStopping = true
        }
        LogStore.log(TAG, "Stopping stream")
        statsHandler.removeCallbacks(statsRunnable)
        try {
            httpClient.dispatcher.cancelAll()

            signalingClient?.disconnect()
            signalingClient = null

            peerConnection?.close()
            peerConnection = null

            // Stop internal audio capture before disposing the capturer so the audio thread
            // does not reference a released AudioRecord.
            isAudioCapturing = false
            try {
                // Stopping the record unblocks the capture thread's read() call.
                audioRecord?.stop()
            } catch (e: Exception) {
                LogStore.logError(TAG, "AudioRecord stop failed", e)
            }
            try {
                audioCaptureThread?.join(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            audioCaptureThread = null

            try {
                audioDataChannel?.close()
            } catch (e: Exception) {
                LogStore.logError(TAG, "Failed to close audio data channel", e)
            }
            audioDataChannel = null

            // Release the record if the capture thread has not already done so.
            val record = audioRecord
            if (record != null) {
                try {
                    record.release()
                } catch (e: Exception) {
                    LogStore.logError(TAG, "AudioRecord release failed", e)
                }
                audioRecord = null
            }

            try {
                mediaProjection?.stop()
            } catch (e: Exception) {
                LogStore.logError(TAG, "MediaProjection stop failed", e)
            }
            mediaProjection = null

            capturer?.stopCapture()
            capturer?.dispose()
            capturer = null

            videoTrack?.dispose()
            videoTrack = null

            audioTrack?.dispose()
            audioTrack = null

            audioSource?.dispose()
            audioSource = null

            videoSource?.dispose()
            videoSource = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            eglBase?.releaseSurface()
            eglBase?.release()
            eglBase = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            try {
                audioDeviceModule?.release()
            } catch (e: Exception) {
                LogStore.logError(TAG, "Audio device module release failed", e)
            }
            audioDeviceModule = null
        } catch (e: Exception) {
            LogStore.logError(TAG, "Error during stream teardown", e)
        }

        streamRepository.setStreaming(false)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            LogStore.logError(TAG, "stopForeground failed", e)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            Handler(Looper.getMainLooper()).post(block)
        }
    }

    private fun startInternalAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            LogStore.log(TAG, "Internal audio capture requires Android 10+ (API 29), skipping audio")
            return
        }

        val projection = mediaProjection
        if (projection == null) {
            LogStore.log(TAG, "MediaProjection not available, skipping internal audio capture")
            return
        }

        try {
            val config = createAudioPlaybackCaptureConfig(projection)
            val minBuf = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                LogStore.log(TAG, "AudioRecord min buffer size invalid ($minBuf), skipping audio")
                return
            }

            val record = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setAudioPlaybackCaptureConfig(config)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                LogStore.log(TAG, "AudioRecord failed to initialize, skipping internal audio capture")
                try {
                    record.release()
                } catch (e: Exception) {
                    LogStore.logError(TAG, "Failed to release uninitialized AudioRecord", e)
                }
                return
            }

            audioRecord = record
            startAudioCaptureThread(record)
            LogStore.log(TAG, "Internal audio capture started (48kHz stereo 16-bit)")
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to start internal audio capture", e)
        }
    }

    private fun createAudioPlaybackCaptureConfig(mediaProjection: MediaProjection): AudioPlaybackCaptureConfiguration {
        return AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
    }

    private fun startAudioCaptureThread(record: AudioRecord) {
        isAudioCapturing = true
        audioCaptureThread = Thread({
            try {
                record.startRecording()
                val chunkBytes = ByteArray(BYTES_PER_CHUNK)
                while (isAudioCapturing) {
                    val read = readFullChunk(record, chunkBytes)
                    if (read != BYTES_PER_CHUNK) {
                        // Stop requested or read error; exit loop.
                        break
                    }

                    val timestampNs = SystemClock.elapsedRealtimeNanos()
                    val combined = ByteArray(TIMESTAMP_BYTES + BYTES_PER_CHUNK)
                    ByteBuffer.wrap(combined)
                        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        .putLong(timestampNs)
                        .put(chunkBytes)

                    val dc = audioDataChannel
                    if (dc?.state() == DataChannel.State.OPEN) {
                        val buffer = DataChannel.Buffer(ByteBuffer.wrap(combined), true)
                        dc.send(buffer)
                    }
                }
            } catch (e: Exception) {
                LogStore.logError(TAG, "Audio capture thread error", e)
            } finally {
                try {
                    record.stop()
                } catch (e: Exception) {
                    LogStore.logError(TAG, "AudioRecord stop failed in capture thread", e)
                }
                try {
                    record.release()
                } catch (e: Exception) {
                    LogStore.logError(TAG, "AudioRecord release failed in capture thread", e)
                }
                audioRecord = null
            }
        }, "AudioCaptureThread").apply { start() }
    }

    private fun readFullChunk(record: AudioRecord, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size && isAudioCapturing) {
            val read = record.read(buffer, totalRead, buffer.size - totalRead)
            if (read < 0) {
                // Error code from AudioRecord.read (e.g. ERROR_INVALID_OPERATION).
                return read
            }
            totalRead += read
            if (read == 0) {
                // No data available; yield briefly to avoid tight spinning.
                Thread.sleep(1)
            }
        }
        return totalRead
    }

    companion object {
        private const val TAG = "StreamService"

        private const val ACTION_START = "com.floatoverlay.app.stream.START"
        private const val ACTION_STOP = "com.floatoverlay.app.stream.STOP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID = "float_stream_channel"
        private const val FOREGROUND_SERVICE_ID = 2

        private const val VIDEO_TRACK_ID = "screen_video"
        private const val AUDIO_TRACK_ID = "screen_audio"
        private const val DATA_CHANNEL_LABEL = "audio-pcm"

        private const val AUDIO_SAMPLE_RATE = 48000
        private const val AUDIO_CHANNELS = 2
        private const val AUDIO_BYTES_PER_SAMPLE = 2
        private const val MS_PER_CHUNK = 10
        private const val SAMPLES_PER_CHUNK = AUDIO_SAMPLE_RATE * MS_PER_CHUNK / 1000
        private const val BYTES_PER_CHUNK = SAMPLES_PER_CHUNK * AUDIO_CHANNELS * AUDIO_BYTES_PER_SAMPLE
        private const val TIMESTAMP_BYTES = 8

        const val EXTRA_VIDEO_WIDTH = "video_width"
        const val EXTRA_VIDEO_HEIGHT = "video_height"
        const val EXTRA_VIDEO_FPS = "video_fps"

        @Volatile
        private var factoryInitialized = false

        fun start(context: Context, resultCode: Int, data: Intent, width: Int, height: Int, fps: Int) {
            val intent = Intent(context, StreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
                putExtra(EXTRA_VIDEO_WIDTH, width)
                putExtra(EXTRA_VIDEO_HEIGHT, height)
                putExtra(EXTRA_VIDEO_FPS, fps)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamService::class.java).apply {
                action = ACTION_STOP
            }
            // Use startService, not startForegroundService, because we are stopping.
            // startForegroundService would require another startForeground() call.
            context.startService(intent)
        }
    }
}

package com.floatoverlay.app.stream

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Build
import android.view.Display
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.floatoverlay.app.LogStore
import com.floatoverlay.app.MainActivity
import com.floatoverlay.app.R
import com.floatoverlay.app.data.StreamRepository
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Foreground service that captures the device screen via MediaProjection and
 * feeds it into a WebRTC video track.
 *
 * The service keeps the capture/encoding pipeline alive while the user is in
 * another app (e.g. Project Zomboid via ZomDroid).
 */
class StreamService : Service() {

    private var capturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null

    private lateinit var streamRepository: StreamRepository

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
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (data != null && resultCode == Activity.RESULT_OK) {
                    startStreaming(resultCode, data)
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

            val factoryOptions = PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            }

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(factoryOptions)
                .createPeerConnectionFactory()

            surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglContext)
            LogStore.log(TAG, "WebRTC initialized")
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to initialize WebRTC", e)
        }
    }

    private fun startStreaming(resultCode: Int, data: Intent) {
        if (capturer != null) {
            LogStore.log(TAG, "Stream already active")
            return
        }

        if (peerConnectionFactory == null || surfaceTextureHelper == null) {
            LogStore.log(TAG, "WebRTC not initialized, cannot start stream")
            stopSelf()
            return
        }

        startForegroundWithNotification()

        val metrics = DisplayMetrics()
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        defaultDisplay.getRealMetrics(metrics)

        // Target 1280x720 landscape. Preserve aspect ratio and cap dimensions.
        val (width, height) = computeLandscapeSize(metrics.widthPixels, metrics.heightPixels, TARGET_WIDTH, TARGET_HEIGHT)

        try {
            val source = peerConnectionFactory!!.createVideoSource(true)
            videoSource = source

            capturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    LogStore.log(TAG, "MediaProjection stopped by system")
                    stopStreaming()
                }
            }).apply {
                initialize(surfaceTextureHelper, applicationContext, source.capturerObserver)
                startCapture(width, height, TARGET_FPS)
            }

            videoTrack = peerConnectionFactory?.createVideoTrack(VIDEO_TRACK_ID, source)

            streamRepository.setStreaming(true)
            LogStore.log(TAG, "Screen capture started ${width}x${height} @ ${TARGET_FPS}fps")
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to start screen capture", e)
            stopStreaming()
        }
    }

    private fun computeLandscapeSize(screenWidth: Int, screenHeight: Int, targetWidth: Int, targetHeight: Int): Pair<Int, Int> {
        val isLandscape = screenWidth >= screenHeight
        val (longer, shorter) = if (isLandscape) {
            screenWidth to screenHeight
        } else {
            screenHeight to screenWidth
        }

        // Scale to fit inside target box while preserving aspect ratio.
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
        LogStore.log(TAG, "Stopping stream")
        try {
            capturer?.stopCapture()
            capturer?.dispose()
            capturer = null

            videoTrack?.dispose()
            videoTrack = null

            videoSource?.dispose()
            videoSource = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            eglBase?.releaseSurface()
            eglBase?.release()
            eglBase = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        } catch (e: Exception) {
            LogStore.logError(TAG, "Error during stream teardown", e)
        }

        streamRepository.setStreaming(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StreamService"

        private const val ACTION_START = "com.floatoverlay.app.stream.START"
        private const val ACTION_STOP = "com.floatoverlay.app.stream.STOP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID = "float_stream_channel"
        private const val FOREGROUND_SERVICE_ID = 2

        private const val TARGET_WIDTH = 1280
        private const val TARGET_HEIGHT = 720
        private const val TARGET_FPS = 30

        private const val VIDEO_TRACK_ID = "screen_video"

        @Volatile
        private var factoryInitialized = false

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, StreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

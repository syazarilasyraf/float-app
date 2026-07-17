package com.floatoverlay.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView

import androidx.core.app.NotificationCompat
import com.floatoverlay.app.model.OverlayConfig

class FloatOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var iconView: View? = null
    private var webView: WebView? = null
    private var overlayContainer: FrameLayout? = null
    private var badgeCounter: TextView? = null
    private lateinit var repository: OverlayRepository
    private lateinit var counter: NotificationCounter

    private var overlayParams: WindowManager.LayoutParams? = null
    private var iconParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var currentConfig: OverlayConfig? = null
    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = OverlayRepository(this)
        counter = NotificationCounter(repository)
        startForeground()
        showIcon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_INCREMENT_BADGE -> {
                val categoryName = intent.getStringExtra(EXTRA_CATEGORY)
                val amount = intent.getIntExtra(EXTRA_AMOUNT, 1)
                val category = categoryName?.let {
                    try {
                        NotificationCounter.Category.valueOf(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: NotificationCounter.Category.CHAT
                incrementBadge(category, amount)
            }
            ACTION_CLEAR_BADGE -> {
                clearBadge()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeViews()
        super.onDestroy()
    }

    private fun startForeground() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_running))
            .setContentText(getString(R.string.overlay_running_desc))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(FOREGROUND_SERVICE_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showIcon() {
        windowManager = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            dpToPx(44),
            dpToPx(44),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16)
            y = dpToPx(100)
        }
        iconParams = params

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.floating_icon, null)
        badgeCounter = view.findViewById(R.id.badgeCounter)
        updateBadge()

        setupDrag(view, params)
        view.setOnClickListener {
            if (!isDragging) {
                expandOverlay()
            }
        }

        iconView = view
        windowManager?.addView(view, params)
    }

    private fun expandOverlay() {
        if (overlayView != null) {
            overlayView?.visibility = View.VISIBLE
            isExpanded = true
            iconView?.visibility = View.GONE
            clearBadge()
            return
        }

        currentConfig = repository.getEnabledOverlays().firstOrNull()
        val config = currentConfig
        val width = config?.widthDp ?: 240
        val height = config?.heightDp ?: 160

        val params = WindowManager.LayoutParams(
            dpToPx(width),
            dpToPx(height),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = iconParams?.x ?: dpToPx(16)
            y = (iconParams?.y ?: dpToPx(100)) + dpToPx(64)
        }
        overlayParams = params

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val container = inflater.inflate(R.layout.floating_overlay, null) as FrameLayout
        overlayContainer = container
        webView = container.findViewById(R.id.overlayWebView)
        val minimizeButton = container.findViewById<ImageButton>(R.id.minimizeButton)

        applyConfigToView(config)
        setupWebView(config)
        setupDrag(container, params)

        container.setOnClickListener {
            // Reserved for future interaction
        }

        minimizeButton.setOnClickListener {
            minimizeOverlay()
        }

        overlayView = container
        windowManager?.addView(container, params)
        isExpanded = true
        iconView?.visibility = View.GONE
        clearBadge()
    }

    private fun minimizeOverlay() {
        overlayView?.visibility = View.GONE
        isExpanded = false
        iconView?.visibility = View.VISIBLE
    }

    private fun applyConfigToView(config: OverlayConfig?) {
        val container = overlayContainer ?: return
        if (config != null) {
            container.background = OverlayBackgroundDrawable.fromConfig(config)
        }
    }

    private fun setupWebView(config: OverlayConfig?) {
        webView?.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(0x00000000)

            if (config != null && config.url.isNotBlank()) {
                loadUrl(config.url)
            } else {
                loadDataWithBaseURL(
                    null,
                    SAMPLE_OVERLAY_HTML,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                        isDragging = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeViews() {
        overlayView?.let { windowManager?.removeView(it) }
        iconView?.let { windowManager?.removeView(it) }
        overlayView = null
        iconView = null
        overlayContainer = null
        webView = null
        badgeCounter = null
        overlayParams = null
        iconParams = null
    }

    private fun incrementBadge(category: NotificationCounter.Category, amount: Int) {
        counter.increment(category, amount)
        updateBadge()
    }

    private fun clearBadge() {
        counter.clear()
        updateBadge()
    }

    private fun updateBadge() {
        badgeCounter?.apply {
            val total = counter.total()
            if (total > 0) {
                text = if (total > 99) "99+" else total.toString()
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val CHANNEL_ID = "float_overlay_channel"
        private const val FOREGROUND_SERVICE_ID = 1

        const val ACTION_INCREMENT_BADGE = "com.floatoverlay.app.INCREMENT_BADGE"
        const val ACTION_CLEAR_BADGE = "com.floatoverlay.app.CLEAR_BADGE"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_AMOUNT = "amount"

        fun incrementBadge(context: Context, category: NotificationCounter.Category, amount: Int = 1) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_INCREMENT_BADGE
                putExtra(EXTRA_CATEGORY, category.name)
                putExtra(EXTRA_AMOUNT, amount)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun clearBadge(context: Context) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_CLEAR_BADGE
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        private const val SAMPLE_OVERLAY_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        margin: 0;
                        padding: 0;
                        background: rgba(0,0,0,0.7);
                        color: white;
                        font-family: sans-serif;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        border-radius: 16px;
                    }
                    .title { font-size: 18px; margin-bottom: 8px; }
                    .timer { font-size: 36px; font-weight: bold; }
                </style>
            </head>
            <body>
                <div class="title">Sociabuzz Timer</div>
                <div class="timer">02:45:30</div>
            </body>
            </html>
        """
    }
}

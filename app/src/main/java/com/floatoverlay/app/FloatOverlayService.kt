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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.floatoverlay.app.model.OverlayConfig

class FloatOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var iconView: View? = null
    private var badgeCounter: TextView? = null
    private lateinit var repository: OverlayRepository
    private lateinit var counter: NotificationCounter

    private val overlayViews = mutableListOf<View>()
    private val overlayParamsList = mutableListOf<WindowManager.LayoutParams>()
    private var iconParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LogStore.log(TAG, "Service onCreate")
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
            ACTION_RELOAD_OVERLAYS -> {
                LogStore.log(TAG, "Reload overlays command received")
                reloadOverlays()
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
        LogStore.log(TAG, "Showing floating icon")
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
                toggleOverlays()
            }
        }

        iconView = view
        windowManager?.addView(view, params)
    }

    private fun toggleOverlays() {
        if (isExpanded) {
            hideOverlays()
        } else {
            showOverlays()
        }
    }

    private fun showOverlays() {
        LogStore.log(TAG, "showOverlays called")
        try {
            val configs = repository.getEnabledOverlays()
            LogStore.log(TAG, "Found ${configs.size} enabled overlays")

            if (configs.isEmpty()) {
                showToast("No enabled overlays. Add one in the app.")
                return
            }

            if (overlayViews.isNotEmpty()) {
                overlayViews.forEach { it.visibility = View.VISIBLE }
                isExpanded = true
                iconView?.visibility = View.GONE
                clearBadge()
                return
            }

            val baseX = iconParams?.x ?: dpToPx(16)
            val baseY = (iconParams?.y ?: dpToPx(100)) + dpToPx(56)

            configs.forEachIndexed { index, config ->
                val params = createOverlayParams(config, baseX + dpToPx(index * 16), baseY + dpToPx(index * 16))
                val container = createOverlayView(config, params)
                overlayViews.add(container)
                overlayParamsList.add(params)
                windowManager?.addView(container, params)
                LogStore.log(TAG, "Overlay #${index + 1} (${config.name}) added")
            }

            isExpanded = true
            iconView?.visibility = View.GONE
            clearBadge()
        } catch (e: Exception) {
            LogStore.logError(TAG, "showOverlays failed", e)
            Log.e(TAG, "showOverlays failed", e)
            showToast("Overlay error: ${e.message}")
        }
    }

    private fun hideOverlays() {
        LogStore.log(TAG, "Hiding overlays")
        overlayViews.forEach { it.visibility = View.GONE }
        isExpanded = false
        iconView?.visibility = View.VISIBLE
    }

    private fun reloadOverlays() {
        LogStore.log(TAG, "Reloading overlays")
        removeOverlayViewsOnly()
        if (isExpanded) {
            showOverlays()
        }
    }

    private fun createOverlayParams(config: OverlayConfig, x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            dpToPx(config.widthDp.coerceIn(50, 1000)),
            dpToPx(config.heightDp.coerceIn(50, 1000)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun createOverlayView(config: OverlayConfig, params: WindowManager.LayoutParams): FrameLayout {
        val container = FrameLayout(this)
        container.background = OverlayBackgroundDrawable.fromConfig(config)

        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = dpToPx(24)
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.setBackgroundColor(0x00000000)

        if (config.url.isNotBlank()) {
            LogStore.log(TAG, "Loading URL for ${config.name}: ${config.url}")
            webView.loadUrl(config.url)
        } else {
            LogStore.log(TAG, "Loading sample HTML for ${config.name}")
            webView.loadDataWithBaseURL(null, SAMPLE_OVERLAY_HTML, "text/html", "UTF-8", null)
        }
        container.addView(webView)

        val minimizeButton = TextView(this).apply {
            text = "−"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            setOnClickListener {
                hideOverlays()
            }
        }
        container.addView(minimizeButton)

        val resizeHandle = View(this).apply {
            background = getDrawable(R.drawable.resize_handle)
            layoutParams = FrameLayout.LayoutParams(dpToPx(20), dpToPx(20)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        }
        setupResize(resizeHandle, params, config)
        container.addView(resizeHandle)

        setupDrag(container, params)
        return container
    }

    private fun setupResize(handle: View, params: WindowManager.LayoutParams, config: OverlayConfig) {
        var startWidth = 0
        var startHeight = 0
        var startX = 0f
        var startY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startWidth = params.width
                    startHeight = params.height
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt()
                    val dy = (event.rawY - startY).toInt()
                    val newWidth = (startWidth + dx).coerceAtLeast(dpToPx(100))
                    val newHeight = (startHeight + dy).coerceAtLeast(dpToPx(60))
                    params.width = newWidth
                    params.height = newHeight
                    windowManager?.updateViewLayout(handle.parent as View, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val newWidthDp = pxToDp(params.width).coerceIn(50, 1000)
                    val newHeightDp = pxToDp(params.height).coerceIn(50, 1000)
                    repository.addOrUpdate(config.copy(widthDp = newWidthDp, heightDp = newHeightDp))
                    LogStore.log(TAG, "Resized ${config.name} to ${newWidthDp}x${newHeightDp} dp")
                    true
                }
                else -> false
            }
        }
    }

    private fun pxToDp(px: Int): Int {
        return (px / resources.displayMetrics.density).toInt()
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

    private fun removeOverlayViewsOnly() {
        overlayViews.forEach {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                LogStore.logError(TAG, "removeOverlayViewsOnly", e)
            }
        }
        overlayViews.clear()
        overlayParamsList.clear()
    }

    private fun removeViews() {
        removeOverlayViewsOnly()
        iconView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                LogStore.logError(TAG, "removeViews icon", e)
            }
        }
        iconView = null
        badgeCounter = null
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

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "FloatOverlayService"
        private const val CHANNEL_ID = "float_overlay_channel"
        private const val FOREGROUND_SERVICE_ID = 1

        const val ACTION_INCREMENT_BADGE = "com.floatoverlay.app.INCREMENT_BADGE"
        const val ACTION_CLEAR_BADGE = "com.floatoverlay.app.CLEAR_BADGE"
        const val ACTION_RELOAD_OVERLAYS = "com.floatoverlay.app.RELOAD_OVERLAYS"
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

        fun reloadOverlays(context: Context) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_RELOAD_OVERLAYS
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

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
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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

    private val overlayViews = mutableMapOf<String, FrameLayout>()
    private val overlayParams = mutableMapOf<String, WindowManager.LayoutParams>()
    private val lastConfigs = mutableMapOf<String, OverlayConfig>()
    private var iconParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var isExpanded = false

    private val zoomHandler = Handler(Looper.getMainLooper())
    private val pendingZoomRunnables = mutableMapOf<String, MutableList<Runnable>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LogStore.log(TAG, "Service onCreate")
        repository = OverlayRepository(this)
        counter = NotificationCounter(repository)
        startForeground()
        showIcon()
        if (repository.isAutoShowEnabled() && repository.getEnabledOverlays().isNotEmpty()) {
            LogStore.log(TAG, "Auto-showing overlays on start")
            showOverlays()
        }
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
                val id = intent.getStringExtra(EXTRA_OVERLAY_ID)
                LogStore.log(TAG, "Reload overlays command received, id=$id")
                reloadOverlays(id)
            }
            ACTION_REFRESH_OVERLAY -> {
                val id = intent.getStringExtra(EXTRA_OVERLAY_ID)
                LogStore.log(TAG, "Refresh overlay command received, id=$id")
                refreshOverlay(id)
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
            dpToPx(36),
            dpToPx(36),
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

        setupIconDrag(view, params)
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

            val screenSize = getScreenSize()

            configs.forEachIndexed { index, config ->
                if (!overlayViews.containsKey(config.id)) {
                    addOverlay(config, screenSize, index)
                } else {
                    overlayViews[config.id]?.visibility = View.VISIBLE
                    LogStore.log(TAG, "Overlay (${config.name}) already exists, showing")
                }
            }

            isExpanded = true
            bringIconToFront()
            clearBadge()
        } catch (e: Exception) {
            LogStore.logError(TAG, "showOverlays failed", e)
            Log.e(TAG, "showOverlays failed", e)
            showToast("Overlay error: ${e.message}")
        }
    }

    private fun hideOverlays() {
        LogStore.log(TAG, "Hiding overlays")
        overlayViews.values.forEach { it.visibility = View.GONE }
        isExpanded = false
    }

    private fun bringIconToFront() {
        val view = iconView ?: return
        val params = iconParams ?: return
        try {
            windowManager?.removeView(view)
            windowManager?.addView(view, params)
            LogStore.log(TAG, "Floating icon brought to front")
        } catch (e: Exception) {
            LogStore.logError(TAG, "bringIconToFront failed", e)
        }
    }

    private fun reloadOverlays(changedId: String?) {
        LogStore.log(TAG, "Reloading overlays, changedId=$changedId")
        try {
            val configs = repository.getEnabledOverlays().associateBy { it.id }

            // Remove overlays that are no longer enabled or deleted
            val idsToRemove = overlayViews.keys.filter { configs[it] == null }
            idsToRemove.forEach { id ->
                removeOverlayView(id)
                lastConfigs.remove(id)
            }

            if (!isExpanded) return

            if (changedId == null) {
                // Adding a new overlay or generic refresh: let showOverlays add any missing ones
                showOverlays()
                return
            }

            val newConfig = configs[changedId] ?: return
            val container = overlayViews[changedId]
            if (container == null) {
                // Newly enabled overlay while already expanded
                val screenSize = getScreenSize()
                addOverlay(newConfig, screenSize)
                bringIconToFront()
                return
            }

            val oldConfig = lastConfigs[changedId]
            if (oldConfig == null || oldConfig.url != newConfig.url) {
                LogStore.log(TAG, "URL changed for ${newConfig.name}, recreating overlay")
                removeOverlayView(changedId)
                lastConfigs.remove(changedId)
                val screenSize = getScreenSize()
                addOverlay(newConfig, screenSize)
            } else {
                LogStore.log(TAG, "Applying smart update to ${newConfig.name}")
                applyOverlayChanges(container, overlayParams[changedId]!!, oldConfig, newConfig)
                lastConfigs[changedId] = newConfig
            }
        } catch (e: Exception) {
            LogStore.logError(TAG, "reloadOverlays failed", e)
        }
    }

    private fun refreshOverlay(id: String?) {
        if (id == null) return
        val container = overlayViews[id] ?: return
        val webView = container.findViewWithTag<WebView>("overlayWebView") ?: return
        webView.reload()
        LogStore.log(TAG, "Reloaded overlay $id")
    }

    private fun removeOverlayView(id: String) {
        cancelVisualZoomRetries(id)
        overlayViews[id]?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                LogStore.logError(TAG, "removeOverlayView $id", e)
            }
        }
        overlayViews.remove(id)
        overlayParams.remove(id)
        lastConfigs.remove(id)
    }

    private fun createOverlayParams(config: OverlayConfig, x: Int, y: Int): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (config.touchThrough) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return WindowManager.LayoutParams(
            dpToPx(config.widthDp.coerceIn(50, 1000)),
            dpToPx(config.heightDp.coerceIn(50, 1000)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun createOverlayView(config: OverlayConfig, params: WindowManager.LayoutParams): FrameLayout {
        val container = FrameLayout(this)
        container.background = OverlayBackgroundDrawable.fromConfig(config, resources.displayMetrics.density)
        container.alpha = config.opacityPercent / 100f

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
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val currentConfig = repository.getOverlay(config.id) ?: config
                view?.let {
                    applyZoom(it, currentConfig)
                    applyOffset(it, currentConfig.contentOffsetX, currentConfig.contentOffsetY)
                    scheduleVisualZoomRetries(currentConfig, it)
                    LogStore.log(TAG, "Page finished for ${currentConfig.name}, applied zoom/offset")
                }
            }
        }
        webView.setBackgroundColor(0x00000000)

        if (config.url.isNotBlank()) {
            LogStore.log(TAG, "Loading URL for ${config.name}: ${config.url}")
            webView.loadUrl(config.url)
        } else {
            LogStore.log(TAG, "Loading sample HTML for ${config.name}")
            webView.loadDataWithBaseURL(null, SAMPLE_OVERLAY_HTML, "text/html", "UTF-8", null)
        }
        webView.tag = "overlayWebView"
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

        val isInteractive = !config.touchThrough
        val showHandle = config.showResizeHandle && isInteractive

        val resizeHandle = View(this).apply {
            tag = "resizeHandle"
            background = getDrawable(R.drawable.resize_handle)
            layoutParams = FrameLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
            visibility = if (showHandle) View.VISIBLE else View.GONE
        }
        if (isInteractive) {
            setupResize(resizeHandle, params, config)
        }
        container.addView(resizeHandle)

        if (isInteractive) {
            setupDrag(container, params, config)
        }
        return container
    }

    private fun applyZoom(webView: WebView, config: OverlayConfig) {
        val scale = config.scalePercent.coerceIn(25, 300) / 100f
        val script = if (config.zoomMode == "visual") {
            "document.body.style.zoom='';document.body.style.transformOrigin='0 0';document.body.style.transform='scale($scale)';"
        } else {
            "document.body.style.transform='';document.body.style.transformOrigin='';document.body.style.zoom='${config.scalePercent.coerceIn(25, 300)}%';"
        }
        webView.evaluateJavascript(script) { result ->
            LogStore.log(TAG, "Zoom applied mode=${config.zoomMode}, scale=${config.scalePercent}%, result=$result")
        }
    }

    private fun scheduleVisualZoomRetries(config: OverlayConfig, webView: WebView) {
        if (config.zoomMode != "visual") return
        cancelVisualZoomRetries(config.id)
        val list = mutableListOf<Runnable>()
        pendingZoomRunnables[config.id] = list
        val delays = listOf(0L, 1000L, 2000L, 4000L, 8000L)
        delays.forEach { delay ->
            val runnable = Runnable {
                val latest = repository.getOverlay(config.id) ?: config
                if (latest.zoomMode == "visual") {
                    applyZoom(webView, latest)
                }
            }
            list.add(runnable)
            zoomHandler.postDelayed(runnable, delay)
        }
    }

    private fun cancelVisualZoomRetries(id: String) {
        pendingZoomRunnables[id]?.forEach { zoomHandler.removeCallbacks(it) }
        pendingZoomRunnables.remove(id)
    }

    private fun applyOffset(webView: WebView, x: Int, y: Int) {
        webView.postDelayed({
            webView.scrollTo(x.coerceAtLeast(0), y.coerceAtLeast(0))
            LogStore.log(TAG, "Offset applied: $x,$y")
        }, 300)
    }

    private fun addOverlay(config: OverlayConfig, screenSize: Pair<Int, Int>, index: Int = overlayViews.size): FrameLayout {
        val x = percentToX(config.posXPercent, index, screenSize.first)
        val y = percentToY(config.posYPercent, index, screenSize.second)
        val params = createOverlayParams(config, x, y)
        val container = createOverlayView(config, params)
        overlayViews[config.id] = container
        overlayParams[config.id] = params
        windowManager?.addView(container, params)
        if (config.posXPercent < 0f || config.posYPercent < 0f) {
            val xPercent = params.x.toFloat() / screenSize.first
            val yPercent = params.y.toFloat() / screenSize.second
            repository.addOrUpdate(config.copy(posXPercent = xPercent, posYPercent = yPercent))
            LogStore.log(TAG, "Saved initial position for ${config.name}: $xPercent,$yPercent")
        }
        lastConfigs[config.id] = config
        LogStore.log(TAG, "Overlay (${config.name}) added at $x,$y (${config.posXPercent},${config.posYPercent})")
        return container
    }

    private fun applyOverlayChanges(
        container: FrameLayout,
        params: WindowManager.LayoutParams,
        oldConfig: OverlayConfig,
        newConfig: OverlayConfig
    ) {
        val screenSize = getScreenSize()

        if (oldConfig.widthDp != newConfig.widthDp || oldConfig.heightDp != newConfig.heightDp) {
            params.width = dpToPx(newConfig.widthDp.coerceIn(50, 1000))
            params.height = dpToPx(newConfig.heightDp.coerceIn(50, 1000))
            windowManager?.updateViewLayout(container, params)
            LogStore.log(TAG, "Updated size for ${newConfig.name}: ${newConfig.widthDp}x${newConfig.heightDp} dp")
        }

        if (oldConfig.posXPercent != newConfig.posXPercent || oldConfig.posYPercent != newConfig.posYPercent) {
            params.x = percentToX(newConfig.posXPercent, overlayViews.size, screenSize.first)
            params.y = percentToY(newConfig.posYPercent, overlayViews.size, screenSize.second)
            windowManager?.updateViewLayout(container, params)
            LogStore.log(TAG, "Updated position for ${newConfig.name}: ${params.x},${params.y}")
        }

        if (oldConfig.opacityPercent != newConfig.opacityPercent) {
            container.alpha = newConfig.opacityPercent / 100f
            LogStore.log(TAG, "Updated opacity for ${newConfig.name}: ${newConfig.opacityPercent}%")
        }

        if (oldConfig.backgroundColor != newConfig.backgroundColor ||
            oldConfig.cornerRadiusDp != newConfig.cornerRadiusDp ||
            oldConfig.transparentBackground != newConfig.transparentBackground
        ) {
            container.background = OverlayBackgroundDrawable.fromConfig(newConfig, resources.displayMetrics.density)
            LogStore.log(TAG, "Updated background for ${newConfig.name}")
        }

        if (oldConfig.touchThrough != newConfig.touchThrough ||
            oldConfig.showResizeHandle != newConfig.showResizeHandle
        ) {
            updateInteractivity(container, params, newConfig)
            LogStore.log(TAG, "Updated interactivity for ${newConfig.name}")
        }

        if (oldConfig.scalePercent != newConfig.scalePercent ||
            oldConfig.zoomMode != newConfig.zoomMode ||
            oldConfig.contentOffsetX != newConfig.contentOffsetX ||
            oldConfig.contentOffsetY != newConfig.contentOffsetY
        ) {
            val webView = container.findViewWithTag<WebView>("overlayWebView")
            webView?.let {
                applyZoom(it, newConfig)
                applyOffset(it, newConfig.contentOffsetX, newConfig.contentOffsetY)
            }
            LogStore.log(TAG, "Updated zoom/offset for ${newConfig.name}")
        }
    }

    private fun updateInteractivity(container: FrameLayout, params: WindowManager.LayoutParams, config: OverlayConfig) {
        val isInteractive = !config.touchThrough

        params.flags = if (config.touchThrough) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        windowManager?.updateViewLayout(container, params)

        val resizeHandle = container.findViewWithTag<View>("resizeHandle")
        resizeHandle?.visibility = if (config.showResizeHandle && isInteractive) View.VISIBLE else View.GONE

        container.setOnTouchListener(if (isInteractive) setupDragListener(container, params, config) else null)
        resizeHandle?.setOnTouchListener(if (isInteractive) setupResizeListener(resizeHandle, params, config) else null)
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams, config: OverlayConfig): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
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
                    if (isDragging) {
                        val screenSize = getScreenSize()
                        val xPercent = params.x.toFloat() / screenSize.first
                        val yPercent = params.y.toFloat() / screenSize.second
                        val latest = repository.getOverlay(config.id) ?: config
                        repository.addOrUpdate(latest.copy(posXPercent = xPercent, posYPercent = yPercent))
                        LogStore.log(TAG, "Saved position for ${config.name}: ${xPercent},${yPercent}")
                    } else {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupResizeListener(handle: View, params: WindowManager.LayoutParams, config: OverlayConfig): View.OnTouchListener {
        var startWidth = 0
        var startHeight = 0
        var startX = 0f
        var startY = 0f

        return View.OnTouchListener { _, event ->
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
                    val latest = repository.getOverlay(config.id) ?: config
                    repository.addOrUpdate(latest.copy(widthDp = newWidthDp, heightDp = newHeightDp))
                    LogStore.log(TAG, "Resized ${config.name} to ${newWidthDp}x${newHeightDp} dp")
                    true
                }
                else -> false
            }
        }
    }

    private fun setupResize(handle: View, params: WindowManager.LayoutParams, config: OverlayConfig) {
        handle.setOnTouchListener(setupResizeListener(handle, params, config))
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams, config: OverlayConfig) {
        view.setOnTouchListener(setupDragListener(view, params, config))
    }

    private fun setupIconDrag(view: View, params: WindowManager.LayoutParams) {
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
        overlayViews.keys.toList().forEach { removeOverlayView(it) }
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

    private fun getScreenSize(): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    private fun percentToX(percent: Float, index: Int, screenWidth: Int): Int {
        return if (percent >= 0f) (percent * screenWidth).toInt() else dpToPx(16 + index * 16)
    }

    private fun percentToY(percent: Float, index: Int, screenHeight: Int): Int {
        return if (percent >= 0f) (percent * screenHeight).toInt() else dpToPx(100 + index * 16)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun pxToDp(px: Int): Int {
        return (px / resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "FloatOverlayService"
        private const val CHANNEL_ID = "float_overlay_channel"
        private const val FOREGROUND_SERVICE_ID = 1

        const val ACTION_INCREMENT_BADGE = "com.floatoverlay.app.INCREMENT_BADGE"
        const val ACTION_CLEAR_BADGE = "com.floatoverlay.app.CLEAR_BADGE"
        const val ACTION_RELOAD_OVERLAYS = "com.floatoverlay.app.RELOAD_OVERLAYS"
        const val ACTION_REFRESH_OVERLAY = "com.floatoverlay.app.REFRESH_OVERLAY"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_OVERLAY_ID = "overlay_id"

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

        fun reloadOverlays(context: Context, overlayId: String? = null) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_RELOAD_OVERLAYS
                putExtra(EXTRA_OVERLAY_ID, overlayId)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun refreshOverlay(context: Context, overlayId: String) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_REFRESH_OVERLAY
                putExtra(EXTRA_OVERLAY_ID, overlayId)
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

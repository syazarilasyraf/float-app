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
    private var iconParams: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var isExpanded = false

    private val donationQueue = ArrayDeque<Pair<String, Double>>()
    private val donationHandler = Handler(Looper.getMainLooper())

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
                val id = intent.getStringExtra(EXTRA_OVERLAY_ID)
                LogStore.log(TAG, "Reload overlays command received, id=$id")
                reloadOverlays(id)
            }
            ACTION_DONATION -> {
                val name = intent.getStringExtra(EXTRA_DONATION_NAME) ?: "Anonymous"
                val amount = intent.getDoubleExtra(EXTRA_DONATION_AMOUNT, 0.0)
                LogStore.log(TAG, "Donation received: $name RM$amount")
                handleDonation(name, amount)
            }
            ACTION_APPLY_HUD_SETTINGS -> {
                val goal = intent.getIntExtra(EXTRA_HUD_GOAL, 50)
                val mpr = intent.getIntExtra(EXTRA_HUD_MPR, 5)
                val cap = intent.getIntExtra(EXTRA_HUD_CAP, 3)
                applyHudSettings(goal, mpr, cap)
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
                    LogStore.log(TAG, "Overlay (${config.name}) added at $x,$y (${config.posXPercent},${config.posYPercent})")
                } else {
                    overlayViews[config.id]?.visibility = View.VISIBLE
                    LogStore.log(TAG, "Overlay (${config.name}) already exists, showing")
                }
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
        overlayViews.values.forEach { it.visibility = View.GONE }
        isExpanded = false
        iconView?.visibility = View.VISIBLE
    }

    private fun reloadOverlays(changedId: String?) {
        LogStore.log(TAG, "Reloading overlays, changedId=$changedId")
        try {
            val configs = repository.getEnabledOverlays().associateBy { it.id }

            // Remove overlays that are no longer enabled or were explicitly changed
            val idsToRemove = overlayViews.keys.filter { id ->
                val config = configs[id]
                when {
                    config == null -> true // disabled/deleted
                    id == changedId -> true // this one changed, recreate it
                    else -> false // never touch other overlays
                }
            }

            idsToRemove.forEach { id ->
                removeOverlayView(id)
            }

            // Add or recreate needed overlays
            if (isExpanded) {
                showOverlays()
            }
        } catch (e: Exception) {
            LogStore.logError(TAG, "reloadOverlays failed", e)
        }
    }

    private fun removeOverlayView(id: String) {
        overlayViews[id]?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                LogStore.logError(TAG, "removeOverlayView $id", e)
            }
        }
        overlayViews.remove(id)
        overlayParams.remove(id)
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
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val hud = container.findViewWithTag<WebView>("hudWebView")
                if (hud == view) {
                    val settings = repository.loadHudSettings()
                    applyHudSettings(settings.goalRM, settings.minutesPerRM, settings.capHours)
                }
                if (config.donationSource) {
                    view?.evaluateJavascript(DONATION_DETECTION_JS, null)
                    LogStore.log(TAG, "Injected donation detector into ${config.name}")
                }
            }
        }
        if (config.donationSource) {
            webView.addJavascriptInterface(DonationBridge { name, amount ->
                handleDonation(name, amount)
            }, "FloatOverlayBridge")
        }
        webView.setBackgroundColor(0x00000000)

        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        if (config.overlayType == OverlayConfig.TYPE_LOCAL && config.assetName == "stream_hud.html") {
            webView.tag = "hudWebView"
        }

        when {
            config.overlayType == OverlayConfig.TYPE_LOCAL && config.assetName.isNotBlank() -> {
                val path = "file:///android_asset/overlays/${config.assetName}"
                LogStore.log(TAG, "Loading local asset for ${config.name}: $path")
                webView.loadUrl(path)
            }
            config.url.isNotBlank() -> {
                LogStore.log(TAG, "Loading URL for ${config.name}: ${config.url}")
                webView.loadUrl(config.url)
            }
            else -> {
                LogStore.log(TAG, "Loading sample HTML for ${config.name}")
                webView.loadDataWithBaseURL(null, SAMPLE_OVERLAY_HTML, "text/html", "UTF-8", null)
            }
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

        val isInteractive = !config.locked && !config.touchThrough
        val showHandle = config.showResizeHandle && isInteractive

        val resizeHandle = View(this).apply {
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
                    val latest = repository.getOverlay(config.id) ?: config
                    repository.addOrUpdate(latest.copy(widthDp = newWidthDp, heightDp = newHeightDp))
                    LogStore.log(TAG, "Resized ${config.name} to ${newWidthDp}x${newHeightDp} dp")
                    true
                }
                else -> false
            }
        }
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams, config: OverlayConfig) {
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

    fun applyHudSettings(goalRM: Int, minutesPerRM: Int, capHours: Int) {
        LogStore.log(TAG, "Applying HUD settings: goal=$goalRM, mpr=$minutesPerRM, cap=$capHours")
        val js = "setGoal($goalRM); setMinutesPerRM($minutesPerRM); setCapHours($capHours);"
        overlayViews.values.forEach { container ->
            val webView = container.findViewWithTag<WebView>("hudWebView")
            webView?.evaluateJavascript(js) { result ->
                LogStore.log(TAG, "HUD settings result: $result")
            }
        }
    }

    private fun handleDonation(name: String, amount: Double) {
        LogStore.log(TAG, "Queuing donation $name RM$amount")
        donationQueue.addLast(Pair(name, amount))
        processDonationQueue()
    }

    private fun processDonationQueue() {
        if (donationQueue.isEmpty()) return
        val (name, amount) = donationQueue.removeFirst()
        LogStore.log(TAG, "Processing donation $name RM$amount")

        val escapedName = name.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'")
        val js = "onDonation(\"$escapedName\", $amount)"

        overlayViews.values.forEach { container ->
            val webView = container.findViewWithTag<WebView>("hudWebView")
            webView?.evaluateJavascript(js) { result ->
                LogStore.log(TAG, "HUD donation result: $result")
            }
        }

        incrementBadge(NotificationCounter.Category.DONATION, 1)

        if (donationQueue.isNotEmpty()) {
            donationHandler.postDelayed({ processDonationQueue() }, 500)
        }
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
        const val ACTION_DONATION = "com.floatoverlay.app.DONATION"
        const val ACTION_APPLY_HUD_SETTINGS = "com.floatoverlay.app.APPLY_HUD_SETTINGS"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_OVERLAY_ID = "overlay_id"
        const val EXTRA_DONATION_NAME = "donation_name"
        const val EXTRA_DONATION_AMOUNT = "donation_amount"
        const val EXTRA_HUD_GOAL = "hud_goal"
        const val EXTRA_HUD_MPR = "hud_mpr"
        const val EXTRA_HUD_CAP = "hud_cap"

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

        fun applyHudSettings(context: Context, goalRM: Int, minutesPerRM: Int, capHours: Int) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_APPLY_HUD_SETTINGS
                putExtra(EXTRA_HUD_GOAL, goalRM)
                putExtra(EXTRA_HUD_MPR, minutesPerRM)
                putExtra(EXTRA_HUD_CAP, capHours)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun triggerDonation(context: Context, name: String, amount: Double) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_DONATION
                putExtra(EXTRA_DONATION_NAME, name)
                putExtra(EXTRA_DONATION_AMOUNT, amount)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        private const val DONATION_DETECTION_JS = """
            (function() {
                if (window.__floatOverlayObserver) return;
                const observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1) {
                                const text = node.innerText || node.textContent || "";
                                const match = text.match(/RM\\s?([\\d.,]+)/);
                                if (match) {
                                    const amount = parseFloat(match[1].replace(/,/g, ""));
                                    let name = "Anonymous";
                                    const nameMatch = text.match(/from\\s+([\\w\\s@_.-]+)/i) || text.match(/([\\w\\s@_.-]+)\\s+donated/i);
                                    if (nameMatch) name = nameMatch[1].trim();
                                    if (window.FloatOverlayBridge && amount > 0) {
                                        window.FloatOverlayBridge.onDonation(name, amount);
                                    }
                                }
                            }
                        });
                    });
                });
                observer.observe(document.body, { childList: true, subtree: true });
                window.__floatOverlayObserver = true;
            })();
        """

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

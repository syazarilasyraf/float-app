package com.floatoverlay.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.floatoverlay.app.ai.AIProviderFactory
import com.floatoverlay.app.ai.asText
import com.floatoverlay.app.data.ConversationRepository
import com.floatoverlay.app.data.ProjectRepository
import com.floatoverlay.app.model.OverlayConfig
import com.floatoverlay.app.ui.ai.ChatAdapter
import kotlin.math.min

class FloatOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var iconView: View? = null
    private var badgeCounter: TextView? = null
    private lateinit var repository: OverlayRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var counter: NotificationCounter

    private val overlayViews = mutableMapOf<String, FrameLayout>()
    private val overlayParams = mutableMapOf<String, WindowManager.LayoutParams>()
    private val lastConfigs = mutableMapOf<String, OverlayConfig>()
    private var iconParams: WindowManager.LayoutParams? = null

    private val aiAdapters = mutableMapOf<String, ChatAdapter>()
    private val minecraftProjectIds = mutableMapOf<String, String>()
    private val minecraftImageScales = mutableMapOf<String, Float>()

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraUseCases = mutableMapOf<String, Preview>()
    private val serviceLifecycleOwner = ServiceLifecycleOwner()
    private var skipCameraOverlays = false

    private var displayManager: DisplayManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var currentDisplayRotation: Int = Surface.ROTATION_0

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
        profileRepository = ProfileRepository(this)
        counter = NotificationCounter(repository)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        currentDisplayRotation = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
        serviceLifecycleOwner.handleEvent(Lifecycle.Event.ON_CREATE)
        serviceLifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
        serviceLifecycleOwner.handleEvent(Lifecycle.Event.ON_RESUME)
        startForeground()
        showIcon()
        if (repository.isAutoShowEnabled() && repository.getEnabledOverlays().isNotEmpty()) {
            LogStore.log(TAG, "Auto-showing overlays on start")
            showOverlays()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        skipCameraOverlays = intent?.getBooleanExtra(EXTRA_SKIP_CAMERA, false) ?: false
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
            ACTION_REORDER_OVERLAYS -> {
                LogStore.log(TAG, "Reorder overlays command received")
                reorderOverlays()
            }
            ACTION_OPEN_FLOATING_AI -> {
                LogStore.log(TAG, "Open floating AI command received")
                openFloatingAI()
            }
            ACTION_TOGGLE_FLOATING_AI -> {
                LogStore.log(TAG, "Toggle floating AI command received")
                toggleFloatingAI()
            }
            ACTION_OPEN_FLOATING_MINECRAFT -> {
                val projectId = intent.getStringExtra(EXTRA_PROJECT_ID) ?: ""
                LogStore.log(TAG, "Open floating Minecraft command received, project=$projectId")
                if (projectId.isNotBlank()) openFloatingMinecraft(projectId)
            }
            ACTION_REFRESH_MINECRAFT_OVERLAYS -> {
                refreshMinecraftOverlays()
            }
            ACTION_APPLY_PROFILE -> {
                val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
                val isManual = intent?.getBooleanExtra(EXTRA_PROFILE_MANUAL, true) ?: true
                LogStore.log(TAG, "Apply profile command received, id=$profileId manual=$isManual")
                if (profileId != null) {
                    applyProfile(profileId, isManual)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeViews()
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            cameraUseCases.clear()
        } catch (e: Exception) {
            LogStore.logError(TAG, "Camera unbind on destroy failed", e)
        }
        displayListener?.let { displayManager?.unregisterDisplayListener(it) }
        displayListener = null
        serviceLifecycleOwner.handleEvent(Lifecycle.Event.ON_PAUSE)
        serviceLifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
        serviceLifecycleOwner.handleEvent(Lifecycle.Event.ON_DESTROY)
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
            dpToPx(20),
            dpToPx(20),
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
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            configs.forEachIndexed { index, config ->
                if (isCameraUrl(config.url) && (skipCameraOverlays || !hasCameraPermission)) {
                    LogStore.log(TAG, "Skipping camera overlay (${config.name}) due to missing permission")
                    showToast("Camera permission denied; skipping camera overlay")
                    return@forEachIndexed
                }
                if (!overlayViews.containsKey(config.id)) {
                    addOverlay(config, screenSize, index)
                } else {
                    overlayViews[config.id]?.visibility = View.VISIBLE
                    LogStore.log(TAG, "Overlay (${config.name}) already exists, showing")
                }
            }

            reorderOverlays()
            isExpanded = true
            bringIconToFront()
            updateOrientationListener()
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
        updateOrientationListener()
    }

    private fun openFloatingAI() {
        try {
            if (overlayViews.containsKey(AI_OVERLAY_ID)) {
                overlayViews[AI_OVERLAY_ID]?.visibility = View.VISIBLE
                isExpanded = true
                bringIconToFront()
                return
            }

            val config = repository.getOverlay(AI_OVERLAY_ID) ?: OverlayConfig(
                id = AI_OVERLAY_ID,
                name = "Float AI",
                url = "float://ai",
                type = OverlayConfig.Type.AI_CHAT,
                widthDp = 320,
                heightDp = 420,
                opacityPercent = 95,
                touchThrough = false,
                posXPercent = 0.05f,
                posYPercent = 0.15f
            )
            repository.addOrUpdate(config)
            val screenSize = getScreenSize()
            addOverlay(config, screenSize)
            isExpanded = true
            bringIconToFront()
        } catch (e: Exception) {
            LogStore.logError(TAG, "openFloatingAI failed", e)
            showToast("Float AI error: ${e.message}")
        }
    }

    private fun toggleFloatingAI() {
        val view = overlayViews[AI_OVERLAY_ID]
        if (view != null && view.visibility == View.VISIBLE) {
            view.visibility = View.GONE
            isExpanded = overlayViews.values.any { it.visibility == View.VISIBLE }
            LogStore.log(TAG, "Floating AI hidden via toggle")
        } else {
            openFloatingAI()
        }
    }

    private fun openFloatingMinecraft(projectId: String) {
        val overlayId = "$MINECRAFT_OVERLAY_PREFIX$projectId"
        if (overlayViews.containsKey(overlayId)) {
            overlayViews[overlayId]?.visibility = View.VISIBLE
            isExpanded = true
            bringIconToFront()
            refreshMinecraftOverlays()
            return
        }

        val project = ProjectRepository(this).getProject(projectId)
        val config = repository.getOverlay(overlayId) ?: OverlayConfig(
            id = overlayId,
            name = project?.name ?: "Minecraft Build",
            url = "float://minecraft/$projectId",
            type = OverlayConfig.Type.MINECRAFT_PROJECT,
            widthDp = 300,
            heightDp = 380,
            opacityPercent = 95,
            touchThrough = false,
            posXPercent = 0.05f,
            posYPercent = 0.15f
        )
        repository.addOrUpdate(config)
        val screenSize = getScreenSize()
        addOverlay(config, screenSize)
        isExpanded = true
        bringIconToFront()
    }

    private fun refreshMinecraftOverlays() {
        minecraftProjectIds.entries.forEach { (id, projectId) ->
            overlayViews[id]?.let {
                refreshMinecraftView(it.findViewWithTag("overlayMinecraftView"), projectId)
            }
        }
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

    private fun reorderOverlays() {
        if (overlayViews.isEmpty()) return
        val sorted = overlayViews.toList().sortedBy { (id, _) ->
            lastConfigs[id]?.zIndex ?: repository.getOverlay(id)?.zIndex ?: 0
        }
        sorted.forEach { (id, container) ->
            val params = overlayParams[id] ?: return@forEach
            try {
                windowManager?.removeView(container)
                windowManager?.addView(container, params)
                LogStore.log(TAG, "Reordered overlay $id (z=${lastConfigs[id]?.zIndex ?: repository.getOverlay(id)?.zIndex ?: 0})")
            } catch (e: Exception) {
                LogStore.logError(TAG, "reorderOverlays failed for $id", e)
            }
        }
        bringIconToFront()
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
                updateOrientationListener()
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
            updateOrientationListener()
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
        cameraUseCases[id]?.let { useCase ->
            try {
                cameraProvider?.unbind(useCase)
            } catch (e: Exception) {
                LogStore.logError(TAG, "Camera unbind for $id failed", e)
            }
            cameraUseCases.remove(id)
        }
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
        var flags = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        // AI chat needs focus so the EditText can show the soft keyboard.
        // Other overlays stay non-focusable by default.
        if (config.resolvedType() != OverlayConfig.Type.AI_CHAT) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            // Let touches outside the chat bubble pass through to the app underneath.
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
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
            if (config.resolvedType() == OverlayConfig.Type.AI_CHAT) {
                // Let the overlay resize/pan correctly when the soft keyboard appears.
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
        }
    }

    private fun createOverlayView(config: OverlayConfig, params: WindowManager.LayoutParams): FrameLayout {
        val container = FrameLayout(this)
        container.background = OverlayBackgroundDrawable.fromConfig(config, resources.displayMetrics.density)
        container.alpha = config.opacityPercent / 100f

        if (isCameraUrl(config.url)) {
            val previewView = PreviewView(this).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                tag = "overlayCameraView"
                isClickable = false
                isFocusable = false
                isLongClickable = false
            }
            container.addView(previewView)
            bindCamera(config, previewView)
            applyCameraShape(container, config)
            applyCameraFilter(container, config)
            applyCameraOpacity(container, config)
        } else when (config.resolvedType()) {
            OverlayConfig.Type.AI_CHAT -> addAiChatContent(container, config)
            OverlayConfig.Type.MINECRAFT_PROJECT -> addMinecraftContent(container, config)
            else -> addWebContent(container, config)
        }

        val resizeHandle = View(this).apply {
            tag = "resizeHandle"
            background = getDrawable(R.drawable.resize_handle)
            layoutParams = FrameLayout.LayoutParams(dpToPx(12), dpToPx(12)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        }
        container.addView(resizeHandle)

        applyInteractiveState(container, params, config)
        return container
    }

    private fun applyInteractiveState(container: FrameLayout, params: WindowManager.LayoutParams, config: OverlayConfig) {
        val isTouchThrough = config.touchThrough
        val isLocked = config.locked
        val canDragResize = !isTouchThrough && !isLocked
        val canCameraDoubleTap = isCameraUrl(config.url) && !isTouchThrough && isLocked

        val resizeHandle = container.findViewWithTag<View>("resizeHandle")
        resizeHandle?.visibility = if (config.showResizeHandle && canDragResize) View.VISIBLE else View.GONE
        resizeHandle?.setOnTouchListener(if (canDragResize) setupResizeListener(resizeHandle, params, config) else null)

        container.setOnTouchListener(if (canDragResize) setupDragListener(container, params, config) else null)

        val cameraView = container.findViewWithTag<PreviewView>("overlayCameraView")
        cameraView?.setOnTouchListener(
            when {
                canDragResize -> setupDragListener(container, params, config)
                canCameraDoubleTap -> setupCameraDoubleTapListener(config)
                else -> null
            }
        )

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

    private fun getCameraLensFacing(url: String): Int = when (url) {
        "camera://front" -> CameraSelector.LENS_FACING_FRONT
        "camera://back" -> CameraSelector.LENS_FACING_BACK
        else -> CameraSelector.LENS_FACING_BACK
    }

    private fun getCameraScaleX(url: String, flip: Boolean): Float {
        val front = getCameraLensFacing(url) == CameraSelector.LENS_FACING_FRONT
        return if (front && !flip) -1f else 1f
    }

    private fun getTargetRotation(config: OverlayConfig): Int {
        return when (config.cameraRotation) {
            "0" -> Surface.ROTATION_0
            "90" -> Surface.ROTATION_90
            "180" -> Surface.ROTATION_180
            "270" -> Surface.ROTATION_270
            else -> currentDisplayRotation
        }
    }

    private fun createDisplayListener(): DisplayManager.DisplayListener {
        return object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    val rotation = displayManager?.getDisplay(displayId)?.rotation ?: return
                    if (rotation != currentDisplayRotation) {
                        currentDisplayRotation = rotation
                        updateAllCameraRotations()
                        maybeAutoApplyProfileOnRotation()
                    }
                }
            }
        }
    }

    private fun updateOrientationListener() {
        val hasVisibleCamera = overlayViews.any { (id, container) ->
            container.visibility == View.VISIBLE && isCameraUrl(lastConfigs[id]?.url ?: repository.getOverlay(id)?.url ?: "")
        }
        if (hasVisibleCamera && displayListener == null) {
            displayListener = createDisplayListener().also {
                displayManager?.registerDisplayListener(it, null)
                LogStore.log(TAG, "Registered display orientation listener")
            }
        } else if (!hasVisibleCamera && displayListener != null) {
            displayListener?.let { displayManager?.unregisterDisplayListener(it) }
            displayListener = null
            LogStore.log(TAG, "Unregistered display orientation listener")
        }
    }

    private fun updateAllCameraRotations() {
        overlayViews.keys.forEach { id ->
            val config = lastConfigs[id] ?: repository.getOverlay(id) ?: return@forEach
            if (isCameraUrl(config.url)) {
                updateCameraRotation(id, config)
            }
        }
    }

    private fun updateCameraRotation(id: String, config: OverlayConfig) {
        val preview = cameraUseCases[id] ?: return
        val container = overlayViews[id] ?: return
        val rotation = getTargetRotation(config)
        try {
            preview.targetRotation = rotation
            applyCameraShape(container, config)
            LogStore.log(TAG, "Updated camera rotation for ${config.name}: ${rotationToLabel(rotation)} (mode=${config.cameraRotation})")
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to update camera rotation for ${config.name}", e)
        }
    }

    private fun rotationToLabel(rotation: Int): String = when (rotation) {
        Surface.ROTATION_0 -> "0°"
        Surface.ROTATION_90 -> "90°"
        Surface.ROTATION_180 -> "180°"
        Surface.ROTATION_270 -> "270°"
        else -> "?"
    }

    private fun getCurrentOrientationLabel(): String {
        return when (resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            else -> "portrait"
        }
    }

    private fun maybeAutoApplyProfileOnRotation() {
        if (!profileRepository.isAutoApplyOnRotationEnabled()) return
        val lastManual = profileRepository.getLastManualApplyTime()
        if (System.currentTimeMillis() - lastManual < 10_000L) {
            LogStore.log(TAG, "Auto-apply on rotation skipped: within 10s of manual apply")
            return
        }
        val orientation = getCurrentOrientationLabel()
        val candidates = profileRepository.getProfiles().filter {
            it.orientation == orientation || it.orientation == "any"
        }
        if (candidates.isEmpty()) return
        val profile = if (candidates.size == 1) {
            candidates.first()
        } else {
            // Most recently created/applied wins. Creation order is implicit in the list,
            // but to prefer recently applied we don't have a timestamp. Use the last one in
            // the saved list as a simple "most recent" proxy.
            candidates.last()
        }
        LogStore.log(TAG, "Auto-applying profile \"${profile.name}\" on rotation to $orientation")
        applyProfile(profile.id, isManual = false)
    }

    private fun applyProfile(profileId: String, isManual: Boolean) {
        val profile = profileRepository.getProfile(profileId) ?: run {
            LogStore.log(TAG, "Profile not found: $profileId")
            return
        }
        // Deep copy so the service and repository don't share mutable references.
        val snapshot = profile.overlays.map { it.copy() }
        repository.saveOverlays(snapshot)
        if (isManual) {
            profileRepository.recordManualApplyTime()
        }
        LogStore.log(TAG, "Applied profile \"${profile.name}\" with ${snapshot.size} overlays")
        fullReloadOverlays()
    }

    private fun fullReloadOverlays() {
        // Remove all existing overlay views and recreate them from the repository.
        overlayViews.keys.toList().forEach { id ->
            removeOverlayView(id)
            lastConfigs.remove(id)
        }
        if (isExpanded) {
            showOverlays()
        }
    }

    private fun bindCamera(config: OverlayConfig, previewView: PreviewView) {
        val lensFacing = getCameraLensFacing(config.url)
        previewView.scaleX = getCameraScaleX(config.url, config.cameraFlip)

        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                cameraUseCases[config.id]?.let { provider.unbind(it) }

                val preview = Preview.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                        it.targetRotation = getTargetRotation(config)
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                provider.bindToLifecycle(serviceLifecycleOwner, cameraSelector, preview)
                cameraUseCases[config.id] = preview
                LogStore.log(TAG, "Camera bound for overlay ${config.id} lens=$lensFacing flip=${config.cameraFlip}")
            } catch (e: Exception) {
                LogStore.logError(TAG, "Camera binding failed for ${config.id}", e)
                showToast("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyCameraOpacity(container: FrameLayout, config: OverlayConfig) {
        if (!isCameraUrl(config.url)) return
        container.alpha = if (config.touchThrough) 1f else config.opacityPercent / 100f
    }

    private fun applyCameraShape(container: FrameLayout, config: OverlayConfig) {
        if (config.cameraShape == "circle") {
            container.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val size = min(view.width, view.height)
                    val left = (view.width - size) / 2
                    val top = (view.height - size) / 2
                    outline.setOval(left, top, left + size, top + size)
                }
            }
        } else {
            container.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpToPx(config.cornerRadiusDp).toFloat())
                }
            }
        }
        container.clipToOutline = true
    }

    private fun applyCameraFilter(container: FrameLayout, config: OverlayConfig) {
        val previewView = container.findViewWithTag<PreviewView>("overlayCameraView") ?: return
        val paint = when (config.cameraFilter) {
            "mono" -> Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            "sepia" -> Paint().apply {
                colorFilter = ColorMatrixColorFilter(floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "warm" -> Paint().apply {
                colorFilter = ColorMatrixColorFilter(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "cool" -> Paint().apply {
                colorFilter = ColorMatrixColorFilter(floatArrayOf(
                    0.8f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1.2f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "vivid" -> Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.6f) })
            }
            "fade" -> Paint().apply {
                val saturationMatrix = ColorMatrix().apply { setSaturation(0.8f) }
                val offsetMatrix = ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 30f,
                    0f, 1f, 0f, 0f, 30f,
                    0f, 0f, 1f, 0f, 30f,
                    0f, 0f, 0f, 1f, 0f
                ))
                saturationMatrix.postConcat(offsetMatrix)
                colorFilter = ColorMatrixColorFilter(saturationMatrix)
            }
            else -> null
        }
        if (paint != null) {
            previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            previewView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun flipCamera(overlayId: String) {
        val config = repository.getOverlay(overlayId) ?: return
        if (!isCameraUrl(config.url)) return
        val newUrl = if (config.url == "camera://front") "camera://back" else "camera://front"
        val updated = config.copy(url = newUrl)
        repository.addOrUpdate(updated)
        LogStore.log(TAG, "Flipped camera for ${config.name}: $newUrl")
        reloadOverlays(overlayId)
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
            val saved = config.copy(posXPercent = xPercent, posYPercent = yPercent)
            repository.addOrUpdate(saved)
            lastConfigs[config.id] = saved
            LogStore.log(TAG, "Saved initial position for ${config.name}: $xPercent,$yPercent")
        } else {
            lastConfigs[config.id] = config
        }
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

        if ((oldConfig.posXPercent != newConfig.posXPercent || oldConfig.posYPercent != newConfig.posYPercent) &&
            newConfig.posXPercent >= 0f && newConfig.posYPercent >= 0f
        ) {
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

        if (isCameraUrl(newConfig.url)) {
            if (oldConfig.cameraShape != newConfig.cameraShape ||
                oldConfig.cornerRadiusDp != newConfig.cornerRadiusDp
            ) {
                applyCameraShape(container, newConfig)
                LogStore.log(TAG, "Updated camera shape for ${newConfig.name}")
            }
            if (oldConfig.cameraFilter != newConfig.cameraFilter) {
                applyCameraFilter(container, newConfig)
                LogStore.log(TAG, "Updated camera filter for ${newConfig.name}")
            }
            if (oldConfig.cameraFlip != newConfig.cameraFlip) {
                val previewView = container.findViewWithTag<PreviewView>("overlayCameraView")
                previewView?.scaleX = getCameraScaleX(newConfig.url, newConfig.cameraFlip)
                LogStore.log(TAG, "Updated camera flip for ${newConfig.name}")
            }
            if (oldConfig.cameraRotation != newConfig.cameraRotation) {
                updateCameraRotation(newConfig.id, newConfig)
                LogStore.log(TAG, "Updated camera rotation mode for ${newConfig.name}: ${newConfig.cameraRotation}")
            }
            if (oldConfig.opacityPercent != newConfig.opacityPercent ||
                oldConfig.touchThrough != newConfig.touchThrough
            ) {
                applyCameraOpacity(container, newConfig)
                LogStore.log(TAG, "Updated camera opacity for ${newConfig.name}")
            }
        }

        if (oldConfig.touchThrough != newConfig.touchThrough ||
            oldConfig.showResizeHandle != newConfig.showResizeHandle ||
            oldConfig.locked != newConfig.locked
        ) {
            val readd = oldConfig.touchThrough != newConfig.touchThrough
            updateInteractivity(container, params, newConfig, readd)
            LogStore.log(TAG, "Updated interactivity for ${newConfig.name}")
        }

        if (!isCameraUrl(newConfig.url) &&
            (oldConfig.scalePercent != newConfig.scalePercent ||
            oldConfig.zoomMode != newConfig.zoomMode ||
            oldConfig.contentOffsetX != newConfig.contentOffsetX ||
            oldConfig.contentOffsetY != newConfig.contentOffsetY)
        ) {
            val webView = container.findViewWithTag<WebView>("overlayWebView")
            webView?.let {
                applyZoom(it, newConfig)
                applyOffset(it, newConfig.contentOffsetX, newConfig.contentOffsetY)
            }
            LogStore.log(TAG, "Updated zoom/offset for ${newConfig.name}")
        }
    }

    private fun updateInteractivity(
        container: FrameLayout,
        params: WindowManager.LayoutParams,
        config: OverlayConfig,
        readd: Boolean = false
    ) {
        val currentlyNotTouchable = (params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        val shouldBeNotTouchable = config.touchThrough
        if (currentlyNotTouchable != shouldBeNotTouchable) {
            params.flags = if (shouldBeNotTouchable) {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
        }

        if (readd) {
            try {
                windowManager?.removeView(container)
                windowManager?.addView(container, params)
                LogStore.log(TAG, "Re-added ${config.name} to apply touch-through flag change")
            } catch (e: Exception) {
                LogStore.logError(TAG, "Re-add failed for ${config.name}, falling back to updateViewLayout", e)
                windowManager?.updateViewLayout(container, params)
            }
            applyInteractiveState(container, params, config)
            reorderOverlays()
        } else {
            windowManager?.updateViewLayout(container, params)
            applyInteractiveState(container, params, config)
        }
    }

    private fun setupDragListener(view: View, params: WindowManager.LayoutParams, config: OverlayConfig): View.OnTouchListener {
        var isCameraDoubleTap = false
        val gestureDetector = if (isCameraUrl(config.url)) {
            GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    isCameraDoubleTap = true
                    flipCamera(config.id)
                    return true
                }
            })
        } else null

        return View.OnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isCameraDoubleTap = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isCameraDoubleTap) return@OnTouchListener true
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
                    if (isCameraDoubleTap) {
                        isCameraDoubleTap = false
                        return@OnTouchListener true
                    }
                    if (isDragging) {
                        val screenSize = getScreenSize()
                        val xPercent = params.x.toFloat() / screenSize.first
                        val yPercent = params.y.toFloat() / screenSize.second
                        val latest = repository.getOverlay(config.id) ?: config
                        val saved = latest.copy(posXPercent = xPercent, posYPercent = yPercent)
                        repository.addOrUpdate(saved)
                        lastConfigs[config.id] = saved
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
                    val saved = latest.copy(widthDp = newWidthDp, heightDp = newHeightDp)
                    repository.addOrUpdate(saved)
                    lastConfigs[config.id] = saved
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

    private fun setupCameraDoubleTapListener(config: OverlayConfig): View.OnTouchListener {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                flipCamera(config.id)
                return true
            }
        })
        return View.OnTouchListener { _, event -> detector.onTouchEvent(event) }
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

    private fun addWebContent(container: FrameLayout, config: OverlayConfig) {
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
    }

    private fun addAiChatContent(container: FrameLayout, config: OverlayConfig) {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.floating_ai_chat, container, false)
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = dpToPx(24)
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.floatingChatRecyclerView)
        val adapter = ChatAdapter()
        aiAdapters[config.id] = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        refreshAiChatAdapter(adapter)

        view.findViewById<Button>(R.id.floatingChatSendButton).setOnClickListener {
            val input = view.findViewById<android.widget.EditText>(R.id.floatingChatInput)
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                sendFloatingAiMessage(text)
                input.text.clear()
            }
        }

        view.tag = "overlayAiChatView"
        container.addView(view)
    }

    private fun addMinecraftContent(container: FrameLayout, config: OverlayConfig) {
        val projectId = config.url.removePrefix("float://minecraft/")
            .removePrefix("focus/")
            .trim()
        minecraftProjectIds[config.id] = projectId

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.floating_minecraft_focus, container, false)
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply {
            topMargin = dpToPx(24)
        }

        view.tag = "overlayMinecraftView"
        container.addView(view)
        refreshMinecraftView(view, projectId)
    }

    private fun refreshMinecraftView(view: View?, projectId: String) {
        val root = view ?: return
        val project = ProjectRepository(this).getProject(projectId) ?: return

        val titleText = root.findViewById<TextView>(R.id.focusProjectName)
        val imageView = root.findViewById<ImageView>(R.id.focusReferenceImage)
        val stepCounter = root.findViewById<TextView>(R.id.focusStepCounter)
        val stepTitle = root.findViewById<TextView>(R.id.focusStepTitle)
        val stepDescription = root.findViewById<TextView>(R.id.focusStepDescription)
        val prevButton = root.findViewById<Button>(R.id.focusPrevButton)
        val nextButton = root.findViewById<Button>(R.id.focusNextButton)
        val zoomInButton = root.findViewById<Button>(R.id.focusZoomInButton)
        val zoomOutButton = root.findViewById<Button>(R.id.focusZoomOutButton)
        val materialProgress = root.findViewById<TextView>(R.id.focusMaterialProgress)

        titleText.text = project.name

        val currentStepIndex = project.steps.indexOfFirst { !it.completed }.coerceAtLeast(0)
        val step = project.steps.getOrNull(currentStepIndex)

        stepCounter.text = "Step ${currentStepIndex + 1} / ${project.totalSteps}"
        stepTitle.text = step?.title ?: "No steps yet"
        stepDescription.text = step?.description ?: ""
        stepDescription.visibility = if (stepDescription.text.isNullOrBlank()) View.GONE else View.VISIBLE

        val ref = project.references.firstOrNull { it.imageUri.isNotBlank() }
        if (ref != null) {
            try {
                imageView.setImageURI(android.net.Uri.parse(ref.imageUri))
            } catch (e: Exception) {
                imageView.setImageResource(R.drawable.ic_overlay)
            }
        } else {
            imageView.setImageResource(R.drawable.ic_overlay)
        }

        // Initialize or restore zoom scale for this project.
        val scale = minecraftImageScales.getOrPut(projectId) { 1f }
        applyImageZoom(imageView, scale)

        zoomInButton.setOnClickListener {
            val newScale = (minecraftImageScales[projectId] ?: 1f) * 1.2f
            minecraftImageScales[projectId] = newScale.coerceAtMost(5f)
            applyImageZoom(imageView, minecraftImageScales[projectId] ?: 1f)
        }

        zoomOutButton.setOnClickListener {
            val newScale = (minecraftImageScales[projectId] ?: 1f) / 1.2f
            minecraftImageScales[projectId] = newScale.coerceAtLeast(0.5f)
            applyImageZoom(imageView, minecraftImageScales[projectId] ?: 1f)
        }

        materialProgress.text = "Materials: ${project.materialProgressPercent}%"

        prevButton.setOnClickListener {
            // Move to previous step by marking current as not completed.
            val idx = project.steps.indexOfFirst { !it.completed }.coerceAtLeast(0)
            if (idx > 0) {
                val updated = project.copy(
                    steps = project.steps.mapIndexed { i, s ->
                        if (i == idx - 1) s.copy(completed = false) else s
                    },
                    updatedAt = System.currentTimeMillis()
                )
                ProjectRepository(this).saveProject(updated)
                refreshMinecraftView(root, projectId)
            }
        }

        nextButton.setOnClickListener {
            val idx = project.steps.indexOfFirst { !it.completed }.coerceAtLeast(0)
            if (idx < project.steps.size) {
                val updated = project.copy(
                    steps = project.steps.mapIndexed { i, s ->
                        if (i == idx) s.copy(completed = true) else s
                    },
                    updatedAt = System.currentTimeMillis()
                )
                ProjectRepository(this).saveProject(updated)
                refreshMinecraftView(root, projectId)
            }
        }
    }

    private fun applyImageZoom(imageView: ImageView, scale: Float) {
        imageView.post {
            val drawable = imageView.drawable ?: return@post
            val matrix = Matrix()
            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()
            val viewWidth = imageView.width.toFloat()
            val viewHeight = imageView.height.toFloat()
            if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return@post

            // Fit center as baseline, then apply scale.
            val scaleFit = (viewWidth / drawableWidth).coerceAtMost(viewHeight / drawableHeight)
            val finalScale = scaleFit * scale
            val dx = (viewWidth - drawableWidth * finalScale) / 2f
            val dy = (viewHeight - drawableHeight * finalScale) / 2f

            matrix.setScale(finalScale, finalScale)
            matrix.postTranslate(dx, dy)
            imageView.imageMatrix = matrix
        }
    }

    private fun refreshAiChatAdapter(adapter: ChatAdapter?) {
        try {
            adapter ?: return
            val conversation = ConversationRepository(this).getConversation()
            adapter.submitList(conversation.messages)
        } catch (e: Exception) {
            LogStore.logError(TAG, "refreshAiChatAdapter failed", e)
        }
    }

    private fun sendFloatingAiMessage(text: String) {
        try {
            ConversationRepository(this).addMessage(
                com.floatoverlay.app.model.Message(
                    role = com.floatoverlay.app.model.Message.Role.USER,
                    content = text
                )
            )
            aiAdapters.values.forEach { refreshAiChatAdapter(it) }

            // Use the same provider and tools as the in-app AI.
            val provider = AIProviderFactory.create(this)
            val conversation = ConversationRepository(this).getConversation()
            provider.sendMessage(
                conversation.messages,
                com.floatoverlay.app.ai.ToolRegistry.all(),
                object : com.floatoverlay.app.ai.AIProvider.AIResponseCallback {
                    override fun onLoading() {}
                    override fun onResult(message: com.floatoverlay.app.model.Message) {
                        handleFloatingAiResponse(message)
                    }
                    override fun onError(error: Throwable) {}
                }
            )
        } catch (e: Exception) {
            LogStore.logError(TAG, "sendFloatingAiMessage failed", e)
        }
    }

    private fun handleFloatingAiResponse(message: com.floatoverlay.app.model.Message) {
        try {
            val toolCall = message.toolCall
            if (toolCall != null) {
                ConversationRepository(this).addMessage(message)
                val tool = com.floatoverlay.app.ai.ToolRegistry.get(toolCall.toolName)
                val result = tool?.execute(toolCall.arguments)
                    ?: com.floatoverlay.app.ai.ToolExecutionResult.Error("Tool not found")
                ConversationRepository(this).addMessage(
                    com.floatoverlay.app.model.Message(
                        role = com.floatoverlay.app.model.Message.Role.TOOL,
                        content = result.asText(),
                        toolResult = com.floatoverlay.app.model.Message.ToolResult(
                            toolName = toolCall.toolName,
                            success = result is com.floatoverlay.app.ai.ToolExecutionResult.Success,
                            message = result.asText(),
                            toolCallId = toolCall.toolCallId
                        )
                    )
                )
            } else {
                ConversationRepository(this).addMessage(message)
            }
            aiAdapters.values.forEach { refreshAiChatAdapter(it) }
            // If a project was created or modified, refresh any open Minecraft overlays.
            minecraftProjectIds.entries.forEach { (id, projectId) ->
                overlayViews[id]?.let { refreshMinecraftView(it.findViewWithTag("overlayMinecraftView"), projectId) }
            }
        } catch (e: Exception) {
            LogStore.logError(TAG, "handleFloatingAiResponse failed", e)
        }
    }

    private fun isCameraUrl(url: String): Boolean = url.startsWith("camera://")

    private inner class ServiceLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
        fun handleEvent(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }

    companion object {
        private const val TAG = "FloatOverlayService"
        private const val CHANNEL_ID = "float_overlay_channel"
        private const val FOREGROUND_SERVICE_ID = 1

        const val ACTION_INCREMENT_BADGE = "com.floatoverlay.app.INCREMENT_BADGE"
        const val ACTION_CLEAR_BADGE = "com.floatoverlay.app.CLEAR_BADGE"
        const val ACTION_RELOAD_OVERLAYS = "com.floatoverlay.app.RELOAD_OVERLAYS"
        const val ACTION_REFRESH_OVERLAY = "com.floatoverlay.app.REFRESH_OVERLAY"
        const val ACTION_REORDER_OVERLAYS = "com.floatoverlay.app.REORDER_OVERLAYS"
        const val ACTION_APPLY_PROFILE = "com.floatoverlay.app.APPLY_PROFILE"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_OVERLAY_ID = "overlay_id"
        const val EXTRA_SKIP_CAMERA = "skip_camera"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_MANUAL = "profile_manual"

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

        fun reorderOverlays(context: Context) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_REORDER_OVERLAYS
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun openFloatingAI(context: Context) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_OPEN_FLOATING_AI
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun toggleFloatingAI(context: Context) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_TOGGLE_FLOATING_AI
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun openFloatingMinecraftProject(context: Context, projectId: String) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_OPEN_FLOATING_MINECRAFT
                putExtra(EXTRA_PROJECT_ID, projectId)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun refreshMinecraftOverlays(context: Context) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_REFRESH_MINECRAFT_OVERLAYS
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun applyProfile(context: Context, profileId: String, manual: Boolean = true) {
            val intent = Intent(context, FloatOverlayService::class.java).apply {
                action = ACTION_APPLY_PROFILE
                putExtra(EXTRA_PROFILE_ID, profileId)
                putExtra(EXTRA_PROFILE_MANUAL, manual)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        private const val ACTION_OPEN_FLOATING_AI = "com.floatoverlay.app.OPEN_FLOATING_AI"
        private const val ACTION_TOGGLE_FLOATING_AI = "com.floatoverlay.app.TOGGLE_FLOATING_AI"
        private const val ACTION_OPEN_FLOATING_MINECRAFT = "com.floatoverlay.app.OPEN_FLOATING_MINECRAFT"
        private const val ACTION_REFRESH_MINECRAFT_OVERLAYS = "com.floatoverlay.app.REFRESH_MINECRAFT_OVERLAYS"

        private const val AI_OVERLAY_ID = "float_ai"
        private const val MINECRAFT_OVERLAY_PREFIX = "float_minecraft_"

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

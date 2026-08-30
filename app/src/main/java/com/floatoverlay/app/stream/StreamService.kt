package com.floatoverlay.app.stream

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Foreground service that captures the screen via MediaProjection and streams
 * it over WebRTC to a private browser viewer.
 *
 * For the MVP this service is created as a placeholder; the capture and WebRTC
 * pipeline will be wired up in subsequent phases.
 */
class StreamService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO Phase 2/3/4: start foreground notification and begin capture.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // TODO: release MediaProjection, WebRTC factory, and notification.
        super.onDestroy()
    }
}

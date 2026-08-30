package com.floatoverlay.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogStore {

    private const val MAX_LOGS = 100

    // Drop spammy WebRTC network-change messages that can flood the in-app log.
    private val spammyTags = setOf("NetworkMonitorAutoDetect", "NetworkMonitor")

    private val logs = ArrayDeque<String>()
    private var listeners = mutableListOf<() -> Unit>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun log(tag: String, message: String) {
        if (spammyTags.any { tag.contains(it, ignoreCase = true) }) return
        val line = "[${timeFormat.format(Date())}] $tag: $message"
        logs.addLast(line)
        while (logs.size > MAX_LOGS) logs.removeFirst()
        listeners.forEach { it.invoke() }
    }

    @Synchronized
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val stack = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
        log(tag, "ERROR: $message$stack")
    }

    @Synchronized
    fun getLogs(): List<String> = logs.toList()

    @Synchronized
    fun clear() {
        logs.clear()
        listeners.forEach { it.invoke() }
    }

    @Synchronized
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}

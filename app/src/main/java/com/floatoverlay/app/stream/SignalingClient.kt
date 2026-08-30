package com.floatoverlay.app.stream

import android.util.Log
import com.floatoverlay.app.LogStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket signaling client for the Float streaming server.
 *
 * Connects as the sender and relays SDP/ICE messages to/from the viewer.
 */
class SignalingClient(
    private val serverUrl: String,
    private val streamId: String,
    private val token: String,
    private val listener: Listener
) {

    interface Listener {
        fun onConnected()
        fun onViewerJoined()
        fun onOfferAnswerReceived(sdpType: String, sdp: String)
        fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String)
        fun onDisconnected()
        fun onError(message: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    fun connect() {
        val wsUrl = buildWsUrl()
        LogStore.log(TAG, "Connecting to signaling server: $wsUrl")
        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                LogStore.log(TAG, "Signaling socket open")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                LogStore.log(TAG, "Signaling socket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                LogStore.log(TAG, "Signaling socket closed: $code $reason")
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                LogStore.logError(TAG, "Signaling socket failure", t)
                listener.onError(t.message ?: "WebSocket failure")
            }
        })
    }

    private fun buildWsUrl(): String {
        val base = serverUrl.replace("http://", "ws://").replace("https://", "wss://").trimEnd('/')
        return "$base/ws/sender/$streamId?token=$token"
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.optString("type")) {
                "viewer-joined" -> listener.onViewerJoined()
                "answer" -> {
                    val sdp = json.getString("sdp")
                    listener.onOfferAnswerReceived("answer", sdp)
                }
                "candidate" -> {
                    val candidate = json.getJSONObject("candidate")
                    listener.onIceCandidateReceived(
                        candidate.optString("sdpMid"),
                        candidate.optInt("sdpMLineIndex"),
                        candidate.optString("candidate")
                    )
                }
                "viewer-disconnected" -> listener.onDisconnected()
                "pong" -> { /* keep-alive response */ }
                else -> LogStore.log(TAG, "Unknown signaling message: $text")
            }
        } catch (e: Exception) {
            LogStore.logError(TAG, "Failed to parse signaling message", e)
        }
    }

    fun sendOffer(sdpType: String, sdp: String) {
        val json = JSONObject().apply {
            put("type", sdpType)
            put("sdp", sdp)
        }
        webSocket?.send(json.toString())
    }

    fun sendIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val json = JSONObject().apply {
            put("type", "candidate")
            put(
                "candidate",
                JSONObject().apply {
                    put("sdpMid", sdpMid)
                    put("sdpMLineIndex", sdpMLineIndex)
                    put("candidate", candidate)
                }
            )
        }
        webSocket?.send(json.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "Stream stopping")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    companion object {
        private const val TAG = "SignalingClient"
    }
}

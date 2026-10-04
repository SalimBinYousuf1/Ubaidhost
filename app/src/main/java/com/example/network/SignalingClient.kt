package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

class SignalingClient(
    private val onMessageReceived: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var serverUrl: String = ""
    private var shouldKeepConnected = false

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect(url: String) {
        serverUrl = url
        shouldKeepConnected = true
        initiateConnection()
    }

    private fun initiateConnection() {
        if (serverUrl.isBlank() || serverUrl.startsWith("wss://relay.ubaid.internal")) {
            // Local-only mode or placeholder relay; keep status clean
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            return
        }

        _connectionStatus.value = ConnectionStatus.CONNECTING
        try {
            val request = Request.Builder().url(serverUrl).build()
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    reconnectJob?.cancel()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    onMessageReceived(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    if (shouldKeepConnected) {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w("SignalingClient", "Relay connection failed: ${t.message}")
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    if (shouldKeepConnected) {
                        scheduleReconnect()
                    }
                }
            })
        } catch (e: Exception) {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            if (shouldKeepConnected) {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            delay(10000) // 10s backoff
            if (shouldKeepConnected) {
                initiateConnection()
            }
        }
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) ?: false
    }

    fun disconnect() {
        shouldKeepConnected = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Host disconnected")
        webSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }
}

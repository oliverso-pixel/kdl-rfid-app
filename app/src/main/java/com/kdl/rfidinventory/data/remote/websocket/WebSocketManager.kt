package com.kdl.rfidinventory.data.remote.websocket

import android.content.Context
import android.content.SharedPreferences
import com.kdl.rfidinventory.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class WebSocketState {
    object Connected : WebSocketState()
    object Connecting : WebSocketState()
    data class Disconnected(val reason: String? = null) : WebSocketState()
    data class Error(val error: String) : WebSocketState()
}

@Singleton
class WebSocketManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private val _connectionState = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected())
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    private val _enableWebSocket = MutableStateFlow(getWebSocketEnabled())
    val enableWebSocket: StateFlow<Boolean> = _enableWebSocket.asStateFlow()

    private val _websocketUrl = MutableStateFlow(getWebSocketUrl())
    val websocketUrl: StateFlow<String> = _websocketUrl.asStateFlow()

    // 關鍵：isOnline 取決於 WebSocket 是否啟用且已連接
    val isOnline: StateFlow<Boolean> = combine(
        _connectionState,
        _enableWebSocket
    ) { state, enabled ->
        enabled && state is WebSocketState.Connected
    }.stateIn(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(Constants.WS_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(Constants.WS_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS)
        .writeTimeout(Constants.WS_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    init {
        // 監聽 WebSocket 啟用狀態變化
        CoroutineScope(Dispatchers.IO).launch {
            _enableWebSocket.collect { enabled ->
                if (enabled) {
                    Timber.d("🟢 WebSocket enabled, attempting to connect...")
                    connect()
                } else {
                    Timber.d("🔴 WebSocket disabled, disconnecting...")
                    disconnect()
                }
            }
        }
    }

    private fun getWebSocketEnabled(): Boolean {
        return sharedPreferences.getBoolean(Constants.PREF_ENABLE_WEBSOCKET, false)
    }

    private fun getWebSocketUrl(): String {
        return sharedPreferences.getString(
            Constants.PREF_WEBSOCKET_URL,
            Constants.DEFAULT_WEBSOCKET_URL
        ) ?: Constants.DEFAULT_WEBSOCKET_URL
    }

    fun setWebSocketEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(Constants.PREF_ENABLE_WEBSOCKET, enabled)
            .apply()
        _enableWebSocket.value = enabled
        Timber.d("🔧 WebSocket enabled set to: $enabled")
    }

    fun updateWebSocketUrl(url: String) {
        sharedPreferences.edit()
            .putString(Constants.PREF_WEBSOCKET_URL, url)
            .apply()
        _websocketUrl.value = url
        Timber.d("🔧 WebSocket URL updated to: $url")

        // 如果啟用且已連接，重新連接
        if (_enableWebSocket.value && webSocket != null) {
            CoroutineScope(Dispatchers.IO).launch {
                disconnect()
                delay(500)
                connect()
            }
        }
    }

    fun connect() {
        // ⭐ 只有啟用時才連接
        if (!_enableWebSocket.value) {
            Timber.w("⚠️ WebSocket is disabled, skipping connection")
            _connectionState.value = WebSocketState.Disconnected("WebSocket 已禁用")
            return
        }

        if (webSocket != null) {
            Timber.d("⚠️ WebSocket already connected or connecting")
            return
        }

        try {
            _connectionState.value = WebSocketState.Connecting

            val url = _websocketUrl.value
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Timber.d("✅ WebSocket connected")
                    _connectionState.value = WebSocketState.Connected
                    startHeartbeat()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Timber.d("📨 Received message: $text")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Timber.d("👋 WebSocket closing: $reason")
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Timber.d("🔌 WebSocket closed: $reason")
                    _connectionState.value = WebSocketState.Disconnected(reason)
                    stopHeartbeat()
                    this@WebSocketManager.webSocket = null

                    // 只在啟用時自動重連
                    if (_enableWebSocket.value) {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Timber.e(t, "❌ WebSocket error")
                    _connectionState.value = WebSocketState.Error(t.message ?: "Unknown error")
                    stopHeartbeat()
                    this@WebSocketManager.webSocket = null

                    // 只在啟用時自動重連
                    if (_enableWebSocket.value) {
                        scheduleReconnect()
                    }
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to connect WebSocket")
            _connectionState.value = WebSocketState.Error(e.message ?: "Connection failed")
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        stopHeartbeat()

        webSocket?.let {
            it.close(1000, "User disconnected")
            webSocket = null
        }

        _connectionState.value = WebSocketState.Disconnected("手動斷開")
        Timber.d("🔌 WebSocket disconnected")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && webSocket != null) {
                try {
                    webSocket?.send("ping")
                    Timber.d("💓 Heartbeat sent")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to send heartbeat")
                    break
                }
                delay(Constants.WS_HEARTBEAT_INTERVAL)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun scheduleReconnect() {
        if (!_enableWebSocket.value) {
            Timber.d("⚠️ WebSocket disabled, skipping reconnect")
            return
        }

        reconnectJob?.cancel()
        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            delay(5000) // 5 秒後重連
            if (_enableWebSocket.value) {
                Timber.d("🔄 Attempting to reconnect...")
                connect()
            }
        }
    }

    fun sendMessage(message: String) {
        if (!_enableWebSocket.value) {
            Timber.w("⚠️ WebSocket is disabled, cannot send message")
            return
        }

        webSocket?.send(message) ?: run {
            Timber.w("⚠️ WebSocket not connected, cannot send message")
        }
    }
}
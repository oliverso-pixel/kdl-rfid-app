package com.kdl.rfidinventory.data.remote.websocket

import android.content.Context
import com.kdl.rfidinventory.data.local.datastore.SettingsDataStore
import com.kdl.rfidinventory.data.repository.DeviceRepository
import com.kdl.rfidinventory.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepository: DeviceRepository,
    private val settingsDataStore: SettingsDataStore
) {
    private var webSocket: WebSocket? = null

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        const val INITIAL_RECONNECT_DELAY = 1_000L
        const val MAX_RECONNECT_DELAY = 30_000L
        const val BACKOFF_FACTOR = 2.0
    }

    @Volatile private var isManualDisconnect = false
    @Volatile private var reconnectAttempt = 0

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected())
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    // 🔧 直接把 DataStore 的 Flow 轉成 StateFlow
    val websocketUrl: StateFlow<String> = settingsDataStore.websocketUrl
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsDataStore.DEFAULT_WEBSOCKET_URL
        )

    val enableWebSocket: StateFlow<Boolean> = settingsDataStore.websocketEnabled
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsDataStore.DEFAULT_WEBSOCKET_ENABLED
        )

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var lastPongTime = System.currentTimeMillis()

    init {
        // isOnline 計算
        scope.launch {
            combine(enableWebSocket, _connectionState) { enabled, state ->
                enabled && state is WebSocketState.Connected
            }.collect { online ->
                _isOnline.value = online
                Timber.d("📡 isOnline updated: $online")
            }
        }

        // 監聽 URL 變化：如果外部改 URL 且目前已連線，自動重連
        scope.launch {
            websocketUrl
                .drop(1)                  // 忽略初始值
                .distinctUntilChanged()
                .collect { newUrl ->
                    Timber.d("🔄 WebSocket URL flow changed: $newUrl")
                    if (webSocket != null || reconnectJob?.isActive == true) {
                        Timber.d("🔄 Active connection exists, restarting with new URL")
                        restartConnection()
                    }
                }
        }

        // 監聽 enabled 變化
        scope.launch {
            enableWebSocket
                .drop(1)
                .distinctUntilChanged()
                .collect { enabled ->
                    Timber.d("🔄 WebSocket enabled flow changed: $enabled")
                    if (enabled) {
                        if (webSocket == null) connect()
                    } else {
                        disconnect()
                    }
                }
        }
    }

    /**
     * 使用者主動呼叫 → 重置手動斷開標記
     */
    fun connect() {
        if (!enableWebSocket.value) {
            Timber.w("⚠️ WebSocket is disabled")
            return
        }
        isManualDisconnect = false
        doConnect()
    }

    private fun doConnect() {
        if (webSocket != null) {
            Timber.w("⚠️ WebSocket already connected, skipping")
            return
        }
        if (!enableWebSocket.value) {
            Timber.w("⚠️ WebSocket disabled, abort connect")
            return
        }

        val deviceId = deviceRepository.getDeviceId()
        val baseUrl = websocketUrl.value
        val url = "$baseUrl?deviceId=$deviceId"

        Timber.d("🔌 Connecting to WebSocket: $url (attempt #${reconnectAttempt + 1})")
        _connectionState.value = WebSocketState.Connecting

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("✅ WebSocket connected")
                _connectionState.value = WebSocketState.Connected
                reconnectAttempt = 0
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("📩 WebSocket 收到訊息: $text")
                lastPongTime = System.currentTimeMillis()
                try {
                    if (text.contains("\"type\":\"pong\"")) {
                        Timber.v("💓 Pong received")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing message")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("🔌 WebSocket closing: $code / $reason")
                _connectionState.value = WebSocketState.Disconnected(reason)
                stopHeartbeat()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("❌ WebSocket closed: $code / $reason")
                _connectionState.value = WebSocketState.Disconnected(reason)
                this@WebSocketManager.webSocket = null
                stopHeartbeat()
                scheduleReconnect("closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "❌ WebSocket error")
                _connectionState.value = WebSocketState.Error(t.message ?: "Unknown error")
                this@WebSocketManager.webSocket = null
                stopHeartbeat()
                scheduleReconnect("failure: ${t.message}")
            }
        })
    }

    private fun scheduleReconnect(reason: String) {
        if (isManualDisconnect) {
            Timber.d("🛑 Manual disconnect flag set, no reconnect ($reason)")
            return
        }
        if (!enableWebSocket.value) {
            Timber.d("🛑 WebSocket disabled, no reconnect ($reason)")
            return
        }
        if (reconnectJob?.isActive == true) {
            Timber.d("⏭️ Reconnect already scheduled, skipping")
            return
        }

        val delayMs = (INITIAL_RECONNECT_DELAY *
                Math.pow(BACKOFF_FACTOR, reconnectAttempt.toDouble()))
            .toLong()
            .coerceAtMost(MAX_RECONNECT_DELAY)

        reconnectAttempt++

        Timber.w("🔄 Scheduling reconnect #$reconnectAttempt in ${delayMs}ms (reason: $reason)")

        reconnectJob = scope.launch {
            try {
                delay(delayMs)
                if (isManualDisconnect || !enableWebSocket.value) {
                    Timber.d("🛑 Reconnect cancelled during delay")
                    return@launch
                }
                if (webSocket != null) {
                    Timber.d("⏭️ Already connected, skip reconnect")
                    return@launch
                }
                Timber.d("🔄 Executing reconnect #$reconnectAttempt...")
                doConnect()
            } catch (e: CancellationException) {
                Timber.d("🛑 Reconnect job cancelled")
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        Timber.d("🛑 Reconnect cancelled & counter reset")
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(Constants.WS_HEARTBEAT_INTERVAL)
                if (webSocket == null || _connectionState.value !is WebSocketState.Connected) {
                    Timber.w("⚠️ WebSocket not connected, stopping heartbeat")
                    break
                }
                sendHeartbeat()
            }
        }
        Timber.d("💓 Heartbeat started (interval: ${Constants.WS_HEARTBEAT_INTERVAL}ms)")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Timber.d("💓 Heartbeat stopped")
    }

    private fun sendHeartbeat() {
        val deviceId = deviceRepository.getDeviceId()
        val heartbeatMessage = """
            {
                "type": "heartbeat",
                "deviceId": "$deviceId",
                "message": "ping",
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        val success = webSocket?.send(heartbeatMessage) ?: false
        if (success) {
            Timber.v("💓 Heartbeat sent")
        } else {
            Timber.w("⚠️ Failed to send heartbeat")
            webSocket?.cancel()
            webSocket = null
            _connectionState.value = WebSocketState.Disconnected("heartbeat failed")
            stopHeartbeat()
            scheduleReconnect("heartbeat failure")
        }
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: run {
            Timber.w("⚠️ WebSocket not connected, cannot send message")
            false
        }
    }

    fun disconnect() {
        Timber.d("🔌 Manual disconnect requested")
        isManualDisconnect = true
        cancelReconnect()
        stopHeartbeat()
        webSocket?.close(1000, "Client disconnecting")
        webSocket = null
        _connectionState.value = WebSocketState.Disconnected("Client disconnected")
        Timber.d("🔌 WebSocket disconnected (manual)")
    }

    /**
     * 🔧 改：寫入 DataStore（flow collector 會自動觸發重連）
     */
    fun updateWebSocketUrl(url: String) {
        scope.launch {
            settingsDataStore.setWebSocketUrl(url)
            Timber.d("🔄 WebSocket URL updated via DataStore: $url")
            // 重連邏輯由 init 裡的 websocketUrl collector 處理
        }
    }

    /**
     * 🆕 內部使用：URL 變化時重新啟動連線
     */
    private fun restartConnection() {
        scope.launch {
            Timber.d("🔄 Restarting connection with new URL")
            stopHeartbeat()
            cancelReconnect()
            webSocket?.close(1000, "URL changed")
            webSocket = null
            delay(300)
            isManualDisconnect = false
            doConnect()
        }
    }

    /**
     * 🔧 改：寫入 DataStore（flow collector 會處理 connect/disconnect）
     */
    fun setWebSocketEnabled(enabled: Boolean) {
        scope.launch {
            settingsDataStore.setWebSocketEnabled(enabled)
            Timber.d("🔄 WebSocket enabled via DataStore: $enabled")
        }
    }

    fun cleanup() {
        Timber.d("🧹 WebSocketManager cleanup")
        isManualDisconnect = true
        cancelReconnect()
        stopHeartbeat()
        webSocket?.close(1000, "App shutting down")
        webSocket = null
        scope.cancel()
        Timber.d("🧹 WebSocketManager cleaned up")
    }
}
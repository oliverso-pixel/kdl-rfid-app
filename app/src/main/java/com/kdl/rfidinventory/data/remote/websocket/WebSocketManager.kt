package com.kdl.rfidinventory.data.remote.websocket

//import android.content.Context
//import com.kdl.rfidinventory.data.local.preferences.PreferencesManager
//import com.kdl.rfidinventory.data.repository.DeviceRepository
//import com.kdl.rfidinventory.util.Constants
//import dagger.hilt.android.qualifiers.ApplicationContext
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//import okhttp3.*
//import timber.log.Timber
//import java.util.concurrent.TimeUnit
//import javax.inject.Inject
//import javax.inject.Singleton

import android.content.Context
import com.kdl.rfidinventory.data.local.preferences.PreferencesManager
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
    private val preferencesManager: PreferencesManager
) {

//    private var webSocket: WebSocket? = null
//
//    private var heartbeatJob: Job? = null
//    private val heartbeatScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//
//    private val client = OkHttpClient.Builder()
//        .connectTimeout(30, TimeUnit.SECONDS)
//        .readTimeout(30, TimeUnit.SECONDS)
//        .writeTimeout(30, TimeUnit.SECONDS)
//        .pingInterval(20, TimeUnit.SECONDS)
//        .build()
//
//    // WebSocket 連接狀態
//    private val _connectionState = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected())
//    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()
//
//    // WebSocket URL
//    private val _websocketUrl = MutableStateFlow(preferencesManager.getWebSocketUrl())
//    val websocketUrl: StateFlow<String> = _websocketUrl.asStateFlow()
//
//    // WebSocket 啟用狀態
//    private val _enableWebSocket = MutableStateFlow(preferencesManager.isWebSocketEnabled())
//    val enableWebSocket: StateFlow<Boolean> = _enableWebSocket.asStateFlow()
//
//    private val _isOnline = MutableStateFlow(false)
//    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
//
//    private var lastPongTime = System.currentTimeMillis()
//
//    init {
//        heartbeatScope.launch {
//            kotlinx.coroutines.flow.combine(
//                _enableWebSocket,
//                _connectionState
//            ) { enabled, state ->
//                enabled && state is WebSocketState.Connected
//            }.collect { online ->
//                _isOnline.value = online
//                Timber.d("📡 isOnline updated: $online")
//            }
//        }
//    }
//
//    /**
//     * 連接 WebSocket
//     */
//    fun connect() {
//        if (!_enableWebSocket.value) {
//            Timber.w("⚠️ WebSocket is disabled")
//            return
//        }
//
//        if (webSocket != null) {
//            Timber.w("⚠️ WebSocket already connected")
//            return
//        }
//
//        val deviceId = deviceRepository.getDeviceId()
//        val baseUrl = _websocketUrl.value
//        val url = "$baseUrl?deviceId=$deviceId"
//
//        Timber.d("🔌 Connecting to WebSocket: $url")
//        _connectionState.value = WebSocketState.Connecting
//
//        val request = Request.Builder()
//            .url(url)
//            .build()
//
//        webSocket = client.newWebSocket(request, object : WebSocketListener() {
//            override fun onOpen(webSocket: WebSocket, response: Response) {
//                Timber.d("✅ WebSocket connected")
//                _connectionState.value = WebSocketState.Connected
//
//                // 連接成功後啟動心跳包
//                startHeartbeat()
//            }
//
//            override fun onMessage(webSocket: WebSocket, text: String) {
//                Timber.d("📩 WebSocket 收到訊息: $text")
//                // 更新最後收到消息的時間
//                lastPongTime = System.currentTimeMillis()
//
//                // 解析消息類型
//                try {
//                    if (text.contains("\"type\":\"pong\"")) {
//                        Timber.v("💓 Pong received")
//                    }
//                } catch (e: Exception) {
//                    Timber.e(e, "Error parsing message")
//                }
//            }
//
//            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
//                Timber.d("🔌 WebSocket closing: $code / $reason")
//                _connectionState.value = WebSocketState.Disconnected(reason)
//                stopHeartbeat()
//            }
//
//            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
//                Timber.d("❌ WebSocket closed: $code / $reason")
//                _connectionState.value = WebSocketState.Disconnected(reason)
//                this@WebSocketManager.webSocket = null
//                stopHeartbeat()
//            }
//
//            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
//                Timber.e(t, "❌ WebSocket error")
//                _connectionState.value = WebSocketState.Error(t.message ?: "Unknown error")
//                this@WebSocketManager.webSocket = null
//                stopHeartbeat()
//            }
//        })
//    }
//
//    /**
//     * 啟動心跳包
//     */
//    private fun startHeartbeat() {
//        stopHeartbeat()
//
//        heartbeatJob = heartbeatScope.launch {
//            while (isActive) {
//                delay(Constants.WS_HEARTBEAT_INTERVAL)  // 30秒
//
//                if (webSocket == null || _connectionState.value !is WebSocketState.Connected) {
//                    Timber.w("⚠️ WebSocket not connected, stopping heartbeat")
//                    break
//                }
//
//                // 檢查是否超時
////                val timeSinceLastPong = System.currentTimeMillis() - lastPongTime
////                if (timeSinceLastPong > Constants.WS_TIMEOUT) {
////                    Timber.e("❌ WebSocket timeout (no pong for ${timeSinceLastPong}ms)")
////                    reconnect()
////                    break
////                }
//
//                sendHeartbeat()
//            }
//        }
//
//        Timber.d("💓 Heartbeat started (interval: ${Constants.WS_HEARTBEAT_INTERVAL}ms)")
//    }
//
//    /**
//     * 重啟心跳包（接收到消息時調用）
//     */
//    private fun restartHeartbeat() {
//        if (_connectionState.value is WebSocketState.Connected) {
//            startHeartbeat()
//        }
//    }
//
//    /**
//     * 停止心跳包
//     */
//    private fun stopHeartbeat() {
//        heartbeatJob?.cancel()
//        heartbeatJob = null
//        Timber.d("💓 Heartbeat stopped")
//    }
//
//    /**
//     * 發送心跳包
//     */
//    private fun sendHeartbeat() {
//        val deviceId = deviceRepository.getDeviceId()
//        val heartbeatMessage = """
//            {
//                "type": "heartbeat",
//                "deviceId": "$deviceId",
//                "message": "ping",
//                "timestamp": ${System.currentTimeMillis()}
//            }
//        """.trimIndent()
//
//        val success = webSocket?.send(heartbeatMessage) ?: false
//
//        if (success) {
//            Timber.v("💓 Heartbeat sent")
//        } else {
//            Timber.w("⚠️ Failed to send heartbeat, reconnecting...")
////            reconnect()
//        }
//    }
//
//    /**
//     * 重新連接
//     */
//    private fun reconnect() {
//        heartbeatScope.launch {
//            disconnect()
//            delay(1000)
//            connect()
//        }
//    }
//
//    /**
//     * 發送消息
//     */
//    fun send(message: String): Boolean {
//        return webSocket?.send(message) ?: run {
//            Timber.w("⚠️ WebSocket not connected, cannot send message")
//            false
//        }
//    }
//
//    /**
//     * 斷開連接
//     */
//    fun disconnect() {
//        stopHeartbeat()
//        webSocket?.close(1000, "Client disconnecting")
//        webSocket = null
//        _connectionState.value = WebSocketState.Disconnected("Client disconnected")
//        Timber.d("🔌 WebSocket disconnected")
//    }
//
//    /**
//     * 更新 WebSocket URL
//     */
//    fun updateWebSocketUrl(url: String) {
//        _websocketUrl.value = url
//        preferencesManager.setWebSocketUrl(url)
//        Timber.d("🔄 WebSocket URL updated: $url")
//
//        if (webSocket != null) {
//            disconnect()
//            connect()
//        }
//    }
//
//    /**
//     * 設置 WebSocket 啟用狀態
//     */
//    fun setWebSocketEnabled(enabled: Boolean) {
//        _enableWebSocket.value = enabled
//        preferencesManager.setWebSocketEnabled(enabled)
//        Timber.d("🔄 WebSocket enabled: $enabled")
//
//        if (enabled) {
//            connect()
//        } else {
//            disconnect()
//        }
//    }
//
//    /**
//     * 清理資源
//     */
//    fun cleanup() {
//        stopHeartbeat()
//        heartbeatScope.cancel()
//        disconnect()
//        Timber.d("🧹 WebSocketManager cleaned up")
//    }
private var webSocket: WebSocket? = null

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null                // 🆕 重連任務
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 🆕 重連參數
    private companion object {
        const val INITIAL_RECONNECT_DELAY = 1_000L       // 1 秒
        const val MAX_RECONNECT_DELAY = 30_000L          // 最大 30 秒
        const val BACKOFF_FACTOR = 2.0                    // 每次 × 2
    }

    // 🆕 狀態標記
    @Volatile private var isManualDisconnect = false     // 是否為手動斷開
    @Volatile private var reconnectAttempt = 0           // 當前重連次數（僅用於日誌/退避）

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _connectionState = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected())
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    private val _websocketUrl = MutableStateFlow(preferencesManager.getWebSocketUrl())
    val websocketUrl: StateFlow<String> = _websocketUrl.asStateFlow()

    private val _enableWebSocket = MutableStateFlow(preferencesManager.isWebSocketEnabled())
    val enableWebSocket: StateFlow<Boolean> = _enableWebSocket.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var lastPongTime = System.currentTimeMillis()

    init {
        scope.launch {
            combine(_enableWebSocket, _connectionState) { enabled, state ->
                enabled && state is WebSocketState.Connected
            }.collect { online ->
                _isOnline.value = online
                Timber.d("📡 isOnline updated: $online")
            }
        }
    }

    /**
     * 連接 WebSocket（使用者主動呼叫 → 重置手動斷開標記）
     */
    fun connect() {
        if (!_enableWebSocket.value) {
            Timber.w("⚠️ WebSocket is disabled")
            return
        }

        // 🔧 新的連線生命週期 → 重置手動標記
        isManualDisconnect = false

        doConnect()
    }

    /**
     * 🆕 內部真正執行連線（不重置 isManualDisconnect，供重試使用）
     */
    private fun doConnect() {
        if (webSocket != null) {
            Timber.w("⚠️ WebSocket already connected, skipping")
            return
        }

        if (!_enableWebSocket.value) {
            Timber.w("⚠️ WebSocket disabled, abort connect")
            return
        }

        val deviceId = deviceRepository.getDeviceId()
        val baseUrl = _websocketUrl.value
        val url = "$baseUrl?deviceId=$deviceId"

        Timber.d("🔌 Connecting to WebSocket: $url (attempt #${reconnectAttempt + 1})")
        _connectionState.value = WebSocketState.Connecting

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Timber.d("✅ WebSocket connected")
                _connectionState.value = WebSocketState.Connected

                // 🔧 連線成功 → 重置重試計數
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

                // 🔧 非手動斷開 → 自動重連
                scheduleReconnect("closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Timber.e(t, "❌ WebSocket error")
                _connectionState.value = WebSocketState.Error(t.message ?: "Unknown error")
                this@WebSocketManager.webSocket = null
                stopHeartbeat()

                // 🔧 失敗 → 自動重連
                scheduleReconnect("failure: ${t.message}")
            }
        })
    }

    /**
     * 🆕 排程自動重連（指數退避）
     */
    private fun scheduleReconnect(reason: String) {
        // 🛑 手動斷開 / 被禁用 → 不重連
        if (isManualDisconnect) {
            Timber.d("🛑 Manual disconnect flag set, no reconnect ($reason)")
            return
        }
        if (!_enableWebSocket.value) {
            Timber.d("🛑 WebSocket disabled, no reconnect ($reason)")
            return
        }

        // 避免重複排程
        if (reconnectJob?.isActive == true) {
            Timber.d("⏭️ Reconnect already scheduled, skipping")
            return
        }

        // 計算退避延遲：min(initial × factor^n, max)
        val delayMs = (INITIAL_RECONNECT_DELAY *
                Math.pow(BACKOFF_FACTOR, reconnectAttempt.toDouble()))
            .toLong()
            .coerceAtMost(MAX_RECONNECT_DELAY)

        reconnectAttempt++

        Timber.w("🔄 Scheduling reconnect #$reconnectAttempt in ${delayMs}ms (reason: $reason)")

        reconnectJob = scope.launch {
            try {
                delay(delayMs)

                // 延遲期間可能被取消
                if (isManualDisconnect || !_enableWebSocket.value) {
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

    /**
     * 🆕 取消待執行的重連
     */
    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        Timber.d("🛑 Reconnect cancelled & counter reset")
    }

    /**
     * 啟動心跳包
     */
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
            // 🔧 心跳失敗 → 強制觸發重連（關閉當前連線）
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

    /**
     * 🔧 斷開連接（手動）→ 取消所有重試
     */
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

    fun updateWebSocketUrl(url: String) {
        _websocketUrl.value = url
        preferencesManager.setWebSocketUrl(url)
        Timber.d("🔄 WebSocket URL updated: $url")

        if (webSocket != null || reconnectJob?.isActive == true) {
            // 內部切換 → 不算「使用者手動斷開」
            scope.launch {
                Timber.d("🔄 Restarting connection with new URL")
                stopHeartbeat()
                cancelReconnect()
                webSocket?.close(1000, "URL changed")
                webSocket = null
                delay(300)
                // 重新觸發（視為新的連線生命週期）
                isManualDisconnect = false
                doConnect()
            }
        }
    }

    fun setWebSocketEnabled(enabled: Boolean) {
        _enableWebSocket.value = enabled
        preferencesManager.setWebSocketEnabled(enabled)
        Timber.d("🔄 WebSocket enabled: $enabled")

        if (enabled) {
            connect()
        } else {
            disconnect()  // 禁用 = 手動斷開
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
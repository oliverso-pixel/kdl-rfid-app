package com.kdl.rfidinventory.presentation.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.remote.websocket.WebSocketState
import com.kdl.rfidinventory.util.NetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val pendingOperationDao: PendingOperationDao,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    // ⭐ WebSocket 連接狀態
    val webSocketState: StateFlow<WebSocketState> = webSocketManager.connectionState

    // ⭐ WebSocket 是否啟用
    val webSocketEnabled: StateFlow<Boolean> = webSocketManager.enableWebSocket

    // ⭐ 是否在線（WebSocket 啟用且已連接）
    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

    // ⭐ 待同步操作數量
    val pendingOperationsCount: StateFlow<Int> = pendingOperationDao.getPendingCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    // ⭐ 網絡狀態（綜合 isOnline 和 pendingCount）
    val networkState: StateFlow<NetworkState> = combine(
        isOnline,
        pendingOperationsCount
    ) { online, pendingCount ->
        if (online) {
            NetworkState.Connected
        } else {
            NetworkState.Disconnected(pendingCount)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetworkState.Disconnected(0)
    )

    init {
        Timber.d("📱 MainViewModel initialized")
        observeWebSocketState()
    }

    private fun observeWebSocketState() {
        viewModelScope.launch {
            combine(
                webSocketState,
                webSocketEnabled,
                isOnline
            ) { state, enabled, online ->
                Triple(state, enabled, online)
            }.collect { (state, enabled, online) ->
                Timber.d("📡 WebSocket - State: $state, Enabled: $enabled, Online: $online")
            }
        }
    }

    /**
     * 手動同步待處理操作
     */
    fun syncPendingOperations() {
        viewModelScope.launch {
            try {
                val count = pendingOperationsCount.value
                if (count > 0 && isOnline.value) {
                    Timber.d("🔄 Syncing $count pending operations...")
                    // TODO: 實作同步邏輯
                } else if (!isOnline.value) {
                    Timber.w("⚠️ Cannot sync: offline (WebSocket enabled: ${webSocketEnabled.value})")
                } else {
                    Timber.d("✅ No pending operations to sync")
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to sync pending operations")
            }
        }
    }

    /**
     * 手動重新連接 WebSocket
     */
    fun reconnectWebSocket() {
        if (!webSocketEnabled.value) {
            Timber.w("⚠️ WebSocket is disabled, cannot reconnect")
            return
        }

        viewModelScope.launch {
            try {
                Timber.d("🔄 Reconnecting WebSocket...")
                webSocketManager.disconnect()
                kotlinx.coroutines.delay(500)
                webSocketManager.connect()
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to reconnect WebSocket")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 不要在這裡斷開 WebSocket，因為它是 Singleton
        Timber.d("🧹 MainViewModel cleared")
    }
}
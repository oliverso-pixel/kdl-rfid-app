package com.kdl.rfidinventory.presentation.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.User
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.remote.websocket.WebSocketState
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.remote.model.NetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    pendingOperationDao: PendingOperationDao,
    private val webSocketManager: WebSocketManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    val webSocketState: StateFlow<WebSocketState> = webSocketManager.connectionState
    val webSocketEnabled: StateFlow<Boolean> = webSocketManager.enableWebSocket
    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

    val pendingOperationsCount: StateFlow<Int> = pendingOperationDao.getPendingCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

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

    // 🔧 改：直接訂閱 AuthRepository 的 Flow
    val currentUser: StateFlow<User?> = authRepository.currentUserFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

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

    fun showLogoutDialog() {
        _showLogoutDialog.value = true
    }

    fun dismissLogoutDialog() {
        _showLogoutDialog.value = false
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _showLogoutDialog.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("🧹 MainViewModel cleared")
    }
}
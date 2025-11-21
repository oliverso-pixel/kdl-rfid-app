package com.kdl.rfidinventory.presentation.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.util.NetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val pendingOperationDao: PendingOperationDao
) : ViewModel() {

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    init {
        observePendingOperations()
        checkNetworkConnection()
    }

    private fun observePendingOperations() {
        viewModelScope.launch {
            pendingOperationDao.getPendingCount()
                .collect { count ->
                    if (count > 0) {
                        _networkState.value = NetworkState.Disconnected(count)
                    }
                }
        }
    }

    private fun checkNetworkConnection() {
        // Mock 網路檢查
        viewModelScope.launch {
            // 實際應用應該使用 ConnectivityManager
            _networkState.value = NetworkState.Connected
        }
    }

    fun syncPendingOperations() {
        viewModelScope.launch {
            // TODO: 實作同步邏輯
        }
    }
}
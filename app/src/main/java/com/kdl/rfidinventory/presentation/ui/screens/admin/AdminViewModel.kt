package com.kdl.rfidinventory.presentation.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.repository.AdminRepository
import com.kdl.rfidinventory.util.NetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val pendingOperationDao: PendingOperationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    init {
        loadSettings()
        observePendingOperations()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 載入設定
            adminRepository.getSettings()
                .onSuccess { settings ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            settings = settings
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    private fun observePendingOperations() {
        viewModelScope.launch {
            pendingOperationDao.getPendingCount()
                .collect { count ->
                    _uiState.update { it.copy(pendingOperationsCount = count) }
                }
        }
    }

    fun syncPendingOperations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            adminRepository.syncPendingOperations()
                .onSuccess { syncedCount ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            successMessage = "同步成功，共 $syncedCount 筆記錄"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            error = "同步失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    showClearDataDialog = false
                )
            }

            adminRepository.clearAllData()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "所有資料已清除"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "清除失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    fun exportLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }

            adminRepository.exportLogs()
                .onSuccess { filePath ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            successMessage = "日誌已匯出至: $filePath"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            error = "匯出失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            adminRepository.updateServerUrl(url)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            settings = it.settings.copy(serverUrl = url),
                            successMessage = "伺服器地址已更新"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun updateScanTimeout(timeout: Int) {
        viewModelScope.launch {
            adminRepository.updateScanTimeout(timeout)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            settings = it.settings.copy(scanTimeoutSeconds = timeout),
                            successMessage = "掃描逾時已更新"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            adminRepository.toggleAutoSync(enabled)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            settings = it.settings.copy(autoSync = enabled),
                            successMessage = if (enabled) "自動同步已啟用" else "自動同步已停用"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun showClearDataDialog() {
        _uiState.update { it.copy(showClearDataDialog = true) }
    }

    fun dismissClearDataDialog() {
        _uiState.update { it.copy(showClearDataDialog = false) }
    }

    fun showServerUrlDialog() {
        _uiState.update { it.copy(showServerUrlDialog = true) }
    }

    fun dismissServerUrlDialog() {
        _uiState.update { it.copy(showServerUrlDialog = false) }
    }

    fun showScanTimeoutDialog() {
        _uiState.update { it.copy(showScanTimeoutDialog = true) }
    }

    fun dismissScanTimeoutDialog() {
        _uiState.update { it.copy(showScanTimeoutDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

data class AdminUiState(
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isExporting: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val pendingOperationsCount: Int = 0,
    val showClearDataDialog: Boolean = false,
    val showServerUrlDialog: Boolean = false,
    val showScanTimeoutDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

data class AppSettings(
    val serverUrl: String = "http://192.168.1.100:8080",
    val scanTimeoutSeconds: Int = 30,
    val autoSync: Boolean = true,
    val appVersion: String = "1.0.0",
    val databaseVersion: Int = 1
)
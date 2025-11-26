package com.kdl.rfidinventory.presentation.ui.screens.admin

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.remote.websocket.WebSocketState
import com.kdl.rfidinventory.data.repository.AdminRepository
import com.kdl.rfidinventory.data.repository.RegisterBasketResult
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class BasketManagementMode {
    REGISTER,
    QUERY
}

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val adminRepository: AdminRepository,
    private val pendingOperationDao: PendingOperationDao,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _baskets = MutableStateFlow<List<BasketEntity>>(emptyList())
    val baskets: StateFlow<List<BasketEntity>> = _baskets.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _scanMode = MutableStateFlow(ScanMode.SINGLE)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _basketManagementMode = MutableStateFlow(BasketManagementMode.REGISTER)
    val basketManagementMode: StateFlow<BasketManagementMode> = _basketManagementMode.asStateFlow()

    val scanState = scanManager.scanState

    // WebSocket 狀態
    val webSocketState: StateFlow<WebSocketState> = webSocketManager.connectionState
    val webSocketEnabled: StateFlow<Boolean> = webSocketManager.enableWebSocket
    val webSocketUrl: StateFlow<String> = webSocketManager.websocketUrl

    private val processingUids = mutableSetOf<String>()

    init {
        Timber.d("🎯 AdminViewModel init called (instance: ${this.hashCode()})")
        loadSettings()
        observePendingOperations()
        observeLocalBaskets()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            adminRepository.getSettings()
                .onSuccess { settings ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            settings = settings.copy(
                                websocketUrl = webSocketUrl.value,
                                websocketEnabled = webSocketEnabled.value
                            )
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

    private fun observeLocalBaskets() {
        viewModelScope.launch {
            combine(
                adminRepository.getAllLocalBaskets(),
                _searchQuery
            ) { baskets, query ->
                Timber.d("🔄 Baskets updated from database: ${baskets.size} total")

                if (query.isBlank()) {
                    baskets
                } else {
                    baskets.filter {
                        it.uid.contains(query, ignoreCase = true) ||
                                it.productID?.contains(query, ignoreCase = true) == true ||
                                it.productName?.contains(query, ignoreCase = true) == true ||
                                it.batchId?.contains(query, ignoreCase = true) == true
                    }
                }
            }.collect { filteredBaskets ->
                Timber.d("📊 Filtered baskets: ${filteredBaskets.size}")
                _baskets.value = filteredBaskets
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeScanManager() {
        Timber.d("🎯 Initializing ScanManager (ViewModel instance: ${this.hashCode()}, ScanManager instance: ${scanManager.hashCode()})")

        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _scanMode,
            canStartScan = { true }
        )

        Timber.d("✅ ScanManager initialized (ViewModel instance: ${this.hashCode()})")
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                when (result) {
                    is ScanResult.BarcodeScanned -> {
                        handleScannedBarcode(result.barcode)
                    }
                    is ScanResult.RfidScanned -> {
                        handleScannedRfidTag(result.tag.uid)
                    }
                    is ScanResult.ClearListRequested -> {}
                }
            }
        }
    }

    private fun observeScanErrors() {
        viewModelScope.launch {
            scanManager.errors.collect { error ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch {
            Timber.d("🔄 setScanMode called: $mode (current: ${_scanMode.value})")

            if (scanManager.scanState.value.isScanning) {
                Timber.d("⏹️ Stopping scan before mode change")
                scanManager.stopScanning()
                delay(100)
            }

            _scanMode.value = mode
            Timber.d("✅ _scanMode updated to: ${_scanMode.value}")

            delay(50)

            scanManager.changeScanMode(mode)

            Timber.d("🔄 Scan mode change complete")
        }
    }

    private fun handleScannedBarcode(barcode: String) {
        Timber.d("🔍 Barcode scanned in ${_basketManagementMode.value} mode: $barcode")

        when (_basketManagementMode.value) {
            BasketManagementMode.REGISTER -> {
                registerBasket(barcode)
            }
            BasketManagementMode.QUERY -> {
                queryBasket(barcode)
            }
        }
    }

    private fun handleScannedRfidTag(uid: String) {
        Timber.d("🔍 RFID scanned in ${_basketManagementMode.value} mode: $uid")

        when (_basketManagementMode.value) {
            BasketManagementMode.REGISTER -> {
                registerBasket(uid)
            }
            BasketManagementMode.QUERY -> {
                queryBasket(uid)
            }
        }
    }

    fun toggleScan() {
        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            } else {
                scanManager.startRfidScan(_scanMode.value)
            }
        }
    }

    private fun registerBasket(uid: String) {
        if (processingUids.contains(uid)) {
            Timber.d("⚠️ UID $uid is already being processed, skipping...")
            return
        }

        viewModelScope.launch {
            processingUids.add(uid)
            _uiState.update { it.copy(isRegistering = true) }

            val isOnline = _networkState.value is NetworkState.Connected

            adminRepository.registerBasket(uid, isOnline)
                .onSuccess { result ->
                    val message = when (result) {
                        is RegisterBasketResult.RegisteredSuccessfully ->
                            "✅ 籃子已成功註冊: $uid"
                        is RegisterBasketResult.RegisteredOffline ->
                            "📱 籃子已保存到本地（離線）: $uid"
                        is RegisterBasketResult.AlreadyRegisteredOnServer ->
                            "ℹ️ 籃子已在服務器註冊，已同步到本地: $uid"
                        is RegisterBasketResult.AlreadyExistsLocally ->
                            "⚠️ 籃子已存在於本地資料庫: $uid"
                    }

                    _uiState.update {
                        it.copy(
                            isRegistering = false,
                            successMessage = message
                        )
                    }

                    delay(1000)
                    processingUids.remove(uid)

                    if (_scanMode.value == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRegistering = false,
                            error = "註冊失敗: ${error.message}"
                        )
                    }

                    processingUids.remove(uid)

                    if (_scanMode.value == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }
        }
    }

    private fun queryBasket(uid: String) {
        Timber.d("🔍 Querying basket: $uid")

        _searchQuery.value = uid

        _uiState.update {
            it.copy(successMessage = "已搜索籃子: ${uid.takeLast(8)}")
        }

        if (_scanMode.value == ScanMode.SINGLE) {
            scanManager.stopScanning()
        }
    }

    fun setBasketManagementMode(mode: BasketManagementMode) {
        _basketManagementMode.value = mode
        Timber.d("📋 Basket management mode changed to: $mode")
    }

    fun searchBaskets(query: String) {
        _searchQuery.value = query
    }

    fun deleteBasket(uid: String) {
        viewModelScope.launch {
            adminRepository.deleteLocalBasket(uid)
                .onSuccess {
                    _uiState.update {
                        it.copy(successMessage = "已刪除籃子: $uid")
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "刪除失敗: ${error.message}")
                    }
                }
        }
    }

    fun deleteBatch(uids: List<String>) {
        viewModelScope.launch {
            var successCount = 0
            var failCount = 0

            uids.forEach { uid ->
                adminRepository.deleteLocalBasket(uid)
                    .onSuccess { successCount++ }
                    .onFailure { failCount++ }
            }

            _uiState.update {
                it.copy(
                    successMessage = if (failCount == 0) {
                        "已刪除 $successCount 個籃子"
                    } else {
                        "刪除完成：成功 $successCount 個，失敗 $failCount 個"
                    }
                )
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

    fun updateWebSocketUrl(url: String) {
        webSocketManager.updateWebSocketUrl(url)
        _uiState.update {
            it.copy(
                settings = it.settings.copy(websocketUrl = url),
                successMessage = "WebSocket 地址已更新"
            )
        }
    }

    fun toggleWebSocket(enabled: Boolean) {
        webSocketManager.setWebSocketEnabled(enabled)
        _uiState.update {
            it.copy(
                settings = it.settings.copy(websocketEnabled = enabled),
                successMessage = if (enabled) "WebSocket 已啟用" else "WebSocket 已禁用"
            )
        }
    }

    fun reconnectWebSocket() {
        viewModelScope.launch {
            webSocketManager.disconnect()
            delay(500)
            webSocketManager.connect()
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

    fun showWebSocketUrlDialog() {
        _uiState.update { it.copy(showWebSocketUrlDialog = true) }
    }

    fun dismissWebSocketUrlDialog() {
        _uiState.update { it.copy(showWebSocketUrlDialog = false) }
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

    override fun onCleared() {
        super.onCleared()
        Timber.d("🧹 AdminViewModel onCleared (instance: ${this.hashCode()})")
        scanManager.cleanup()
        processingUids.clear()
    }
}

data class AdminUiState(
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isExporting: Boolean = false,
    val isScanning: Boolean = false,
    val isRegistering: Boolean = false,
    val isSearching: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val pendingOperationsCount: Int = 0,
    val showClearDataDialog: Boolean = false,
    val showServerUrlDialog: Boolean = false,
    val showWebSocketUrlDialog: Boolean = false,
    val showScanTimeoutDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

data class AppSettings(
    val serverUrl: String = "http://192.168.1.100:8080",
    val websocketUrl: String = "ws://192.168.1.100/ws",
    val websocketEnabled: Boolean = false,
    val scanTimeoutSeconds: Int = 30,
    val autoSync: Boolean = true,
    val appVersion: String = "1.0.0",
    val databaseVersion: Int = 1
)
package com.kdl.rfidinventory.presentation.ui.screens.admin

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.BasketEntity
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
    REGISTER,  // 登記新籃子
    QUERY      // 查詢現有籃子
}

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val adminRepository: AdminRepository,
    private val pendingOperationDao: PendingOperationDao,
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

    // 籃子管理模式
    private val _basketManagementMode = MutableStateFlow(BasketManagementMode.REGISTER)
    val basketManagementMode: StateFlow<BasketManagementMode> = _basketManagementMode.asStateFlow()

    val scanState = scanManager.scanState

    // 添加正在處理的 UID 集合
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

    private fun observeLocalBaskets() {
        viewModelScope.launch {
            combine(
                adminRepository.getAllLocalBaskets(),
                _searchQuery
            ) { baskets, query ->
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

            // 先停止掃描
            if (scanManager.scanState.value.isScanning) {
                Timber.d("⏹️ Stopping scan before mode change")
                scanManager.stopScanning()
                delay(100)
            }

            // 更新本地狀態
            _scanMode.value = mode
            Timber.d("✅ _scanMode updated to: ${_scanMode.value}")

            // 延遲確保 StateFlow 更新
            delay(50)

            // 通知 ScanManager
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
        // 檢查是否正在處理這個 UID
        if (processingUids.contains(uid)) {
            Timber.d("⚠️ UID $uid is already being processed, skipping...")
            return
        }

        viewModelScope.launch {
            // 標記為正在處理
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

                    // 延遲移除（避免重複掃描同一個籃子）
                    delay(1000) // 1秒內不再接受同一個 UID
                    processingUids.remove(uid)

                    // 單次掃描模式：掃描成功後自動停止
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

                    // 失敗時立即移除
                    processingUids.remove(uid)

                    // 註冊失敗時，單次模式也停止掃描
                    if (_scanMode.value == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }
        }
    }

    private fun queryBasket(uid: String) {
        Timber.d("🔍 Querying basket: $uid")

        // 自動填入搜索框
        _searchQuery.value = uid

        // 提示用戶
        _uiState.update {
            it.copy(successMessage = "已搜索籃子: ${uid.takeLast(8)}")
        }

        // 單次模式自動停止
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
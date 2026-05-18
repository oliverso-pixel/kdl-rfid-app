package com.kdl.rfidinventory.presentation.ui.screens.admin

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.device.DeviceInfoProvider
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.datastore.SettingsDataStore
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.remote.dto.request.BasketUpdateItemDto
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.model.NetworkState
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.remote.websocket.WebSocketState
import com.kdl.rfidinventory.data.repository.AdminRepository
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.repository.BasketRepository
import com.kdl.rfidinventory.domain.manager.ScanManager
import com.kdl.rfidinventory.domain.manager.ScanResult
import com.kdl.rfidinventory.util.*
import com.kdl.rfidinventory.domain.manager.rfid.RFIDManager
import com.kdl.rfidinventory.domain.model.ScanMode
import com.ubx.usdk.bean.RfidParameter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class BasketManagementMode {
    REGISTER,
    QUERY,
    LOCAL
}

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class AdminViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val basketRepository: BasketRepository,
    private val authRepository: AuthRepository,
    private val adminRepository: AdminRepository,
    private val pendingOperationDao: PendingOperationDao,
    private val webSocketManager: WebSocketManager,
    private val rfidManager: RFIDManager,
    private val settingsDataStore: SettingsDataStore,  // 🔧 改這裡
    private val deviceInfoProvider: DeviceInfoProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

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

    val webSocketState: StateFlow<WebSocketState> = webSocketManager.connectionState
    val webSocketEnabled: StateFlow<Boolean> = webSocketManager.enableWebSocket
    val webSocketUrl: StateFlow<String> = webSocketManager.websocketUrl

    private val processingUids = mutableSetOf<String>()

    private val _queriedBasket = MutableStateFlow<Basket?>(null)
    val queriedBasket = _queriedBasket.asStateFlow()

    init {
        Timber.d("🎯 AdminViewModel init called (instance: ${this.hashCode()})")
        loadSettings()
        loadRFIDInfo()
        loadDeviceName()
        observePendingOperations()
        observeLocalBaskets()
        observeSettingsChanges()

        initializeScanManager()
        observeScanResults()
        observeScanErrors()
    }

    /**
     * 🆕 訂閱 DataStore flow → 自動同步到 uiState.settings
     */
    private fun observeSettingsChanges() {
        viewModelScope.launch {
            combine(
                webSocketManager.websocketUrl,
                webSocketManager.enableWebSocket,
                settingsDataStore.maxBasketsPerScan,
                settingsDataStore.scanTimeout,
                settingsDataStore.autoSync,
                settingsDataStore.serverUrl
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                AppSettings(
                    websocketUrl = values[0] as String,
                    websocketEnabled = values[1] as Boolean,
                    maxBasketsPerScan = values[2] as Int,
                    scanTimeoutSeconds = values[3] as Int,
                    autoSync = values[4] as Boolean,
                    serverUrl = values[5] as String
                )
            }.collect { newSettings ->
                _uiState.update { current ->
                    // 保留從 adminRepository 取得的 appVersion / databaseVersion
                    current.copy(
                        settings = newSettings.copy(
                            appVersion = current.settings.appVersion,
                            databaseVersion = current.settings.databaseVersion
                        )
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            adminRepository.getSettings()
                .onSuccess { settings ->
                    // 🔧 suspend 讀取
                    val maxPerScan = settingsDataStore.getMaxBasketsPerScan()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            settings = settings.copy(
                                websocketUrl = webSocketUrl.value,
                                websocketEnabled = webSocketEnabled.value,
                                maxBasketsPerScan = maxPerScan
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
        }
    }

    fun updateMaxBasketsPerScan(value: Int) {
        viewModelScope.launch {
            settingsDataStore.setMaxBasketsPerScan(value)   // 🔧 suspend
            _uiState.update {
                it.copy(successMessage = "每次掃描上限已更新為 $value")
            }
        }
    }

    fun showMaxBasketsPerScanDialog() {
        _uiState.update { it.copy(showMaxBasketsDialog = true) }
    }
    fun dismissMaxBasketsPerScanDialog() {
        _uiState.update { it.copy(showMaxBasketsDialog = false) }
    }

    private fun loadRFIDInfo() {
        viewModelScope.launch {
            try {
                val firmwareVersion = rfidManager.getFirmwareVersion()
                val currentPower = rfidManager.getPower()
                val isConnected = rfidManager.isConnected()

                _uiState.update {
                    it.copy(
                        rfidInfo = RFIDInfo(
                            firmwareVersion = firmwareVersion ?: "未知",
                            power = currentPower ?: 0,
                            isConnected = isConnected
                        )
                    )
                }
                Timber.d("✅ RFID Info loaded: version=$firmwareVersion, power=$currentPower, connected=$isConnected")
            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to load RFID info")
                _uiState.update { it.copy(error = "獲取 RFID 信息失敗: ${e.message}") }
            }
        }
    }

    fun setPower(power: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSettingPower = true) }

            rfidManager.setPower(power)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSettingPower = false,
                            rfidInfo = it.rfidInfo.copy(power = power),
                            successMessage = "功率設置成功: ${power} dBm"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSettingPower = false, error = "功率設置失败: ${error.message}")
                    }
                }
        }
    }

    fun setFrequencyRegion(region: Int, minFreq: Int, maxFreq: Int) {
        viewModelScope.launch {
            rfidManager.setFrequencyRegion(region, minFreq, maxFreq)
                .onSuccess { /* ... */ }
        }
    }

    fun setSession(session: Int) {
        viewModelScope.launch {
            val param = RfidParameter().apply { Session = session }
            // 設置參數...
        }
    }

    fun setQValue(qValue: Int) {
        viewModelScope.launch {
            val param = RfidParameter().apply { QValue = qValue }
            // 設置參數...
        }
    }

    fun refreshRFIDInfo() { loadRFIDInfo() }

    fun showPowerDialog() { _uiState.update { it.copy(showPowerDialog = true) } }
    fun dismissPowerDialog() { _uiState.update { it.copy(showPowerDialog = false) } }

    private fun loadDeviceName() {
        val name = deviceInfoProvider.getDeviceName()
        _uiState.update { it.copy(deviceName = name) }
    }

    fun showDeviceNameDialog() { _uiState.update { it.copy(showDeviceNameDialog = true) } }
    fun dismissDeviceNameDialog() { _uiState.update { it.copy(showDeviceNameDialog = false) } }

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                settingsDataStore.setCustomDeviceName(name)  // 🔧 suspend
                _uiState.update {
                    it.copy(deviceName = name, successMessage = "設備名稱已更新")
                }
            }
        }
    }

    private fun observePendingOperations() {
        viewModelScope.launch {
            pendingOperationDao.getPendingCount().collect { count ->
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
                                it.productId?.contains(query, ignoreCase = true) == true ||
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
        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _scanMode,
            canStartScan = {
                when (_basketManagementMode.value) {
                    BasketManagementMode.REGISTER -> true
                    BasketManagementMode.QUERY -> true
                    BasketManagementMode.LOCAL -> false
                }
            }
        )
        Timber.d("✅ ScanManager initialized (ViewModel instance: ${this.hashCode()})")
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                val currentMode = _basketManagementMode.value
                Timber.d("📦 Scan Result: ${result::class.simpleName}, Mode: $currentMode")

                when (result) {
                    is ScanResult.BarcodeScanned -> handleScannedBarcode(result.barcode, currentMode)
                    is ScanResult.RfidScanned -> handleScannedRfidTag(result.tag.uid, currentMode)
                    is ScanResult.ClearListRequested -> {
                        if (currentMode == BasketManagementMode.REGISTER) clearScannedUids()
                    }
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
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
                delay(100)
            }
            _scanMode.value = mode
            scanManager.changeScanMode(mode)
        }
    }

    private fun handleScannedBarcode(barcode: String, mode: BasketManagementMode) {
        when (mode) {
            BasketManagementMode.REGISTER -> addToScannedList(barcode)
            BasketManagementMode.QUERY -> fetchBasketDetail(barcode)
            BasketManagementMode.LOCAL -> Timber.d("⚠️ Local mode: Ignoring scan")
        }
    }

    private fun handleScannedRfidTag(uid: String, mode: BasketManagementMode) {
        when (mode) {
            BasketManagementMode.REGISTER -> addToScannedList(uid)
            BasketManagementMode.QUERY -> fetchBasketDetail(uid)
            BasketManagementMode.LOCAL -> Timber.d("⚠️ Local mode: Ignoring scan")
        }
    }

    fun toggleScan() {
        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            } else {
                when (_basketManagementMode.value) {
                    BasketManagementMode.REGISTER -> scanManager.startRfidScan(_scanMode.value)
                    BasketManagementMode.QUERY -> {
                        if (_scanMode.value != ScanMode.SINGLE) {
                            setScanMode(ScanMode.SINGLE)
                            delay(50)
                        }
                        scanManager.startRfidScan(ScanMode.SINGLE)
                    }
                    BasketManagementMode.LOCAL -> {
                        _uiState.update { it.copy(error = "本地管理模式不支援掃描") }
                    }
                }
            }
        }
    }

    private fun addToScannedList(uid: String) {
        _uiState.update { state ->
            if (state.scannedUids.contains(uid)) {
                state.copy(successMessage = "⚠️ UID 已在列表中: ${uid.takeLast(8)}")
            } else {
                val newList = state.scannedUids + uid
                state.copy(
                    scannedUids = newList,
                    successMessage = "✅ 已加入: ${uid.takeLast(8)} (共 ${newList.size} 個)"
                )
            }
        }
        if (_scanMode.value == ScanMode.SINGLE) {
            scanManager.stopScanning()
        }
    }

    fun submitRegistration() {
        val uids = _uiState.value.scannedUids
        if (uids.isEmpty()) {
            _uiState.update { it.copy(error = "請先掃描至少一個籃子") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRegistering = true) }

            basketRepository.bulkRegisterBaskets(
                uids = uids,
                isOnline = _networkState.value is NetworkState.Connected
            ).onSuccess { result ->
                val msg = if (result.isOffline) {
                    "📱 已離線保存 ${result.successCount} 個籃子"
                } else {
                    "✅ 登記完成: 成功 ${result.successCount} / 總數 ${result.totalCount}"
                }
                _uiState.update {
                    it.copy(isRegistering = false, successMessage = msg, scannedUids = emptyList())
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isRegistering = false, error = "提交失敗: ${e.message}")
                }
            }
        }
    }

    fun clearScannedUids() {
        _uiState.update {
            it.copy(scannedUids = emptyList(), successMessage = "已清空暫存列表")
        }
    }

    fun removeScannedUid(uid: String) {
        _uiState.update { state ->
            state.copy(
                scannedUids = state.scannedUids.filter { it != uid },
                successMessage = "已移除: ${uid.takeLast(8)}"
            )
        }
    }

    fun fetchBasketDetail(uid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            basketRepository.fetchBasket(
                uid = uid,
                isOnline = _networkState.value is NetworkState.Connected
            ).onSuccess { basket ->
                _queriedBasket.value = basket
                _uiState.update {
                    it.copy(isLoading = false, successMessage = "✅ 查詢成功: ${uid.takeLast(8)}")
                }
                if (_scanMode.value == ScanMode.SINGLE) scanManager.stopScanning()
            }.onFailure { error ->
                _queriedBasket.value = null
                _uiState.update {
                    it.copy(isLoading = false, error = "查詢失敗: ${error.message}")
                }
                if (_scanMode.value == ScanMode.SINGLE) scanManager.stopScanning()
            }
        }
    }

    fun updateBasketInfo(
        basket: Basket,
        newStatus: String?,
        newWarehouseId: String?,
        newDescription: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 🔧 suspend 呼叫
            val currentUser = authRepository.getCurrentUser()?.username ?: "admin"

            val itemDto = BasketUpdateItemDto(
                rfid = basket.uid,
                status = newStatus,
                warehouseId = newWarehouseId,
                description = newDescription,
                quantity = basket.quantity
            )

            basketRepository.updateBasket(
                updateType = "AdminUpdate",
                commonData = CommonDataDto(updateBy = currentUser),
                items = listOf(itemDto),
                isOnline = _networkState.value is NetworkState.Connected
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, successMessage = "✅ 更新成功") }
                fetchBasketDetail(basket.uid)
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = "更新失敗: ${error.message}") }
            }
        }
    }

    fun setBasketManagementMode(mode: BasketManagementMode) {
        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
                delay(200)
            }

            _basketManagementMode.value = mode

            _uiState.update {
                it.copy(scannedUids = emptyList(), error = null, successMessage = null)
            }
            _queriedBasket.value = null

            when (mode) {
                BasketManagementMode.REGISTER -> setScanMode(ScanMode.CONTINUOUS)
                BasketManagementMode.QUERY -> setScanMode(ScanMode.SINGLE)
                BasketManagementMode.LOCAL -> setScanMode(ScanMode.SINGLE)
            }
        }
    }

    fun searchBaskets(query: String) {
        _searchQuery.value = query
    }

    fun deleteBasket(uid: String) {
        viewModelScope.launch {
            adminRepository.deleteLocalBasket(uid)
                .onSuccess { _uiState.update { it.copy(successMessage = "已刪除籃子: $uid") } }
                .onFailure { error -> _uiState.update { it.copy(error = "刪除失敗: ${error.message}") } }
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
                    successMessage = if (failCount == 0) "已刪除 $successCount 個籃子"
                    else "刪除完成：成功 $successCount 個，失敗 $failCount 個"
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
                        it.copy(isSyncing = false, successMessage = "同步成功，共 $syncedCount 筆記錄")
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSyncing = false, error = "同步失敗: ${error.message}")
                    }
                }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showClearDataDialog = false) }
            adminRepository.clearAllData()
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, successMessage = "所有資料已清除") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = "清除失敗: ${error.message}") }
                }
        }
    }

    fun exportLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            adminRepository.exportLogs()
                .onSuccess { filePath ->
                    _uiState.update {
                        it.copy(isExporting = false, successMessage = "日誌已匯出至: $filePath")
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isExporting = false, error = "匯出失敗: ${error.message}")
                    }
                }
        }
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            // 🔧 寫入 DataStore,同時告知 AdminRepository（如果它還有其他邏輯）
            settingsDataStore.setServerUrl(url)
            adminRepository.updateServerUrl(url)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "伺服器地址已更新") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun updateWebSocketUrl(url: String) {
        webSocketManager.updateWebSocketUrl(url)
        // 🔧 不再手動 copy settings,由 observeSettingsChanges() 自動更新
        _uiState.update { it.copy(successMessage = "WebSocket 地址已更新") }
    }

    fun toggleWebSocket(enabled: Boolean) {
        webSocketManager.setWebSocketEnabled(enabled)
        _uiState.update {
            it.copy(successMessage = if (enabled) "WebSocket 已啟用" else "WebSocket 已禁用")
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
            settingsDataStore.setScanTimeout(timeout)    // 🔧 直接寫 DataStore
            adminRepository.updateScanTimeout(timeout)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "掃描逾時已更新") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoSync(enabled)       // 🔧 直接寫 DataStore
            adminRepository.toggleAutoSync(enabled)
                .onSuccess {
                    _uiState.update {
                        it.copy(successMessage = if (enabled) "自動同步已啟用" else "自動同步已停用")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun showClearDataDialog() { _uiState.update { it.copy(showClearDataDialog = true) } }
    fun dismissClearDataDialog() { _uiState.update { it.copy(showClearDataDialog = false) } }
    fun showServerUrlDialog() { _uiState.update { it.copy(showServerUrlDialog = true) } }
    fun dismissServerUrlDialog() { _uiState.update { it.copy(showServerUrlDialog = false) } }
    fun showWebSocketUrlDialog() { _uiState.update { it.copy(showWebSocketUrlDialog = true) } }
    fun dismissWebSocketUrlDialog() { _uiState.update { it.copy(showWebSocketUrlDialog = false) } }
    fun showScanTimeoutDialog() { _uiState.update { it.copy(showScanTimeoutDialog = true) } }
    fun dismissScanTimeoutDialog() { _uiState.update { it.copy(showScanTimeoutDialog = false) } }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }

    override fun onCleared() {
        super.onCleared()
        Timber.d("🧹 AdminViewModel onCleared")
        scanManager.cleanup()
        processingUids.clear()
    }
}

data class RFIDInfo(
    val firmwareVersion: String = "未知",
    val power: Int = 0,
    val isConnected: Boolean = false
)

data class AdminUiState(
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isExporting: Boolean = false,
    val isScanning: Boolean = false,
    val isRegistering: Boolean = false,
    val isSearching: Boolean = false,
    val isSettingPower: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val pendingOperationsCount: Int = 0,
    val rfidInfo: RFIDInfo = RFIDInfo(),
    val showClearDataDialog: Boolean = false,
    val showServerUrlDialog: Boolean = false,
    val showWebSocketUrlDialog: Boolean = false,
    val showScanTimeoutDialog: Boolean = false,
    val showPowerDialog: Boolean = false,
    val deviceName: String = "",
    val showDeviceNameDialog: Boolean = false,
    val scannedUids: List<String> = emptyList(),
    val showMaxBasketsDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

data class AppSettings(
    val serverUrl: String = "http://192.9.204.144:8000",
    val websocketUrl: String = "ws://192.9.204.144:3001/ws",
    val websocketEnabled: Boolean = true,
    val scanTimeoutSeconds: Int = 30,
    val maxBasketsPerScan: Int = 5,
    val autoSync: Boolean = false,
    val appVersion: String = "1.0.0",
    val databaseVersion: Int = 1
)
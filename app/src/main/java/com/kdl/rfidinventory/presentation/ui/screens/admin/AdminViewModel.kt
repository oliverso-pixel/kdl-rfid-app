package com.kdl.rfidinventory.presentation.ui.screens.admin

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.device.DeviceInfoProvider
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.local.preferences.PreferencesManager
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.remote.dto.request.BasketUpdateItemDto
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.remote.websocket.WebSocketState
import com.kdl.rfidinventory.data.repository.AdminRepository
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.repository.BasketRepository
import com.kdl.rfidinventory.util.*
import com.kdl.rfidinventory.domain.manager.rfid.RFIDManager
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
    private val preferencesManager: PreferencesManager,
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

    // WebSocket 狀態
    val webSocketState: StateFlow<WebSocketState> = webSocketManager.connectionState
    val webSocketEnabled: StateFlow<Boolean> = webSocketManager.enableWebSocket
    val webSocketUrl: StateFlow<String> = webSocketManager.websocketUrl

    private val processingUids = mutableSetOf<String>()

    // 登記模式：暫存的 UID 列表
//    private val _scannedUids = MutableStateFlow<Set<String>>(emptySet())
//    private val _scannedUidsVersion = MutableStateFlow(0)
//    val scannedUids = _scannedUids.asStateFlow()
//    val scannedUidsVersion = _scannedUidsVersion.asStateFlow()

//    private val _uiRefreshTrigger = MutableStateFlow(0)
//    val uiRefreshTrigger = _uiRefreshTrigger.asStateFlow()

    // 查詢模式：當前查詢到的籃子
    private val _queriedBasket = MutableStateFlow<Basket?>(null)
    val queriedBasket = _queriedBasket.asStateFlow()

    init {
        Timber.d("🎯 AdminViewModel init called (instance: ${this.hashCode()})")
        loadSettings()
        loadRFIDInfo()
        loadDeviceName()
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

    /**
     * 加載 RFID 模塊信息
     */
    private fun loadRFIDInfo() {
        viewModelScope.launch {
            try {
                // 獲取固件版本
                val firmwareVersion = rfidManager.getFirmwareVersion()

                // 獲取當前功率
                val currentPower = rfidManager.getPower()

                // 檢查連接狀態
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
                _uiState.update {
                    it.copy(error = "獲取 RFID 信息失敗: ${e.message}")
                }
            }
        }
    }

    /**
     * 設置功率
     */
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
                        it.copy(
                            isSettingPower = false,
                            error = "功率設置失败: ${error.message}"
                        )
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
            val param = RfidParameter().apply {
                Session = session
            }
            // 設置參數...
        }
    }

    fun setQValue(qValue: Int) {
        viewModelScope.launch {
            val param = RfidParameter().apply {
                QValue = qValue
            }
            // 設置參數...
        }
    }

    fun refreshRFIDInfo() {
        loadRFIDInfo()
    }

    fun showPowerDialog() {
        _uiState.update { it.copy(showPowerDialog = true) }
    }

    fun dismissPowerDialog() {
        _uiState.update { it.copy(showPowerDialog = false) }
    }

    /**
     * End of RFID
     */

    private fun loadDeviceName() {
        val name = deviceInfoProvider.getDeviceName()
        _uiState.update { it.copy(deviceName = name) }
    }

    fun showDeviceNameDialog() {
        _uiState.update { it.copy(showDeviceNameDialog = true) }
    }

    fun dismissDeviceNameDialog() {
        _uiState.update { it.copy(showDeviceNameDialog = false) }
    }

    fun updateDeviceName(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                preferencesManager.setCustomDeviceName(name)
                _uiState.update {
                    it.copy(
                        deviceName = name,
                        successMessage = "設備名稱已更新"
                    )
                }
                // 如果需要，這裡可以觸發重新註冊設備 API
            }
        }
    }

    private fun observePendingOperations() {
        viewModelScope.launch {
            pendingOperationDao.getPendingCount().collect { count -> _uiState.update { it.copy(pendingOperationsCount = count) } }
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
                    BasketManagementMode.REGISTER -> true // 登記模式允許掃描
                    BasketManagementMode.QUERY -> true    // 查詢模式允許掃描
                    BasketManagementMode.LOCAL -> false   // 本地模式禁止掃描
                }
            }
        )

        Timber.d("✅ ScanManager initialized (ViewModel instance: ${this.hashCode()})")
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                // ✅ 每次收到結果時，立即讀取最新模式（不依賴 combine）
                val currentMode = _basketManagementMode.value

                Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Timber.d("📦 Scan Result Received")
                Timber.d("  ├─ Result Type: ${result::class.simpleName}")
                Timber.d("  ├─ Current Mode from StateFlow: $currentMode")
                Timber.d("  └─ Timestamp: ${System.currentTimeMillis()}")
                Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                when (result) {
                    is ScanResult.BarcodeScanned -> {
                        handleScannedBarcode(result.barcode, currentMode)
                    }
                    is ScanResult.RfidScanned -> {
                        handleScannedRfidTag(result.tag.uid, currentMode)
                    }
                    is ScanResult.ClearListRequested -> {
                        if (currentMode == BasketManagementMode.REGISTER) {
                            clearScannedUids()
                        }
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

    /**
     * 處理條碼掃描
     */
    private fun handleScannedBarcode(barcode: String, mode: BasketManagementMode) {
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.d("🔍 handleScannedBarcode START")
        Timber.d("  ├─ Barcode: $barcode")
        Timber.d("  ├─ Mode Parameter: $mode")
        Timber.d("  └─ StateFlow Mode: ${_basketManagementMode.value}")

        when (mode) {
            BasketManagementMode.REGISTER -> {
                Timber.run { d("📝 Executing: Register Mode -> addToScannedList") }
                addToScannedList(barcode)
            }
            BasketManagementMode.QUERY -> {
                Timber.d("🔍 Executing: Query Mode -> fetchBasketDetail")
                fetchBasketDetail(barcode)
            }
            BasketManagementMode.LOCAL -> {
                Timber.d("⚠️ Local mode: Ignoring scan")
            }
        }

        Timber.d("🔍 handleScannedBarcode END")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    /**
     * 處理 RFID 掃描
     */
    private fun handleScannedRfidTag(uid: String, mode: BasketManagementMode) {
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.d("🔍 handleScannedRfidTag START")
        Timber.d("  ├─ UID: $uid")
        Timber.d("  ├─ Mode Parameter: $mode")
        Timber.d("  └─ StateFlow Mode: ${_basketManagementMode.value}")

        when (mode) {
            BasketManagementMode.REGISTER -> {
                Timber.d("📝 Executing: Register Mode -> addToScannedList")
                addToScannedList(uid)
            }
            BasketManagementMode.QUERY -> {
                Timber.d("🔍 Executing: Query Mode -> fetchBasketDetail")
                fetchBasketDetail(uid)
            }
            BasketManagementMode.LOCAL -> {
                Timber.d("⚠️ Local mode: Ignoring scan")
            }
        }

        Timber.d("🔍 handleScannedRfidTag END")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    fun toggleScan() {
        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            } else {
                // 根據模式決定掃描方式
                when (_basketManagementMode.value) {
                    BasketManagementMode.REGISTER -> {
                        // 登記模式：使用當前設定的掃描模式
                        scanManager.startRfidScan(_scanMode.value)
                    }
                    BasketManagementMode.QUERY -> {
                        // 查詢模式：強制使用 SINGLE 模式
                        if (_scanMode.value != ScanMode.SINGLE) {
                            setScanMode(ScanMode.SINGLE)
                            delay(50)
                        }
                        scanManager.startRfidScan(ScanMode.SINGLE)
                    }
                    BasketManagementMode.LOCAL -> {
                        // 本地模式：不允許掃描
                        _uiState.update { it.copy(error = "本地管理模式不支援掃描") }
                    }
                }
            }
        }
    }

    /**
     * [登記模式] 加入暫存列表
     */
    private fun addToScannedList(uid: String) {
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Timber.d("📝 [addToScannedList] START")
        Timber.d("  ├─ UID: $uid")
        Timber.d("  ├─ Current List Size: ${_uiState.value.scannedUids.size}")

        _uiState.update { state ->
            if (state.scannedUids.contains(uid)) {
                Timber.d("⚠️ UID already exists")
                state.copy(
                    successMessage = "⚠️ UID 已在列表中: ${uid.takeLast(8)}"
                )
            } else {
                val newList = state.scannedUids + uid
                Timber.d("📦 Updating list: ${state.scannedUids.size} -> ${newList.size}")
                Timber.d("✅ List updated to size: ${newList.size}")

                state.copy(
                    scannedUids = newList,  // ✅ 創建新 List
                    successMessage = "✅ 已加入: ${uid.takeLast(8)} (共 ${newList.size} 個)"
                )
            }
        }

        if (_scanMode.value == ScanMode.SINGLE) {
            scanManager.stopScanning()
        }

        Timber.d("📝 [addToScannedList] END")
        Timber.d("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    fun submitRegistration() {
        val uids = _uiState.value.scannedUids  // ✅ 直接從 uiState 讀取
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
                    it.copy(
                        isRegistering = false,
                        successMessage = msg,
                        scannedUids = emptyList()  // ✅ 成功後清空
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isRegistering = false,
                        error = "提交失敗: ${e.message}"
                    )
                }
            }
        }
    }

    // [登記] 清除暫存
    fun clearScannedUids() {
        _uiState.update {
            it.copy(
                scannedUids = emptyList(),
                successMessage = "已清空暫存列表"
            )
        }
    }

    // [登記] 移除單個
    fun removeScannedUid(uid: String) {
        _uiState.update { state ->
            state.copy(
                scannedUids = state.scannedUids.filter { it != uid },
                successMessage = "已移除: ${uid.takeLast(8)}"
            )
        }
    }

    // [查詢] 獲取詳情
    fun fetchBasketDetail(uid: String) {
        Timber.d("🔍 fetchBasketDetail called: $uid")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            Timber.d("🔄 Loading state set to true")

            basketRepository.fetchBasket(
                uid = uid,
                isOnline = _networkState.value is NetworkState.Connected
            ).onSuccess { basket ->
                Timber.d("✅ Basket fetched successfully: ${basket.uid}")
                _queriedBasket.value = basket
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "✅ 查詢成功: ${uid.takeLast(8)}"
                    )
                }

                if (_scanMode.value == ScanMode.SINGLE) {
                    Timber.d("🛑 Single mode: stopping scan after query")
                    scanManager.stopScanning()
                }
            }.onFailure { error ->
                Timber.e(error, "❌ Failed to fetch basket: $uid")
                _queriedBasket.value = null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "查詢失敗: ${error.message}"
                    )
                }

                if (_scanMode.value == ScanMode.SINGLE) {
                    Timber.d("🛑 Single mode: stopping scan after error")
                    scanManager.stopScanning()
                }
            }
        }
    }

    // [查詢] 更新籃子
    fun updateBasketInfo(
        basket: Basket,
        newStatus: String?,
        newWarehouseId: String?,
        newDescription: String?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

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
            Timber.d("📋 setBasketManagementMode called: $mode (current: ${_basketManagementMode.value})")

            if (scanManager.scanState.value.isScanning) {
                Timber.d("🛑 Stopping scan before mode change")
                scanManager.stopScanning()
                delay(200)
            }

            _basketManagementMode.value = mode
            Timber.d("✅ Mode updated to: ${_basketManagementMode.value}")

            _uiState.update {
                it.copy(
                    scannedUids = emptyList(),
                    error = null,
                    successMessage = null
                )
            }
            _queriedBasket.value = null
            Timber.d("🧹 Cleared temporary data")

            when (mode) {
                BasketManagementMode.REGISTER -> {
                    Timber.d("🔄 Register mode: setting scan mode to CONTINUOUS")
                    setScanMode(ScanMode.CONTINUOUS)
                }
                BasketManagementMode.QUERY -> {
                    Timber.d("🔄 Query mode: setting scan mode to SINGLE")
                    setScanMode(ScanMode.SINGLE)
                }
                BasketManagementMode.LOCAL -> {
                    Timber.d("🔄 Local mode: setting scan mode to SINGLE (no scanning)")
                    setScanMode(ScanMode.SINGLE)
                }
            }

            Timber.d("✅ Mode change complete: ${_basketManagementMode.value}")
        }
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
    val error: String? = null,
    val successMessage: String? = null
)

data class AppSettings(
    val serverUrl: String = "http://192.9.204.144:8000",
    val websocketUrl: String = "ws://192.9.204.144:3001/ws",
    val websocketEnabled: Boolean = false,
    val scanTimeoutSeconds: Int = 30,
    val autoSync: Boolean = true,
    val appVersion: String = "1.0.0",
    val databaseVersion: Int = 1
)
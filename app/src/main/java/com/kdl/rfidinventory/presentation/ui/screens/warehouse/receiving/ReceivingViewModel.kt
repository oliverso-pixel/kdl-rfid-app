package com.kdl.rfidinventory.presentation.ui.screens.warehouse.receiving

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.datastore.SettingsDataStore
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Warehouse
import com.kdl.rfidinventory.data.model.getBasketStatusText
import com.kdl.rfidinventory.data.remote.dto.request.BasketUpdateItemDto
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.model.NetworkState
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.data.repository.BasketRepository
import com.kdl.rfidinventory.domain.manager.ScanManager
import com.kdl.rfidinventory.domain.manager.ScanResult
import com.kdl.rfidinventory.domain.model.ScanContext
import com.kdl.rfidinventory.domain.model.ScanMode
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ReceivingViewModel @Inject constructor(
    private val scanManager: ScanManager,
    webSocketManager: WebSocketManager,
    pendingOperationDao: PendingOperationDao,
    private val authRepository: AuthRepository,
    private val warehouseRepository: WarehouseRepository,
    private val basketRepository: BasketRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceivingUiState())
    val uiState: StateFlow<ReceivingUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

    val networkState: StateFlow<NetworkState> = combine(
        isOnline,
        pendingOperationDao.getPendingCount()
    ) { online, pendingCount ->
        if (online) NetworkState.Connected
        else NetworkState.Disconnected(pendingCount)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetworkState.Disconnected(0)
    )

    val scanState = scanManager.scanState

    private val validatingUids = mutableSetOf<String>()

    val maxBasketsPerScan: StateFlow<Int> = settingsDataStore.maxBasketsPerScan
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsDataStore.DEFAULT_MAX_BASKETS
        )

    init {
//        Timber.d("ReceivingViewModel initialized")
        loadWarehouses()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
        observeNetworkState()
        scanManager.setBarcodeKeepWarm(true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeScanManager() {
        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _uiState.map { it.scanMode }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ScanMode.SINGLE
            ),
            canStartScan = { _uiState.value.currentStep == ReceivingStep.SCANNING || _uiState.value.currentStep == ReceivingStep.SELECT_WAREHOUSE },
            scanContextProvider = {
                if (_uiState.value.currentStep == ReceivingStep.SELECT_WAREHOUSE) ScanContext.WAREHOUSE_SEARCH
                else ScanContext.BASKET_SCAN
            }
        )
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                when (result) {
                    is ScanResult.BarcodeScanned -> {
                        when (_uiState.value.currentStep) {
                            ReceivingStep.SELECT_WAREHOUSE -> { handleWarehouseSelection(result.barcode) }
                            ReceivingStep.SCANNING -> { handleScannedBarcode(result.barcode) }
                        }
                    }
                    is ScanResult.RfidScanned -> handleScannedRfidTag(result.tag.uid)
                    is ScanResult.ClearListRequested -> clearBaskets()
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

    private fun observeNetworkState() {
        viewModelScope.launch {
            networkState.collect { state ->
                Timber.d("📡 Production - Network state: $state")
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            }
            scanManager.changeScanMode(mode)
            _uiState.update { it.copy(scanMode = mode) }

            val shouldKeepWarm = _uiState.value.currentStep == ReceivingStep.SELECT_WAREHOUSE || mode == ScanMode.SINGLE
            scanManager.setBarcodeKeepWarm(shouldKeepWarm)
            Timber.d("🔀 Scan mode changed → $mode, keepBarcodeWarm=$shouldKeepWarm")
        }
    }

    private fun handleScannedBarcode(barcode: String) {
        Timber.d("🔍 Processing barcode: $barcode")
        fetchAndValidateBasket(barcode)
    }

    private fun handleScannedRfidTag(uid: String) {
        Timber.d("🔍 Processing RFID tag: $uid")
        fetchAndValidateBasket(uid)
    }

    fun toggleScanFromButton() {
        if (_uiState.value.currentStep != ReceivingStep.SCANNING) {
            _uiState.update { it.copy(error = "請先選擇倉庫") }
            return
        }

        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            } else {
                scanManager.startRfidScan(_uiState.value.scanMode)
            }
        }
    }

    // ==================== 步驟 1: 倉庫選擇 ====================

    private fun loadWarehouses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingWarehouses = true) }
            warehouseRepository.getWarehouses()
                .onSuccess { warehouses ->
                    _uiState.update {
                        it.copy(
                            warehouses = warehouses.filter { w -> w.isActive },
                            isLoadingWarehouses = false
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to load warehouses")
                    _uiState.update {
                        it.copy(
                            isLoadingWarehouses = false,
                            error = "載入倉庫列表失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    fun selectWarehouse(warehouse: Warehouse) {
        Timber.d("📍 Selected warehouse: ${warehouse.name}")
        scanManager.stopScanning()

        _uiState.update {
            it.copy(
                selectedWarehouse = warehouse,
                currentStep = ReceivingStep.SCANNING
            )
        }
        scanManager.setBarcodeKeepWarm(_uiState.value.scanMode == ScanMode.SINGLE)
    }

    /**
     * 處理倉庫選擇（通過 QR 碼）
     */
    private fun handleWarehouseSelection(scannedId: String) {
        val matchedWarehouse = _uiState.value.warehouses.find { warehouse ->
            warehouse.id.equals(scannedId, ignoreCase = true)
        }

        if (matchedWarehouse != null) {
            Timber.d("🎯 Auto-selecting warehouse: ${matchedWarehouse.name}")
            selectWarehouse(matchedWarehouse)
        } else {
            Timber.w("⚠️ No warehouse found with ID: $scannedId")
            _uiState.update {
                it.copy(error = "找不到倉庫 ID: $scannedId")
            }
        }
    }

    private fun fetchAndValidateBasket(uid: String) {
        if (validatingUids.contains(uid)) { return }

        if (_uiState.value.scannedBaskets.any { it.basket.uid == uid }) {
            _uiState.update { it.copy(error = "籃子已掃描: ${uid.takeLast(8)}") }
            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
            return
        }

        val limit = maxBasketsPerScan.value
        if (_uiState.value.scannedBaskets.size >= limit) {
            scanManager.stopScanning()
            _uiState.update {
                it.copy(
                    error = "⚠️ 已達上限 $limit 個,請先確認收貨後再繼續",
                    showConfirmDialog = true
                )
            }
            Timber.w("🛑 Receiving limit reached ($limit), rejecting: $uid")
            return
        }

        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

            basketRepository.fetchBasket(uid, isOnline.value)
                .onSuccess { basket ->
                    if (basket.status == BasketStatus.IN_PRODUCTION) {
                        Timber.d("✅ Basket valid for receiving: $uid")

                        if (_uiState.value.scannedBaskets.size >= maxBasketsPerScan.value) {
                            scanManager.stopScanning()
                            _uiState.update {
                                it.copy(
                                    error = "⚠️ 已達上限,請先確認收貨",
                                    showConfirmDialog = true
                                )
                            }
                        } else {
                            addBasket(basket)
                        }
                    } else {
                        val statusText = getBasketStatusText(basket.status)
                        _uiState.update {
                            it.copy(error = "籃子狀態錯誤: $statusText (需為生產中)")
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "驗證失敗: ${error.message}") }
                }

            _uiState.update { it.copy(isValidating = false) }
            delay(300)
            validatingUids.remove(uid)

            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
        }
    }

    private fun addBasket(basket: Basket) {
        val item = ScannedBasketItem(
            id = UUID.randomUUID().toString(),
            basket = basket
        )
        val updatedList = _uiState.value.scannedBaskets + item
        val limit = maxBasketsPerScan.value

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets + item,
                totalQuantity = state.totalQuantity + basket.quantity,
                isValidating = false,
//                successMessage = " 籃子 ${basket.tagCode?.takeLast(6)} 已添加"
            )
        }

        if (updatedList.size >= limit) {
            Timber.d("🛑 Receiving reached limit ($limit)")
            scanManager.stopScanning()
            _uiState.update {
                it.copy(
                    showConfirmDialog = true,
                    successMessage = "✅ 已達上限 $limit 個,請確認收貨"
                )
            }
            return
        }

        if (_uiState.value.scanMode == ScanMode.SINGLE) {
            scanManager.stopScanning()
        }
    }

    fun updateBasketQuantity(uid: String, newQuantity: Int) {
        _uiState.update { state ->
            val oldQuantity = state.scannedBaskets
                .find { it.basket.uid == uid }
                ?.basket?.quantity ?: 0

            val quantityDiff = newQuantity - oldQuantity

            state.copy(
                scannedBaskets = state.scannedBaskets.map { item ->
                    if (item.basket.uid == uid) {
                        val updatedBasket = item.basket.copy(quantity = newQuantity)
                        item.copy(basket = updatedBasket)
                    } else {
                        item
                    }
                },
                totalQuantity = (state.totalQuantity + quantityDiff).coerceAtLeast(0)
            )
        }
    }

    fun removeBasket(uid: String) {
        val item = _uiState.value.scannedBaskets.find { it.basket.uid == uid }

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.filter { it.basket.uid != uid },
                totalQuantity = (state.totalQuantity - (item?.basket?.quantity ?: 0)).coerceAtLeast(0)
            )
        }

        Timber.d("🗑️ Basket removed: $uid")
    }

    // ==================== 確認收貨 ====================

    fun showConfirmDialog() {
        if (_uiState.value.selectedWarehouse == null) {
            _uiState.update { it.copy(error = "請先選擇倉庫位置") }
            return
        }

        if (_uiState.value.scannedBaskets.isEmpty()) {
            _uiState.update { it.copy(error = "請至少掃描一個籃子") }
            return
        }

        val hasInvalidStatus = _uiState.value.scannedBaskets.any {
            it.basket.status != BasketStatus.IN_PRODUCTION
        }

        if (hasInvalidStatus) {
            _uiState.update { it.copy(error = "列表中有狀態錯誤的籃子，請先移除") }
            return
        }

        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun confirmReceiving() {
        viewModelScope.launch {

            val warehouse = _uiState.value.selectedWarehouse ?: return@launch
            val baskets = _uiState.value.scannedBaskets
            if (baskets.isEmpty()) return@launch

            _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }

            val currentUser = authRepository.getCurrentUser()?.username ?: "admin"

            val commonData = CommonDataDto(
                warehouseId = warehouse.id,
                updateBy = currentUser,
                status = "IN_STOCK"
            )

            val items = baskets.map {
                BasketUpdateItemDto(
                    rfid = it.basket.uid,
                    quantity = it.basket.quantity
                )
            }

            basketRepository.updateBasket(
                updateType = "Receiving",
                commonData = commonData,
                items = items,
                isOnline = isOnline.value
            ).onSuccess {
                _uiState.update {
                    it.copy(isLoading = false, scannedBaskets = emptyList(), successMessage = "✅ 收貨成功")
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    /**
     * 重置到倉庫選擇步驟
     */
    fun resetToWarehouseSelection() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                currentStep = ReceivingStep.SELECT_WAREHOUSE,
                selectedWarehouse = null,
                scannedBaskets = emptyList(),
                totalQuantity = 0
            )
        }
        scanManager.setBarcodeKeepWarm(true)
    }

    fun clearBaskets() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                scannedBaskets = emptyList(),
                totalQuantity = 0
            )
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }

    override fun onCleared() {
        super.onCleared()
        scanManager.setBarcodeKeepWarm(false)
        scanManager.cleanup()
    }
}

// ==================== Data Classes ====================
data class ReceivingUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val scanMode: ScanMode = ScanMode.CONTINUOUS,
    val isLoadingWarehouses: Boolean = false,
    val currentStep: ReceivingStep = ReceivingStep.SELECT_WAREHOUSE,
    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouse: Warehouse? = null,
    val scannedBaskets: List<ScannedBasketItem> = emptyList(),
    val totalQuantity: Int = 0,
    val showConfirmDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

enum class ReceivingStep {
    SELECT_WAREHOUSE,
    SCANNING
}

data class ScannedBasketItem(
    val id: String,
    val basket: Basket
)
package com.kdl.rfidinventory.presentation.ui.screens.warehouse.receiving

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Warehouse
import com.kdl.rfidinventory.data.model.getBasketStatusText
import com.kdl.rfidinventory.data.remote.dto.request.BasketUpdateItemDto
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.data.repository.BasketRepository
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

enum class ReceivingStep {
    SELECT_WAREHOUSE,
    SCANNING
}

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ReceivingViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val warehouseRepository: WarehouseRepository,
    private val basketRepository: BasketRepository,
    private val webSocketManager: WebSocketManager,
    private val pendingOperationDao: PendingOperationDao,
    private val authRepository: AuthRepository
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

    init {
        Timber.d("ReceivingViewModel initialized")
        loadWarehouses()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
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

                    if (_uiState.value.currentStep == ReceivingStep.SELECT_WAREHOUSE) {
                        scanManager.startBarcodeScan(ScanContext.WAREHOUSE_SEARCH)
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
    }

    // ==================== 步驟 2: 掃描收貨 ====================

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializeScanManager() {
        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _uiState.map { it.scanMode }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ScanMode.SINGLE
            ),
            canStartScan = {
                // ✅ 兩個步驟都可以掃描
                _uiState.value.currentStep == ReceivingStep.SCANNING ||
                        _uiState.value.currentStep == ReceivingStep.SELECT_WAREHOUSE
            }
        )
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                when (result) {
                    is ScanResult.BarcodeScanned -> {
//                        Timber.d("📦 Barcode scanned: ${result.barcode}, context: ${result.context}")

                        when (_uiState.value.currentStep) {
                            ReceivingStep.SELECT_WAREHOUSE -> {
                                // 倉庫選擇步驟：匹配倉庫 ID
                                handleWarehouseSelection(result.barcode)
                            }
                            ReceivingStep.SCANNING -> {
                                // 掃描步驟：處理籃子
                                handleScannedBarcode(result.barcode)
                            }
                        }
                    }
                    is ScanResult.RfidScanned -> {
                        handleScannedRfidTag(result.tag.uid)
                    }
                    is ScanResult.ClearListRequested -> {
                        clearBaskets()
                    }
                }
            }
        }
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

    private fun observeScanErrors() {
        viewModelScope.launch {
            scanManager.errors.collect { error ->
                _uiState.update { it.copy(error = error) }
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch {
            scanManager.changeScanMode(mode)
            _uiState.update { it.copy(scanMode = mode) }
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

    fun clearBaskets() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                scannedBaskets = emptyList(),
                totalQuantity = 0
            )
        }
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

    private fun fetchAndValidateBasket(uid: String) {
        if (validatingUids.contains(uid)) {
            Timber.d("⏭️ Basket $uid is already being validated, skipping...")
            return
        }

        if (_uiState.value.scannedBaskets.any { it.basket.uid == uid }) {
            _uiState.update { it.copy(error = "籃子已掃描: ${uid.takeLast(8)}") }
            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
            return
        }

        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

//            val online = isOnline.value
//            val validationResult = warehouseRepository.validateBasketForReceiving(uid, online)
//
//            when (validationResult) {
//                is BasketValidationForReceivingResult.Valid -> {
//                    Timber.d("✅ Basket validated successfully for receiving: $uid")
//                    addBasket(validationResult.basket)
//                }
//
//                is BasketValidationForReceivingResult.NotRegistered -> {
//                    Timber.w("⚠️ Basket not registered: $uid")
//                    _uiState.update {
//                        it.copy(
//                            isValidating = false,
//                            error = "籃子 ${uid.takeLast(8)} 尚未登記"
//                        )
//                    }
//                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
//                        scanManager.stopScanning()
//                    }
//                }
//
//                is BasketValidationForReceivingResult.InvalidStatus -> {
//                    val statusText = getBasketStatusText(validationResult.currentStatus)
//                    _uiState.update {
//                        it.copy(
//                            isValidating = false,
//                            error = "籃子 ${uid.takeLast(8)} 狀態為「${statusText}」，無法收貨"
//                        )
//                    }
//                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
//                        scanManager.stopScanning()
//                    }
//                }
//
//                is BasketValidationForReceivingResult.Error -> {
//                    Timber.e("❌ Basket validation error: $uid - ${validationResult.message}")
//                    _uiState.update {
//                        it.copy(
//                            isValidating = false,
//                            error = "驗證籃子失敗: ${validationResult.message}"
//                        )
//                    }
//                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
//                        scanManager.stopScanning()
//                    }
//                }
//            }
//
//            kotlinx.coroutines.delay(300)
//            validatingUids.remove(uid)

            basketRepository.fetchBasket(uid, isOnline.value)
                .onSuccess { basket ->
                    // 2. ViewModel 自行判斷業務邏輯
                    if (basket.status == BasketStatus.IN_PRODUCTION) {
                        Timber.d("✅ Basket valid for receiving: $uid")
                        addBasket(basket)
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

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets + item,
                totalQuantity = state.totalQuantity + basket.quantity,
                isValidating = false,
                successMessage = " 籃子 ${basket.tagCode?.takeLast(6)} 已添加"
            )
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
//            val items = _uiState.value.scannedBaskets
//            val warehouse = _uiState.value.selectedWarehouse
//
//            if (warehouse == null) {
//                _uiState.update { it.copy(error = "請先選擇倉庫位置") }
//                return@launch
//            }
//
//            if (items.isEmpty()) {
//                _uiState.update { it.copy(error = "請至少掃描一個籃子") }
//                return@launch
//            }
//
//            _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }
//
//            val online = isOnline.value
//            val receivingItems = items.map { item ->
//                ReceivingItem(
//                    uid = item.basket.uid,
//                    quantity = item.basket.quantity
//                )
//            }
//
//            val currentUser = authRepository.getCurrentUser()?.username ?: ""
//
//            warehouseRepository.receiveBaskets(
//                receivingItems,
//                warehouse.id,
//                currentUser,
//                online
//            )
//                .onSuccess {
//                    _uiState.update {
//                        it.copy(
//                            isLoading = false,
//                            scannedBaskets = emptyList(),
//                            totalQuantity = 0,
//                            successMessage = "✅ 收貨成功，共 ${receivingItems.size} 個籃子"
//                        )
//                    }
//                }
//                .onFailure { error ->
//                    _uiState.update {
//                        it.copy(
//                            isLoading = false,
//                            error = "收貨失敗: ${error.message}"
//                        )
//                    }
//                }

            val warehouse = _uiState.value.selectedWarehouse ?: return@launch
            val baskets = _uiState.value.scannedBaskets
            if (baskets.isEmpty()) return@launch

            _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }

            val currentUser = authRepository.getCurrentUser()?.username ?: "admin"

            // 1. Common Data: 統一倉庫、人員、狀態
            val commonData = CommonDataDto(
                warehouseId = warehouse.id,
                updateBy = currentUser,
                status = "IN_STOCK"
            )

            // 2. Items: 包含個別數量
            val items = baskets.map {
                BasketUpdateItemDto(
                    rfid = it.basket.uid,
                    quantity = it.basket.quantity
                )
            }

            // 3. 統一呼叫
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

        // ✅ 重新啟動條碼掃描
        viewModelScope.launch {
            delay(300)
            scanManager.startBarcodeScan(ScanContext.WAREHOUSE_SEARCH)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        scanManager.cleanup()
    }
}

// ==================== Data Classes ====================

data class ScannedBasketItem(
    val id: String,
    val basket: Basket
)

data class ReceivingUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val isLoadingWarehouses: Boolean = false,

    // 步驟控制
    val currentStep: ReceivingStep = ReceivingStep.SELECT_WAREHOUSE,

    val scanMode: ScanMode = ScanMode.SINGLE,

    // 倉庫選擇
    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouse: Warehouse? = null,

    // 掃描收貨
    val scannedBaskets: List<ScannedBasketItem> = emptyList(),
    val totalQuantity: Int = 0,

    val showConfirmDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
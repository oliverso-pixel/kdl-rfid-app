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
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.BasketValidationForReceivingResult
import com.kdl.rfidinventory.data.repository.ReceivingItem
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ReceivingViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val warehouseRepository: WarehouseRepository,
    private val webSocketManager: WebSocketManager,
    private val pendingOperationDao: PendingOperationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceivingUiState())
    val uiState: StateFlow<ReceivingUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = webSocketManager.isOnline

    // 綜合網絡狀態（結合 isOnline 和待同步數量）
    val networkState: StateFlow<NetworkState> = combine(
        isOnline,
        pendingOperationDao.getPendingCount()
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

//    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
//    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    val scanState = scanManager.scanState

    private val validatingUids = mutableSetOf<String>()

    init {
        Timber.d("ReceivingViewModel initialized")
        loadWarehouses()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
    }

    // 載入倉庫列表
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
                // 必須先選擇倉庫才能掃描
                _uiState.value.selectedWarehouse != null
            }
        )
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
                    is ScanResult.ClearListRequested -> {
                        clearBaskets()
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
        // 檢查是否已選擇倉庫
        if (_uiState.value.selectedWarehouse == null) {
            _uiState.update { it.copy(error = "請先選擇倉庫位置") }
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

    // 選擇倉庫
    fun selectWarehouse(warehouse: Warehouse) {
        _uiState.update {
            it.copy(
                selectedWarehouse = warehouse,
                showWarehouseDialog = false
            )
        }
        Timber.d("📍 倉庫已選擇: ${warehouse.name} (${warehouse.id})")
    }

    // 顯示/隱藏倉庫選擇對話框
    fun showWarehouseDialog() {
        _uiState.update { it.copy(showWarehouseDialog = true) }
    }

    fun dismissWarehouseDialog() {
        _uiState.update { it.copy(showWarehouseDialog = false) }
    }

    /**
     *  驗證並添加籃子
     */
    private fun fetchAndValidateBasket(uid: String) {
        // 防止重複驗證
        if (validatingUids.contains(uid)) {
            Timber.d("⏭️ Basket $uid is already being validated, skipping...")
            return
        }

        // 檢查是否已經在列表中
        if (_uiState.value.scannedBaskets.any { it.basket.uid == uid }) {
            _uiState.update { it.copy(error = "籃子已掃描: ${uid.takeLast(8)}") }
            // 單次模式停止掃描
            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
            return
        }

        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

            val online = isOnline.value
            Timber.d("🌐 Validating basket for receiving - isOnline: $isOnline")

            val validationResult = warehouseRepository.validateBasketForReceiving(uid, online)

            when (validationResult) {
                is BasketValidationForReceivingResult.Valid -> {
                    Timber.d("✅ Basket validated successfully for receiving: $uid")
                    addBasket(validationResult.basket)
                }

                is BasketValidationForReceivingResult.NotRegistered -> {
                    Timber.w("⚠️ Basket not registered: $uid")
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "籃子 ${uid.takeLast(8)} 尚未登記"
                        )
                    }
                    // 單次模式停止掃描
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }

                is BasketValidationForReceivingResult.InvalidStatus -> {
                    Timber.w("⚠️ Basket has invalid status for receiving: $uid (${validationResult.currentStatus})")
                    val statusText = getBasketStatusText(validationResult.currentStatus)
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "籃子 ${uid.takeLast(8)} 狀態為「${statusText}」，無法收貨\n只能收貨「生產中」狀態的籃子"
                        )
                    }
                    // 單次模式停止掃描
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }

                is BasketValidationForReceivingResult.Error -> {
                    Timber.e("❌ Basket validation error: $uid - ${validationResult.message}")
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "驗證籃子失敗: ${validationResult.message}"
                        )
                    }
                    // 單次模式停止掃描
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        scanManager.stopScanning()
                    }
                }
            }

            // 移除驗證標記
            kotlinx.coroutines.delay(300)
            validatingUids.remove(uid)
        }
    }

    /**
     *  添加籃子到列表
     */
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
                successMessage = "✅ 籃子 ${basket.uid.takeLast(8)} 已添加"
            )
        }

        // 單次掃描模式：成功後自動停止
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
            val items = _uiState.value.scannedBaskets
            val warehouse = _uiState.value.selectedWarehouse

            if (warehouse == null) {
                _uiState.update { it.copy(error = "請先選擇倉庫位置") }
                return@launch
            }

            if (items.isEmpty()) {
                _uiState.update { it.copy(error = "請至少掃描一個籃子") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }

            val online = isOnline.value

            // 轉換為 ReceivingItem（包含 quantity）
            val receivingItems = items.map { item ->
                ReceivingItem(
                    uid = item.basket.uid,
                    quantity = item.basket.quantity
                )
            }

            warehouseRepository.receiveBaskets(receivingItems, warehouse.id, online)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            scannedBaskets = emptyList(),
                            totalQuantity = 0,
                            successMessage = "✅ 收貨成功，共 ${receivingItems.size} 個籃子\n倉庫：${warehouse.name}\n可繼續掃描下一批"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "收貨失敗: ${error.message}"
                        )
                    }
                }
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

data class ScannedBasketItem(
    val id: String,
    val basket: Basket
)

data class ReceivingUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val isLoadingWarehouses: Boolean = false,
    val scanMode: ScanMode = ScanMode.SINGLE,
    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouse: Warehouse? = null,
    val showWarehouseDialog: Boolean = false,
    val scannedBaskets: List<ScannedBasketItem> = emptyList(),
    val totalQuantity: Int = 0,
    val showConfirmDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
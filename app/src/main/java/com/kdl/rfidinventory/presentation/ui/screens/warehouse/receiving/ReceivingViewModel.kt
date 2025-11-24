package com.kdl.rfidinventory.presentation.ui.screens.warehouse.receiving

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.repository.Warehouse
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class ReceivingViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceivingUiState())
    val uiState: StateFlow<ReceivingUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    val scanState = scanManager.scanState

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

    private fun fetchAndValidateBasket(uid: String) {
        if (_uiState.value.scannedBaskets.any { it.basket.uid == uid }) {
            _uiState.update { it.copy(error = "籃子已掃描: ${uid.takeLast(8)}") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true) }

            warehouseRepository.getBasketByUid(uid)
                .onSuccess { basket ->
                    Timber.d("✅ Basket fetched: ${basket.uid}, Status: ${basket.status}")

                    val isValidStatus = basket.status == BasketStatus.IN_PRODUCTION

                    val statusMessage = if (!isValidStatus) {
                        when (basket.status) {
                            BasketStatus.UNASSIGNED -> "⚠️ 狀態錯誤：未配置"
                            BasketStatus.RECEIVED -> "⚠️ 狀態錯誤：已收貨"
                            BasketStatus.IN_STOCK -> "⚠️ 狀態錯誤：已在庫"
                            BasketStatus.SHIPPED -> "⚠️ 狀態錯誤：已出貨"
                            BasketStatus.SAMPLING -> "⚠️ 狀態錯誤：抽樣中"
                            else -> "⚠️ 狀態錯誤：${basket.status.name}"
                        }
                    } else {
                        "✅ 籃子 ${uid.takeLast(8)} 已加入"
                    }

                    val item = ScannedBasketItem(
                        id = UUID.randomUUID().toString(),
                        basket = basket
                    )

                    _uiState.update { state ->
                        state.copy(
                            scannedBaskets = state.scannedBaskets + item,
                            totalQuantity = state.totalQuantity + basket.quantity,
                            isValidating = false,
                            successMessage = if (isValidStatus) statusMessage else null,
                            error = if (!isValidStatus) statusMessage else null
                        )
                    }
                }
                .onFailure { error ->
                    Timber.e(error, "Failed to fetch basket")
                    _uiState.update {
                        it.copy(
                            isValidating = false,
                            error = "查詢籃子失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    fun updateBasketQuantity(uid: String, newQuantity: Int) {
        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.map { item ->
                    if (item.basket.uid == uid) {
                        val oldQuantity = item.basket.quantity
                        val updatedBasket = item.basket.copy(quantity = newQuantity)
                        val quantityDiff = newQuantity - oldQuantity

                        _uiState.update { it.copy(totalQuantity = it.totalQuantity + quantityDiff) }

                        item.copy(basket = updatedBasket)
                    } else {
                        item
                    }
                }
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

            val isOnline = _networkState.value is NetworkState.Connected
            val uids = items.map { it.basket.uid }

//            warehouseRepository.receiveBaskets(uids, warehouse.id, isOnline)
            warehouseRepository.receiveBaskets(uids, isOnline)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            scannedBaskets = emptyList(),
                            totalQuantity = 0,
                            successMessage = "✅ 收貨成功，共 ${uids.size} 個籃子\n倉庫：${warehouse.name}\n可繼續掃描下一批"
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
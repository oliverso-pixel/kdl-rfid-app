package com.kdl.rfidinventory.presentation.ui.screens.warehouse.transfer

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
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.repository.ReceivingItem
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

enum class TransferStep {
    SELECT_TARGET_WAREHOUSE,
    SCANNING
}

data class ScannedTransferItem(
    val id: String = UUID.randomUUID().toString(),
    val basket: Basket
)

data class TransferUiState(
    val isLoading: Boolean = false,
    val isLoadingWarehouses: Boolean = false,
    val isValidating: Boolean = false,

    val currentStep: TransferStep = TransferStep.SELECT_TARGET_WAREHOUSE,
    val scanMode: ScanMode = ScanMode.SINGLE,

    val warehouses: List<Warehouse> = emptyList(),
    val selectedWarehouse: Warehouse? = null, // 目標倉庫

    val scannedBaskets: List<ScannedTransferItem> = emptyList(),

    val showConfirmDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val scanManager: ScanManager,
    private val warehouseRepository: WarehouseRepository,
    private val basketRepository: BasketRepository,
    private val webSocketManager: WebSocketManager,
    private val pendingOperationDao: PendingOperationDao,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

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
        loadWarehouses()
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
    }

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

                    // 如果在選擇倉庫步驟，啟動條碼掃描以支援 QR Code 選擇
                    if (_uiState.value.currentStep == TransferStep.SELECT_TARGET_WAREHOUSE) {
                        scanManager.startBarcodeScan(ScanContext.WAREHOUSE_SEARCH)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingWarehouses = false,
                            error = "載入倉庫列表失敗: ${error.message}"
                        )
                    }
                }
        }
    }

    // ==================== 掃描管理器整合 ====================

    private fun initializeScanManager() {
        scanManager.initialize(
            scope = viewModelScope,
            scanMode = _uiState.map { it.scanMode }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                ScanMode.SINGLE
            ),
            canStartScan = { true }
        )
    }

    private fun observeScanResults() {
        viewModelScope.launch {
            scanManager.scanResults.collect { result ->
                when (result) {
                    is ScanResult.BarcodeScanned -> {
                        when (_uiState.value.currentStep) {
                            TransferStep.SELECT_TARGET_WAREHOUSE -> handleWarehouseSelection(result.barcode)
                            TransferStep.SCANNING -> handleScannedBarcode(result.barcode)
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

    // ==================== 業務邏輯 ====================

    fun selectTargetWarehouse(warehouse: Warehouse) {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                selectedWarehouse = warehouse,
                currentStep = TransferStep.SCANNING
            )
        }
    }

    private fun handleWarehouseSelection(scannedId: String) {
        val matchedWarehouse = _uiState.value.warehouses.find {
            it.id.equals(scannedId, ignoreCase = true)
        }
        if (matchedWarehouse != null) {
            selectTargetWarehouse(matchedWarehouse)
        } else {
            _uiState.update { it.copy(error = "找不到倉庫 ID: $scannedId") }
        }
    }

    fun setScanMode(mode: ScanMode) {
        viewModelScope.launch {
            scanManager.changeScanMode(mode)
            _uiState.update { it.copy(scanMode = mode) }
        }
    }

    fun toggleScanFromButton() {
        if (_uiState.value.currentStep != TransferStep.SCANNING) {
            _uiState.update { it.copy(error = "請先選擇目標倉庫") }
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

    private fun handleScannedBarcode(barcode: String) = validateAndAddBasket(barcode)
    private fun handleScannedRfidTag(uid: String) = validateAndAddBasket(uid)

    private fun validateAndAddBasket(uid: String) {
        if (validatingUids.contains(uid)) return
        if (_uiState.value.scannedBaskets.any { it.basket.uid == uid }) {
            _uiState.update { it.copy(error = "籃子已掃描: ${uid.takeLast(8)}") }
            if (_uiState.value.scanMode == ScanMode.SINGLE) scanManager.stopScanning()
            return
        }

        viewModelScope.launch {
            validatingUids.add(uid)
            _uiState.update { it.copy(isValidating = true) }

            basketRepository.fetchBasket(uid, isOnline.value)
                .onSuccess { basket ->
                    // 2. ViewModel 自行判斷業務邏輯
                    if (basket.status == BasketStatus.IN_STOCK) {
                        // 額外檢查：是否已經在目標倉庫
                        val targetWarehouseId = _uiState.value.selectedWarehouse?.id
                        if (basket.warehouseId == targetWarehouseId) {
                            _uiState.update { it.copy(error = "籃子已經在目標倉庫中") }
                        } else {
                            addBasket(basket)
                        }
                    } else {
                        val statusText = getBasketStatusText(basket.status)
                        _uiState.update {
                            it.copy(error = "籃子狀態錯誤: $statusText (需為在庫中)")
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = "讀取失敗: ${error.message}") }
                }

            _uiState.update { it.copy(isValidating = false) }
            validatingUids.remove(uid)
        }
    }

    private fun addBasket(basket: Basket) {
        _uiState.update { state ->
            val item = ScannedTransferItem(basket = basket)
            state.copy(
                scannedBaskets = state.scannedBaskets + item,
                successMessage = "已加入籃子 ${basket.uid.takeLast(8)}"
            )
        }
    }

    fun removeBasket(uid: String) {
        _uiState.update { state ->
            state.copy(scannedBaskets = state.scannedBaskets.filter { it.basket.uid != uid })
        }
    }

    fun clearBaskets() {
        scanManager.stopScanning()
        _uiState.update { it.copy(scannedBaskets = emptyList()) }
    }

    // ==================== 提交 ====================

    fun showConfirmDialog() {
        if (_uiState.value.scannedBaskets.isEmpty()) {
            _uiState.update { it.copy(error = "請至少掃描一個籃子") }
            return
        }
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun submitTransfer() {
        val targetWarehouse = uiState.value.selectedWarehouse ?: return
        val items = uiState.value.scannedBaskets
        if (items.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }

            // 準備資料
            val receivingItems = items.map { ReceivingItem(it.basket.uid, it.basket.quantity) }
            val currentUser = authRepository.getCurrentUser()?.username ?: "admin"

            // 重用收貨邏輯 (Update Basket Status to IN_STOCK at New Warehouse)
            warehouseRepository.receiveBaskets(
                items = receivingItems,
                warehouseId = targetWarehouse.id,
                updateBy = currentUser,
                isOnline = isOnline.value
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scannedBaskets = emptyList(),
                        successMessage = "✅ 成功轉換 ${items.size} 個籃子至 ${targetWarehouse.name}"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = "轉換失敗: ${error.message}")
                }
            }
        }
    }

    fun resetToWarehouseSelection() {
        scanManager.stopScanning()
        _uiState.update {
            it.copy(
                currentStep = TransferStep.SELECT_TARGET_WAREHOUSE,
                selectedWarehouse = null,
                scannedBaskets = emptyList()
            )
        }
        // 重新啟動條碼掃描以便選擇倉庫
        viewModelScope.launch {
            delay(300)
            scanManager.startBarcodeScan(ScanContext.WAREHOUSE_SEARCH)
        }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }

    override fun onCleared() {
        super.onCleared()
        scanManager.cleanup()
    }
}
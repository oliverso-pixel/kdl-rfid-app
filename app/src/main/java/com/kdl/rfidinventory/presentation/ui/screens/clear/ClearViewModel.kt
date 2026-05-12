package com.kdl.rfidinventory.presentation.ui.screens.clear

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.remote.dto.request.BasketUpdateItemDto
import com.kdl.rfidinventory.data.remote.dto.request.CommonDataDto
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.data.repository.BasketRepository
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
class ClearViewModel @Inject constructor(
    private val scanManager: ScanManager,
    webSocketManager: WebSocketManager,
    pendingOperationDao: PendingOperationDao,
    private val authRepository: AuthRepository,
    private val basketRepository: BasketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClearUiState())
    val uiState: StateFlow<ClearUiState> = _uiState.asStateFlow()

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
        Timber.d("Clear initialized")
        initializeScanManager()
        observeScanResults()
        observeScanErrors()
        observeNetworkState()
        scanManager.setBarcodeKeepWarm(false)
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
            canStartScan = { true },
            scanContextProvider = { ScanContext.BASKET_SCAN }
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
            scanManager.errors.collect { error -> _uiState.update { it.copy(error = error) } }
        }
    }
    private fun observeNetworkState() {
        viewModelScope.launch {
            networkState.collect { state ->
                Timber.d("📡 Clear - Network state: $state")
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

            scanManager.setBarcodeKeepWarm(mode == ScanMode.SINGLE)
            Timber.d("🔀 Scan mode changed → $mode, keepBarcodeWarm=${mode == ScanMode.SINGLE}")
        }
    }

    private fun handleScannedBarcode(barcode: String) = fetchAndValidateBasket(barcode)
    private fun handleScannedRfidTag(uid: String) = fetchAndValidateBasket(uid)

    fun toggleScanFromButton() {
        viewModelScope.launch {
            if (scanManager.scanState.value.isScanning) {
                scanManager.stopScanning()
            } else {
                scanManager.startRfidScan(_uiState.value.scanMode)
            }
        }
    }

    // ==================== 業務邏輯 ====================
    /**
     * 驗證並添加籃子
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

            basketRepository.fetchBasket(uid, isOnline.value)
                .onSuccess { basket ->
                    when (basket.status) {
                        BasketStatus.UNASSIGNED -> {
                            Timber.d("✅ Basket valid for production: $uid")
                            _uiState.update {
                                it.copy(error = "籃子 ${uid.takeLast(8)} 狀態為「未配置」，無法清除\n只能清除已配置的籃子")
                            }
                        }
                        else -> {
                            Timber.d("✅ Basket is valid for clearing: $uid (${basket.status})")
                            addBasket(basket)
                        }
                    }
                }
                .onFailure { error ->
                    Timber.w("⚠️ Basket not found: $uid - ${error.message}")
                    _uiState.update {
                        it.copy(error = "籃子 ${uid.takeLast(8)} 不存在或無法讀取")
                    }

                }

            _uiState.update { it.copy(isValidating = false) }
            delay(300)
            validatingUids.remove(uid)

            if (_uiState.value.scanMode == ScanMode.SINGLE) {
                scanManager.stopScanning()
            }
        }
    }

    /**
     * 添加籃子到列表
     */
    private fun addBasket(basket: Basket) {
        val item = ScannedBasketItem(
            id = UUID.randomUUID().toString(),
            basket = basket
        )

        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets + item,
                isValidating = false,
                successMessage = "籃子 ${basket.tagCode?.takeLast(6)} 已添加"
            )
        }

        // 單次掃描模式：成功後自動停止
        if (_uiState.value.scanMode == ScanMode.SINGLE) {
            scanManager.stopScanning()
        }
    }

    fun removeBasket(uid: String) {
        _uiState.update { state ->
            state.copy(
                scannedBaskets = state.scannedBaskets.filter { it.basket.uid != uid }
            )
        }
        Timber.d("🗑️ Basket removed: $uid")
    }

    fun showConfirmDialog() {
        if (_uiState.value.scannedBaskets.isEmpty()) {
            _uiState.update { it.copy(error = "請至少掃描一個籃子") }
            return
        }

        val hasInvalidStatus = _uiState.value.scannedBaskets.any {
            it.basket.status == BasketStatus.UNASSIGNED
        }

        if (hasInvalidStatus) {
            _uiState.update { it.copy(error = "列表中有「未配置」狀態的籃子，請先移除") }
            return
        }

        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun confirmClear() {
        viewModelScope.launch {
            val items = _uiState.value.scannedBaskets

            if (items.isEmpty()) {
                _uiState.update { it.copy(error = "請至少掃描一個籃子") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, showConfirmDialog = false) }

            val online = isOnline.value
            val currentUser = authRepository.getCurrentUser()?.username ?: "admin"

            val commonData = CommonDataDto(
                updateBy = currentUser,
                status = "UNASSIGNED"
            )

            val updateItems = items.map {
                BasketUpdateItemDto(
                    rfid = it.basket.uid,
                    quantity = 0
                )
            }

            basketRepository.updateBasket(
                updateType = "Clear",
                commonData = commonData,
                items = updateItems,
                isOnline = online
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scannedBaskets = emptyList(),
                        successMessage = "✅ 清除成功，共 ${items.size} 個籃子\n可繼續掃描下一批"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "清除失敗: ${error.message}"
                    )
                }
            }
        }
    }

    fun clearBaskets() {
        scanManager.stopScanning()
        _uiState.update { it.copy(scannedBaskets = emptyList()) }
    }

    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }

    override fun onCleared() {
        super.onCleared()
        scanManager.setBarcodeKeepWarm(false)
        scanManager.cleanup()
    }
}

data class ScannedBasketItem(
    val id: String,
    val basket: Basket
)

data class ClearUiState(
    val isLoading: Boolean = false,
    val isValidating: Boolean = false,
    val scanMode: ScanMode = ScanMode.CONTINUOUS,
    val scannedBaskets: List<ScannedBasketItem> = emptyList(),
    val showConfirmDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
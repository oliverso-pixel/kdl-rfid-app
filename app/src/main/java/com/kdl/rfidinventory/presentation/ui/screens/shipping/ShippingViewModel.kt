package com.kdl.rfidinventory.presentation.ui.screens.shipping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Route
import com.kdl.rfidinventory.data.repository.ShippingRepository
import com.kdl.rfidinventory.util.rfid.RFIDManager
import com.kdl.rfidinventory.util.rfid.RFIDTag
import com.kdl.rfidinventory.util.NetworkState
import com.kdl.rfidinventory.util.ScanMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShippingViewModel @Inject constructor(
    private val rfidManager: RFIDManager,
    private val shippingRepository: ShippingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShippingUiState())
    val uiState: StateFlow<ShippingUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val scannedBaskets = mutableMapOf<String, Basket>()

    init {
        loadRoutes()
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            shippingRepository.getRoutes()
                .onSuccess { routes ->
                    _uiState.update { it.copy(routes = routes) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
        }
    }

    fun selectRoute(route: Route) {
        _uiState.update { it.copy(selectedRoute = route) }
    }

    fun startScanning() {
        val selectedRoute = _uiState.value.selectedRoute
        if (selectedRoute == null) {
            _uiState.update { it.copy(error = "請先選擇路線") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            rfidManager.startScan(_uiState.value.scanMode)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            error = "掃描失敗: ${error.message}",
                            isScanning = false
                        )
                    }
                }
                .collect { tag ->
                    handleScannedTag(tag)
                }
        }
    }

    fun stopScanning() {
        rfidManager.stopScan()
        _uiState.update { it.copy(isScanning = false) }
    }

    private fun handleScannedTag(tag: RFIDTag) {
        viewModelScope.launch {
            // 檢查是否已掃描
            if (scannedBaskets.containsKey(tag.uid)) {
                _uiState.update { it.copy(error = "籃子已掃描: ${tag.uid}") }
                return@launch
            }

            // 從資料庫查詢籃子信息
            shippingRepository.getBasketByUid(tag.uid)
                .onSuccess { basket ->
                    // 檢查籃子狀態
                    if (basket.status != BasketStatus.IN_STOCK) {
                        _uiState.update {
                            it.copy(error = "籃子狀態錯誤: ${basket.status.name}")
                        }
                        return@onSuccess
                    }

                    scannedBaskets[tag.uid] = basket
                    _uiState.update {
                        it.copy(
                            scannedBaskets = scannedBaskets.values.toList(),
                            totalQuantity = scannedBaskets.values.sumOf { b -> b.quantity }
                        )
                    }

                    // 單次掃描模式下停止
                    if (_uiState.value.scanMode == ScanMode.SINGLE) {
                        stopScanning()
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "查詢失敗: ${error.message}")
                    }
                }
        }
    }

    fun removeBasket(uid: String) {
        scannedBaskets.remove(uid)
        _uiState.update {
            it.copy(
                scannedBaskets = scannedBaskets.values.toList(),
                totalQuantity = scannedBaskets.values.sumOf { b -> b.quantity }
            )
        }
    }

    fun confirmShipping() {
        viewModelScope.launch {
            val selectedRoute = _uiState.value.selectedRoute
            if (selectedRoute == null) {
                _uiState.update { it.copy(error = "請先選擇路線") }
                return@launch
            }

            if (scannedBaskets.isEmpty()) {
                _uiState.update { it.copy(error = "沒有掃描任何籃子") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true) }

            val isOnline = _networkState.value is NetworkState.Connected
            val uids = scannedBaskets.keys.toList()

            shippingRepository.shipBaskets(
                uids = uids,
                routeId = selectedRoute.id,
                isOnline = isOnline
            ).onSuccess {
                scannedBaskets.clear()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scannedBaskets = emptyList(),
                        totalQuantity = 0,
                        successMessage = "出貨成功，共 ${uids.size} 個籃子"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "出貨失敗: ${error.message}"
                    )
                }
            }
        }
    }

    fun setScanMode(mode: ScanMode) {
        _uiState.update { it.copy(scanMode = mode) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

data class ShippingUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanMode: ScanMode = ScanMode.CONTINUOUS,
    val routes: List<Route> = emptyList(),
    val selectedRoute: Route? = null,
    val scannedBaskets: List<Basket> = emptyList(),
    val totalQuantity: Int = 0,
    val error: String? = null,
    val successMessage: String? = null
)
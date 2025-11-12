package com.kdl.rfidinventory.presentation.ui.screens.clear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.repository.BasketRepository
import com.kdl.rfidinventory.data.rfid.RFIDManager
import com.kdl.rfidinventory.data.rfid.RFIDTag
import com.kdl.rfidinventory.util.NetworkState
import com.kdl.rfidinventory.util.ScanMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClearViewModel @Inject constructor(
    private val rfidManager: RFIDManager,
    private val basketRepository: BasketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClearUiState())
    val uiState: StateFlow<ClearUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val scannedBaskets = mutableMapOf<String, Basket>()

    fun startScanning() {
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
            basketRepository.getBasketByUid(tag.uid)
                .onSuccess { basket ->
                    scannedBaskets[tag.uid] = basket
                    _uiState.update {
                        it.copy(scannedBaskets = scannedBaskets.values.toList())
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
            it.copy(scannedBaskets = scannedBaskets.values.toList())
        }
    }

    fun showConfirmDialog() {
        if (scannedBaskets.isEmpty()) {
            _uiState.update { it.copy(error = "沒有掃描任何籃子") }
            return
        }
        _uiState.update { it.copy(showConfirmDialog = true) }
    }

    fun confirmClear() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    showConfirmDialog = false
                )
            }

            val isOnline = _networkState.value is NetworkState.Connected
            val uids = scannedBaskets.keys.toList()

            basketRepository.clearBasketConfiguration(uids, isOnline)
                .onSuccess {
                    scannedBaskets.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            scannedBaskets = emptyList(),
                            successMessage = "清除成功，共 ${uids.size} 個籃子"
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

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
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

data class ClearUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanMode: ScanMode = ScanMode.CONTINUOUS,
    val scannedBaskets: List<Basket> = emptyList(),
    val showConfirmDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
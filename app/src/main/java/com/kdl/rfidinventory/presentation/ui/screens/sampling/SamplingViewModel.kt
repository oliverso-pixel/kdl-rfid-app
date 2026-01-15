package com.kdl.rfidinventory.presentation.ui.screens.sampling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.repository.SamplingRepository
import com.kdl.rfidinventory.util.rfid.RFIDManager
import com.kdl.rfidinventory.util.rfid.RFIDTag
import com.kdl.rfidinventory.util.NetworkState
import com.kdl.rfidinventory.util.ScanMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SamplingViewModel @Inject constructor(
    private val rfidManager: RFIDManager,
    private val samplingRepository: SamplingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SamplingUiState())
    val uiState: StateFlow<SamplingUiState> = _uiState.asStateFlow()

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
            samplingRepository.getBasketByUid(tag.uid)
                .onSuccess { basket ->
                    // 檢查籃子狀態 - 只能抽樣在倉庫中的籃子
                    if (basket.status != BasketStatus.IN_STOCK && basket.status != BasketStatus.RECEIVED) {
                        _uiState.update {
                            it.copy(error = "籃子狀態錯誤，只能抽樣在庫籃子")
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

    fun showSampleQuantityDialog() {
        if (scannedBaskets.isEmpty()) {
            _uiState.update { it.copy(error = "沒有掃描任何籃子") }
            return
        }
        _uiState.update { it.copy(showSampleQuantityDialog = true) }
    }

    fun confirmSampling(sampleQuantity: Int, remarks: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    showSampleQuantityDialog = false
                )
            }

            val isOnline = _networkState.value is NetworkState.Connected
            val uids = scannedBaskets.keys.toList()

            samplingRepository.markForSampling(
                uids = uids,
                sampleQuantity = sampleQuantity,
                remarks = remarks,
                isOnline = isOnline
            ).onSuccess {
                scannedBaskets.clear()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        scannedBaskets = emptyList(),
                        totalQuantity = 0,
                        successMessage = "抽樣標記成功，共 ${uids.size} 個籃子，抽樣數量 $sampleQuantity"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "抽樣失敗: ${error.message}"
                    )
                }
            }
        }
    }

    fun dismissSampleQuantityDialog() {
        _uiState.update { it.copy(showSampleQuantityDialog = false) }
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

data class SamplingUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanMode: ScanMode = ScanMode.CONTINUOUS,
    val scannedBaskets: List<Basket> = emptyList(),
    val totalQuantity: Int = 0,
    val showSampleQuantityDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
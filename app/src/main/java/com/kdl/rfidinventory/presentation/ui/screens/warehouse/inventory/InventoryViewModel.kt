package com.kdl.rfidinventory.presentation.ui.screens.warehouse.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.repository.WarehouseRepository
import com.kdl.rfidinventory.util.NetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val warehouseRepository: WarehouseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Connected)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private var allBaskets: List<Basket> = emptyList()

    init {
        loadInventory()
    }

    fun loadInventory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            warehouseRepository.getWarehouseBaskets()
                .onSuccess { baskets ->
                    allBaskets = baskets
                    applyFilter()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statistics = calculateStatistics(baskets)
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

    fun setFilterStatus(status: BasketStatus?) {
        _uiState.update { it.copy(filterStatus = status) }
        applyFilter()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = allBaskets.filter { basket ->
            val matchesStatus = _uiState.value.filterStatus?.let {
                basket.status == it
            } ?: true

            val matchesSearch = _uiState.value.searchQuery.let { query ->
                if (query.isBlank()) true
                else {
                    basket.uid.contains(query, ignoreCase = true) ||
                            basket.product?.name?.contains(query, ignoreCase = true) == true ||
                            basket.batch?.id?.contains(query, ignoreCase = true) == true
                }
            }

            matchesStatus && matchesSearch
        }

        _uiState.update { it.copy(baskets = filtered) }
    }

    private fun calculateStatistics(baskets: List<Basket>): InventoryStatistics {
        val groupedByStatus = baskets.groupBy { it.status }
        return InventoryStatistics(
            totalBaskets = baskets.size,
            inStock = groupedByStatus[BasketStatus.IN_STOCK]?.size ?: 0,
            received = groupedByStatus[BasketStatus.RECEIVED]?.size ?: 0,
            totalQuantity = baskets.sumOf { it.quantity }
        )
    }

    fun exportInventory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }

            // TODO: 實作匯出功能
            kotlinx.coroutines.delay(2000) // 模擬匯出

            _uiState.update {
                it.copy(
                    isExporting = false,
                    successMessage = "盤點報表已匯出"
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }
}

data class InventoryUiState(
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val baskets: List<Basket> = emptyList(),
    val filterStatus: BasketStatus? = null,
    val searchQuery: String = "",
    val statistics: InventoryStatistics = InventoryStatistics(),
    val error: String? = null,
    val successMessage: String? = null
)

data class InventoryStatistics(
    val totalBaskets: Int = 0,
    val inStock: Int = 0,
    val received: Int = 0,
    val totalQuantity: Int = 0
)
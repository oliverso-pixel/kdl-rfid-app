package com.kdl.rfidinventory.presentation.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.repository.AdminRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BasketDetailViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BasketDetailUiState())
    val uiState: StateFlow<BasketDetailUiState> = _uiState.asStateFlow()

    fun loadBasket(uid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                adminRepository.getAllLocalBaskets()
                    .collect { baskets ->
                        val basket = baskets.find { it.uid == uid }
                        _uiState.update {
                            it.copy(
                                basket = basket,
                                isLoading = false,
                                error = if (basket == null) "找不到籃子" else null
                            )
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load basket")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "載入失敗: ${e.message}"
                    )
                }
            }
        }
    }

    fun showDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteBasket() {
        viewModelScope.launch {
            val uid = _uiState.value.basket?.uid ?: return@launch

            adminRepository.deleteLocalBasket(uid)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            showDeleteDialog = false,
                            successMessage = "已刪除籃子: $uid"
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            showDeleteDialog = false,
                            error = "刪除失敗: ${error.message}"
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
}

data class BasketDetailUiState(
    val isLoading: Boolean = false,
    val basket: BasketEntity? = null,
    val showDeleteDialog: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
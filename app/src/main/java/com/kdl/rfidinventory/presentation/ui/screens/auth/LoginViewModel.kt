package com.kdl.rfidinventory.presentation.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.model.User
import com.kdl.rfidinventory.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // 檢查是否已登錄
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        if (authRepository.isLoggedIn()) {
            val user = authRepository.getCurrentUser()
            if (user != null) {
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        currentUser = user
                    )
                }
                Timber.d("✅ User already logged in: ${user.username}")
            }
        }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun login() {
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password

        // 驗證輸入
        if (username.isBlank()) {
            _uiState.update { it.copy(error = "請輸入用戶名") }
            return
        }

        if (password.isBlank()) {
            _uiState.update { it.copy(error = "請輸入密碼") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            authRepository.login(username, password)
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            currentUser = user,
                            password = "" // 清除密碼
                        )
                    }
                    Timber.d("✅ Login successful: ${user.username}")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "登錄失敗: ${error.message}"
                        )
                    }
                    Timber.e(error, "❌ Login failed")
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            authRepository.logout()
                .onSuccess {
                    _uiState.update {
                        LoginUiState() // 重置所有狀態
                    }
                    Timber.d("✅ Logout successful")
                }
                .onFailure { error ->
                    Timber.e(error, "❌ Logout failed")
                    // 即使 API 失敗，也清除本地數據
                    _uiState.update {
                        LoginUiState()
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currentUser: User? = null,
    val error: String? = null
)
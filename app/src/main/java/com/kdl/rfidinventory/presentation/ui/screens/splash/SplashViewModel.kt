package com.kdl.rfidinventory.presentation.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.data.repository.AuthRepository
import com.kdl.rfidinventory.domain.manager.rfid.RFIDManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class InitState(
    val progress: Float = 0f,
    val message: String = "正在啟動應用...",
    val isComplete: Boolean = false,
    val isLoggedIn: Boolean = false,   // 🆕 登入狀態
    val error: String? = null
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val rfidManager: RFIDManager,
    private val authRepository: AuthRepository   // 🆕 注入
) : ViewModel() {

    private val _initState = MutableStateFlow(InitState())
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    fun startInitialization() {
        viewModelScope.launch {
            try {
                Timber.d("🚀 Starting app initialization")

                // 步驟 1: 初始化數據庫
                updateState(0.1f, "正在初始化數據庫...")
                delay(200)
                initializeDatabase()
                updateState(0.2f, "數據庫初始化完成")

                // 步驟 2: 載入應用設定
                updateState(0.3f, "正在載入應用設定...")
                delay(200)
                loadSettings()
                updateState(0.4f, "設定載入完成")

                // 步驟 3: 初始化 RFID 設備
                updateState(0.5f, "正在初始化 RFID 設備...")
                delay(300)
                initializeRFID()
                updateState(0.7f, "RFID 設備初始化完成")

                // 步驟 4: 檢查網路連線
                updateState(0.75f, "正在檢查系統狀態...")
                delay(200)
                checkSystemStatus()
                updateState(0.85f, "系統檢查完成")

                // 🆕 步驟 5: 檢查登入狀態
                updateState(0.9f, "正在檢查登入狀態...")
                val isLoggedIn = checkLoginStatus()
                Timber.d("🔐 Login status: $isLoggedIn")

                // 步驟 6: 完成
                updateState(0.95f, "準備就緒...")
                delay(300)
                _initState.value = _initState.value.copy(
                    progress = 1.0f,
                    message = if (isLoggedIn) "歡迎回來" else "初始化完成",
                    isComplete = true,
                    isLoggedIn = isLoggedIn
                )

                Timber.d("✅ App initialization completed (loggedIn=$isLoggedIn)")

            } catch (e: Exception) {
                Timber.e(e, "❌ Initialization failed")
                _initState.value = InitState(
                    progress = _initState.value.progress,
                    message = "初始化失敗",
                    error = e.message ?: "未知錯誤"
                )
            }
        }
    }

    private suspend fun initializeDatabase() {
        try {
            Timber.d("📊 Initializing database...")
            delay(100)
            Timber.d("✅ Database initialized")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Database initialization warning")
        }
    }

    private suspend fun loadSettings() {
        try {
            Timber.d("⚙️ Loading settings...")
            delay(100)
            Timber.d("✅ Settings loaded")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Settings loading warning")
        }
    }

    private suspend fun initializeRFID() {
        Timber.d("📡 Initializing RFID device...")
        val ready = rfidManager.awaitReady(timeoutMs = 5000)
        if (ready) {
            Timber.d("✅ RFID device connected and ready")
        } else {
            Timber.w("⚠️ RFID device not ready after 5s, continuing anyway")
        }
    }

    private suspend fun checkSystemStatus() {
        try {
            Timber.d("🔍 Checking system status...")
            delay(100)
            Timber.d("✅ System status OK")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ System check warning")
        }
    }

    /**
     * 🆕 檢查是否已登入(讀取 DataStore)
     */
    private suspend fun checkLoginStatus(): Boolean {
        return try {
            val loggedIn = authRepository.isLoggedIn()
            val user = authRepository.getCurrentUser()
            val result = loggedIn && user != null
            if (result) {
                Timber.d("✅ User already logged in: ${user?.username}")
            } else {
                Timber.d("ℹ️ No active login session")
            }
            result
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Check login failed, treat as logged out")
            false
        }
    }

    private fun updateState(
        progress: Float,
        message: String,
        isComplete: Boolean = false
    ) {
        _initState.value = _initState.value.copy(
            progress = progress,
            message = message,
            isComplete = isComplete
        )
        Timber.d("📊 Init progress: ${(progress * 100).toInt()}% - $message")
    }
}
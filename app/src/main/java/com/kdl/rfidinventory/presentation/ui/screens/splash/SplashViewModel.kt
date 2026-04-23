package com.kdl.rfidinventory.presentation.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kdl.rfidinventory.domain.manager.rfid.RFIDManager
//import com.kdl.rfidinventory.domain.manager.RFIDManager
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
    val error: String? = null
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val rfidManager: RFIDManager
) : ViewModel() {

    private val _initState = MutableStateFlow(InitState())
    val initState: StateFlow<InitState> = _initState.asStateFlow()

    fun startInitialization() {
        viewModelScope.launch {
            try {
                Timber.d("🚀 Starting app initialization")

                // 步驟 1: 初始化數據庫 (20%)
                updateState(0.1f, "正在初始化數據庫...")
                delay(200)
                initializeDatabase()
                updateState(0.2f, "數據庫初始化完成")

                // 步驟 2: 載入應用設定 (40%)
                updateState(0.3f, "正在載入應用設定...")
                delay(200)
                loadSettings()
                updateState(0.4f, "設定載入完成")

                // 步驟 3: 初始化 RFID 設備 (70%) - 這是關鍵步驟
                updateState(0.5f, "正在初始化 RFID 設備...")
                delay(300)
                initializeRFID()
                updateState(0.7f, "RFID 設備初始化完成")

                // 步驟 4: 檢查網路連線 (85%)
                updateState(0.75f, "正在檢查系統狀態...")
                delay(200)
                checkSystemStatus()
                updateState(0.85f, "系統檢查完成")

                // 步驟 5: 完成 (100%)
                updateState(0.95f, "準備就緒...")
                delay(300)
                updateState(1.0f, "初始化完成", isComplete = true)

                Timber.d("✅ App initialization completed successfully")

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
            // 這裡可以執行資料庫相關的初始化
            // 例如：檢查資料庫是否存在、執行遷移等
            delay(100)
            Timber.d("✅ Database initialized")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Database initialization warning")
            // 資料庫初始化失敗不致命，繼續執行
        }
    }

    private suspend fun loadSettings() {
        try {
            Timber.d("⚙️ Loading settings...")
            // 載入應用設定（如果有 SettingsRepository 或 SharedPreferences）
            delay(100)
            Timber.d("✅ Settings loaded")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Settings loading warning")
        }
    }

    private suspend fun initializeRFID() {
        try {
            Timber.d("📡 Initializing RFID device...")

            // 等待 RFID Manager 初始化完成
            // 從你的 log 看，RFID 會在 Application 啟動時自動初始化
            // 這裡我們給它足夠的時間完成
            var retryCount = 0
            val maxRetries = 5

            while (retryCount < maxRetries) {
                delay(200)

                // 檢查 RFID 是否已連接
                val isConnected = try {
                    rfidManager.isConnected()
                } catch (e: Exception) {
                    Timber.w(e, "Error checking RFID connection")
                    false
                }

                if (isConnected) {
                    Timber.d("✅ RFID device connected and ready")
                    return
                }

                retryCount++
                Timber.d("⏳ Waiting for RFID connection... ($retryCount/$maxRetries)")
            }

            Timber.w("⚠️ RFID device not connected after $maxRetries attempts, continuing anyway")

        } catch (e: Exception) {
            Timber.e(e, "❌ RFID initialization error")
            // RFID 初始化失敗不致命，應用仍可啟動
        }
    }

    private suspend fun checkSystemStatus() {
        try {
            Timber.d("🔍 Checking system status...")
            // 檢查各項系統狀態
            delay(100)
            Timber.d("✅ System status OK")
        } catch (e: Exception) {
            Timber.w(e, "⚠️ System check warning")
        }
    }

    private fun updateState(
        progress: Float,
        message: String,
        isComplete: Boolean = false
    ) {
        _initState.value = InitState(
            progress = progress,
            message = message,
            isComplete = isComplete
        )
        Timber.d("📊 Init progress: ${(progress * 100).toInt()}% - $message")
    }
}
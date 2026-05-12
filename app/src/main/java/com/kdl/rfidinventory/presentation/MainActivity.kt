package com.kdl.rfidinventory.presentation

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.kdl.rfidinventory.data.repository.DeviceRepository
import com.kdl.rfidinventory.data.repository.LoadingRepository
import com.kdl.rfidinventory.data.remote.websocket.WebSocketManager
import com.kdl.rfidinventory.presentation.navigation.NavGraph
import com.kdl.rfidinventory.presentation.ui.screens.splash.SplashScreen
import com.kdl.rfidinventory.presentation.ui.theme.RFIDInventoryTheme
import com.kdl.rfidinventory.util.KeyEventHandler
import com.kdl.rfidinventory.util.ScreenBrightnessManager
import com.kdl.rfidinventory.domain.manager.barcode.BarcodeScanManager
import com.kdl.rfidinventory.domain.manager.rfid.RFIDManager
import com.kdl.rfidinventory.util.PowerState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var keyEventHandler: KeyEventHandler

    @Inject
    lateinit var barcodeScanManager: BarcodeScanManager

    @Inject
    lateinit var rfidManager: RFIDManager

    @Inject
    lateinit var loadingRepository: LoadingRepository

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var webSocketManager: WebSocketManager

    @Inject
    lateinit var screenBrightnessManager: ScreenBrightnessManager

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        window.decorView.post {
//            hideSystemUI()
//        }

        // 初始化屏幕亮度管理
        screenBrightnessManager.initialize(this)
        screenBrightnessManager.resetTimer()

        // 監聽屏幕 dim 狀態，自動暫停/恢復硬體服務
        lifecycleScope.launch {
            screenBrightnessManager.powerState.collect { state ->
                handlePowerStateChange(state)
            }
        }

        // 啟動時註冊設備
        lifecycleScope.launch {
            registerDeviceAndConnect()
        }

        setContent {
            RFIDInventoryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }

        // 初始化 Loading Repository
        lifecycleScope.launch {
            loadingRepository.initialize()
        }
    }

    /**
     * 隱藏系統 UI（導航欄和狀態欄）
     */
    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.apply {
                    hide(WindowInsets.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                // Android 10 及以下
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or View.SYSTEM_UI_FLAG_FULLSCREEN
                        )
            }
            Timber.d("🖥️ System UI hidden successfully")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to hide system UI")
        }
    }

    /**
     *  當窗口獲得焦點時重新隱藏系統 UI
     * （防止用戶從屏幕邊緣滑動後導航欄重新出現）
     */
//    override fun onWindowFocusChanged(hasFocus: Boolean) {
//        super.onWindowFocusChanged(hasFocus)
//        if (hasFocus) {
//            hideSystemUI()
//        }
//    }

    /**
     * 處理分級電源狀態變化
     */
    private fun handlePowerStateChange(state: PowerState) {
        Timber.d("🔋 Power state changed: $state")
        when (state) {
            PowerState.FULL_ACTIVE -> {
                // 恢復所有硬體
                barcodeScanManager.resume()
                rfidManager.resumeHardware()
            }
            PowerState.HARDWARE_IDLE -> {
                // 只暫停 barcode（最常發熱）
                barcodeScanManager.pause()
                // RFID 保持但降功率
                rfidManager.pauseHardware()
            }
            PowerState.SCREEN_DIMMED, PowerState.DEEP_SLEEP -> {
                // 全部暫停
                barcodeScanManager.pause()
                rfidManager.pauseHardware()
            }
        }
    }

    /**
     * 註冊設備並連接 WebSocket
     */
    private suspend fun registerDeviceAndConnect() {
        Timber.d("📱 Starting device registration...")

        deviceRepository.registerDevice()
            .onSuccess { deviceInfo ->
                Timber.d("✅ Device registered successfully")
                Timber.d("   Device ID: ${deviceInfo.deviceId}")
                Timber.d("   Name: ${deviceInfo.name}")
                Timber.d("   Model: ${deviceInfo.model}")
                Timber.d("   OS: ${deviceInfo.osVersion}")
                Timber.d("   IP: ${deviceInfo.ipAddress}")

                // 註冊成功後連接 WebSocket
                webSocketManager.connect()
            }
            .onFailure { error ->
                Timber.e(error, "❌ Device registration failed")
                // 即使註冊失敗，也可以嘗試連接 WebSocket（使用本地 Device ID）
                webSocketManager.connect()
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    private fun AppContent() {
        var isInitialized by remember { mutableStateOf(false) }

        if (!isInitialized) {
            // 顯示啟動畫面
            SplashScreen(
                onInitComplete = {
                    isInitialized = true
                    Timber.d("✅ App initialization complete")
                }
            )
        } else {
            // 顯示主應用程式
            val navController = rememberNavController()
            NavGraph(navController = navController)
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        screenBrightnessManager.resetTimer()

        // 如果剛從 dim 狀態喚醒，給硬體一點時間重新初始化
        // 第一次按鍵只用於喚醒，不處理實際邏輯
        // （這個判斷可選，取決於用戶體驗偏好）

        val keyCode = event.keyCode
        Timber.d("🎯 Activity dispatchKeyEvent: keyCode=$keyCode, action=${event.action}")

//        if (barcodeScanManager.handleKeyEvent(keyCode, event)) {
//            return true
//        }
        if (barcodeScanManager.hardwareState.value == BarcodeScanManager.HardwareState.WARMING_UP) {
            Timber.v("🔥 Hardware warming up, consuming event")
            return true
        }

        lifecycleScope.launch {
            keyEventHandler.handleKeyEvent(keyCode, event)
        }

        return super.dispatchKeyEvent(event)
    }

    // 監聽觸摸事件
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            screenBrightnessManager.resetTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        screenBrightnessManager.resetTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        screenBrightnessManager.release()

        keyEventHandler.reset()
        barcodeScanManager.release()
        rfidManager.release()
        webSocketManager.cleanup()
        webSocketManager.disconnect()
        Timber.d("🛑 MainActivity destroyed")
    }
}
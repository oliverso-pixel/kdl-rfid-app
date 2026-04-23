package com.kdl.rfidinventory.presentation

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        screenBrightnessManager.initialize(this)
//        screenBrightnessManager.resetTimer()

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

        val keyCode = event.keyCode
        Timber.d("🎯 Activity dispatchKeyEvent: keyCode=$keyCode, action=${event.action}")

        if (barcodeScanManager.handleKeyEvent(keyCode, event)) {
            Timber.d("✅ Key event handled by BarcodeScanManager")
            return true
        }

        lifecycleScope.launch {
            if (keyEventHandler.handleKeyEvent(keyCode, event)) {
                Timber.d("✅ Key event handled by KeyEventHandler")
            } else {
                Timber.v("⏭️ Key event not handled")
            }
        }

        return super.dispatchKeyEvent(event)
    }

    // 監聽觸摸事件
//    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
//        // 用戶觸摸屏幕，重置計時器
//        if (ev.action == MotionEvent.ACTION_DOWN) {
//            screenBrightnessManager.resetTimer()
//        }
//        return super.dispatchTouchEvent(ev)
//    }

//    override fun onResume() {
//        super.onResume()
//        hideSystemUI()
//        // Activity 恢覆時重置計時器
//        screenBrightnessManager.resetTimer()
//    }

    override fun onDestroy() {
        super.onDestroy()

//        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        screenBrightnessManager.release()

        keyEventHandler.reset()
        barcodeScanManager.release()
        webSocketManager.cleanup()
        webSocketManager.disconnect()
        Timber.d("🛑 MainActivity destroyed")
    }
}
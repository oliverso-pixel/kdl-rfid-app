package com.kdl.rfidinventory.presentation

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
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
import com.kdl.rfidinventory.util.barcode.BarcodeScanManager
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 📱 啟動時註冊設備
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

    override fun onDestroy() {
        super.onDestroy()
        keyEventHandler.reset()
        barcodeScanManager.release()
        webSocketManager.cleanup()
        webSocketManager.disconnect()
        Timber.d("🛑 MainActivity destroyed")
    }
}
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
import com.kdl.rfidinventory.presentation.navigation.NavGraph
import com.kdl.rfidinventory.presentation.ui.screens.splash.SplashScreen
import com.kdl.rfidinventory.presentation.ui.theme.RFIDInventoryTheme
import com.kdl.rfidinventory.util.KeyEventHandler
import com.kdl.rfidinventory.util.barcode.BarcodeScanManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var keyEventHandler: KeyEventHandler

    @Inject
    lateinit var barcodeScanManager: BarcodeScanManager

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // ⭐ 優先處理條碼 KeyEvent（必須在 KeyEventHandler 之前）
        if (barcodeScanManager.handleKeyEvent(keyCode, event)) {
            Timber.d("✅ Key event handled by BarcodeScanManager")
            return true  // ⭐ 返回 true，防止事件繼續傳遞
        }

        // 處理掃描觸發鍵
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
        Timber.d("🛑 MainActivity destroyed")
    }
}
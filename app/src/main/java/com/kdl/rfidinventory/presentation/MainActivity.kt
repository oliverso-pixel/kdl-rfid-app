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
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.kdl.rfidinventory.presentation.navigation.NavGraph
import com.kdl.rfidinventory.presentation.ui.theme.RFIDInventoryTheme
import com.kdl.rfidinventory.util.KeyEventHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var keyEventHandler: KeyEventHandler

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(Timber.DebugTree())

        setContent {
            RFIDInventoryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }

    /**
     * 攔截按鍵事件
     */
//    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
//        event ?: return super.onKeyDown(keyCode, event)
//
//        if (keyEventHandler.isEnabled) {
//            lifecycleScope.launch {
//                val handled = keyEventHandler.handleKeyEvent(keyCode, event)
//                if (!handled) {
//                    // 如果按鍵事件未處理，交給父類處理
//                    this@MainActivity.runOnUiThread {
//                        super.onKeyDown(keyCode, event)
//                    }
//                }
//            }
//            return true
//        }
//
//        return super.onKeyDown(keyCode, event)
//    }

//    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
//        event ?: return super.onKeyUp(keyCode, event)
//
//        if (keyEventHandler.isEnabled) {
//            lifecycleScope.launch {
//                val handled = keyEventHandler.handleKeyEvent(keyCode, event)
//                if (!handled) {
//                    this@MainActivity.runOnUiThread {
//                        super.onKeyUp(keyCode, event)
//                    }
//                }
//            }
//            return true
//        }
//
//        return super.onKeyUp(keyCode, event)
//    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Timber.d("🎯 Activity dispatchKeyEvent: keyCode=${event.keyCode}, action=${event.action}")

        // 使用協程處理按鍵事件
        lifecycleScope.launch {
            val handled = keyEventHandler.handleKeyEvent(event.keyCode, event)
            if (handled) {
                Timber.d("✅ Key event handled by KeyEventHandler")
                return@launch
            }
        }

        // 如果未處理，則使用默認處理
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        keyEventHandler.reset()
        Timber.d("🛑 MainActivity destroyed")
    }
}
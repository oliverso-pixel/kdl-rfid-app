package com.kdl.rfidinventory.util

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

sealed class ScanTriggerEvent {
    object StartScan : ScanTriggerEvent() {
        override fun toString() = "StartScan"
    }
    object StopScan : ScanTriggerEvent() {
        override fun toString() = "StopScan"
    }
    object ClearList : ScanTriggerEvent() {
        override fun toString() = "ClearList"
    }
}

@Singleton
class KeyEventHandler @Inject constructor() {
    private val _scanTriggerEvents = MutableSharedFlow<ScanTriggerEvent>(
        replay = 0,
        extraBufferCapacity = 10  // 增加緩衝區防止事件丟失
    )
    val scanTriggerEvents: SharedFlow<ScanTriggerEvent> = _scanTriggerEvents.asSharedFlow()

    private var isKeyPressed = false

    companion object {
        // 掃描觸發鍵列表
        private val SCAN_TRIGGER_KEYS = setOf(
            520,
            521,
            523,
            KeyEvent.KEYCODE_F1,
            KeyEvent.KEYCODE_F2,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_VOLUME_DOWN  // 測試用
        )

        // 清除列表鍵
        private val CLEAR_LIST_KEYS = setOf(
            KeyEvent.KEYCODE_F3,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_VOLUME_UP  // 測試用
        )
    }

    /**
     * 處理按鍵事件
     * @return true 如果事件已處理，false 否則
     */
    suspend fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        Timber.d("📱 KeyEvent received: keyCode=$keyCode, action=${event.action}, isPressed=$isKeyPressed")

        return when {
            keyCode in SCAN_TRIGGER_KEYS -> {
                handleScanTrigger(event)
            }

            keyCode in CLEAR_LIST_KEYS -> {
                handleClearList(event)
            }

            else -> {
                Timber.v("⏭️ Unhandled keyCode: $keyCode")
                false
            }
        }
    }

    private suspend fun handleScanTrigger(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!isKeyPressed) {
                    isKeyPressed = true
                    Timber.d("✅ Emitting StartScan event")
                    val emitted = _scanTriggerEvents.tryEmit(ScanTriggerEvent.StartScan)
                    if (!emitted) {
                        Timber.e("❌ Failed to emit StartScan event")
                    }
                } else {
                    Timber.d("⏭️ Key already pressed, ignoring")
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                if (isKeyPressed) {
                    isKeyPressed = false
                    Timber.d("✅ Emitting StopScan event")
                    val emitted = _scanTriggerEvents.tryEmit(ScanTriggerEvent.StopScan)
                    if (!emitted) {
                        Timber.e("❌ Failed to emit StopScan event")
                    }
                } else {
                    Timber.d("⏭️ Key not pressed, ignoring release")
                }
                true
            }
            else -> {
                Timber.d("⚠️ Unknown action: ${event.action}")
                false
            }
        }
    }

    private suspend fun handleClearList(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            Timber.d("✅ Emitting ClearList event")
            val emitted = _scanTriggerEvents.tryEmit(ScanTriggerEvent.ClearList)
            if (!emitted) {
                Timber.e("❌ Failed to emit ClearList event")
            }
            return true
        }
        return false
    }

    fun reset() {
        isKeyPressed = false
        Timber.d("🔄 KeyEventHandler reset")
    }
}
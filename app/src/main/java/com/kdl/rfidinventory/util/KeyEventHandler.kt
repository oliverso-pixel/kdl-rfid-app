package com.kdl.rfidinventory.util

import android.view.KeyEvent
import kotlinx.coroutines.channels.BufferOverflow
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
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val scanTriggerEvents: SharedFlow<ScanTriggerEvent> = _scanTriggerEvents.asSharedFlow()
    private var isKeyPressed = false

    companion object {
        // ⭐ 掃描觸發鍵列表
        private val SCAN_TRIGGER_KEYS = setOf(
            520,
            521,
            523,
            KeyEvent.KEYCODE_F1,
            KeyEvent.KEYCODE_F2,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1
        )

        // ⭐ 條碼數據鍵（不應該觸發掃描）
        private val BARCODE_DATA_KEYS = setOf(
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_SPACE
        ) + (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toSet()

        // 清除列表鍵
        private val CLEAR_LIST_KEYS = setOf(
            KeyEvent.KEYCODE_F3,
            KeyEvent.KEYCODE_BUTTON_X
        )
    }

    /**
     * 處理按鍵事件
     * @return true 如果事件已處理，false 否則
     */
    suspend fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        Timber.d("📱 KeyEvent received: keyCode=$keyCode, action=${event.action}")

        // ⭐ 忽略條碼數據鍵（讓 BarcodeScanManager 處理）
        if (keyCode in BARCODE_DATA_KEYS) {
            Timber.v("⏭️ Barcode data key, ignoring: $keyCode")
            return false
        }

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
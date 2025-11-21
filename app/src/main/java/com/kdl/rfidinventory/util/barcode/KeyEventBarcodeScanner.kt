package com.kdl.rfidinventory.util.barcode

import android.view.KeyEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 KeyEvent 的条码扫描器
 * 当设备不支持广播时的备用方案
 */
@Singleton
class KeyEventBarcodeScanner @Inject constructor() {

    private val barcodeBuffer = StringBuilder()
    private var lastKeyTime = 0L

    companion object {
        private const val SCAN_TIMEOUT_MS = 100L // 按键间隔超过 100ms 视为新的扫描
    }

    /**
     * 处理按键事件
     * @return 如果是条码扫描的一部分返回 true，否则返回 false
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        val currentTime = System.currentTimeMillis()

        // 检查是否超时（新的扫描开始）
        if (currentTime - lastKeyTime > SCAN_TIMEOUT_MS) {
            barcodeBuffer.clear()
        }

        lastKeyTime = currentTime

        // 处理按键
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                // Enter 键表示扫描结束
                if (barcodeBuffer.isNotEmpty()) {
                    val barcode = barcodeBuffer.toString()
                    Timber.d("📱 Barcode scanned via KeyEvent: $barcode")
                    barcodeBuffer.clear()
                    return true
                }
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                // 数字键
                val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
                barcodeBuffer.append(digit)
                return true
            }
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                // 字母键
                val char = ('A' + (keyCode - KeyEvent.KEYCODE_A))
                barcodeBuffer.append(char)
                return true
            }
            KeyEvent.KEYCODE_MINUS -> {
                barcodeBuffer.append('-')
                return true
            }
            KeyEvent.KEYCODE_PERIOD -> {
                barcodeBuffer.append('.')
                return true
            }
        }

        return false
    }

    /**
     * 获取当前缓冲区内容
     */
    fun getCurrentBarcode(): String {
        return barcodeBuffer.toString()
    }

    /**
     * 清空缓冲区
     */
    fun clear() {
        barcodeBuffer.clear()
        lastKeyTime = 0L
    }
}
package com.kdl.rfidinventory.util.rfid

import android.content.Context
import com.kdl.rfidinventory.util.barcode.BarcodeScanManager
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.SoundTool
import com.ubx.usdk.USDKManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import com.ubx.usdk.rfid.RfidManager as UsdkRfidManager

/**
 * RFID 管理器 - 真實硬體 SDK (USDK) 版本
 */
@Singleton
class RFIDManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val barcodeScanManager: BarcodeScanManager
) {
    private var mRfidManager: UsdkRfidManager? = null
    private var isSdkInitialized = false
    private var currentCallback: RfidCallbackBridge? = null

    companion object {
        private const val COM_PORT = "/dev/ttyHSL0"
        private const val BAUD_RATE = 115200
    }

    init {
        Timber.d("RFIDManager initializing...")
        initializeSDK()
    }

    private fun initializeSDK() {
        try {
            // 初始化音效工具
            SoundTool.getInstance(context)

            // 初始化 USDK
            USDKManager.getInstance().init(context) { status ->
                when (status) {
                    USDKManager.STATUS.SUCCESS -> {
                        mRfidManager = USDKManager.getInstance().rfidManager

                        if (mRfidManager == null) {
                            Timber.e("Failed to get RfidManager instance")
                            return@init
                        }

                        // 連接串口
                        if (mRfidManager?.connectCom(COM_PORT, BAUD_RATE) == true) {
                            isSdkInitialized = true
                            Timber.i("✅ RFID SDK initialized successfully")

                            // 獲取固件版本
                            mRfidManager?.getFirmwareVersion(mRfidManager?.readId ?: 0)
                        } else {
                            Timber.e("❌ Failed to connect COM port: $COM_PORT")
                        }
                    }
                    else -> {
                        Timber.e("❌ USDKManager initialization failed: $status")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during SDK initialization")
        }
    }

    /**
     * 條碼掃描（單次觸發）
     * 返回條碼字符串
     */
    fun startBarcodeScan(): Flow<String> {
        Timber.d("🔍 Starting barcode scan...")
        return barcodeScanManager.startScan()
            .map { barcodeData ->
                Timber.d("📱 Barcode: ${barcodeData.content}, Format: ${barcodeData.format}")
                barcodeData.content
            }
    }

    /**
     * 開始 RFID 掃描（連續模式）
     */
    fun startScan(mode: ScanMode): Flow<RFIDTag> = callbackFlow {
        if (!isSdkInitialized || mRfidManager == null) {
            Timber.e("SDK not initialized")
            close(IllegalStateException("RFID SDK 未初始化"))
            return@callbackFlow
        }

        val seenTags = ConcurrentHashMap.newKeySet<String>()
        val isContinuous = mode == ScanMode.CONTINUOUS

        // 創建 Java 回調橋接
        val dataListener = object : RfidDataListener {
            override fun onTagScanned(tag: RFIDTag) {
                Timber.d("📡 Tag scanned: ${tag.uid}")
                trySend(tag).isSuccess
            }

            override fun onScanEnded() {
                Timber.d("🛑 Scan ended")
                close()
            }
        }

        currentCallback = RfidCallbackBridge(
            dataListener,
            context,
            seenTags,
            isContinuous
        )

        // 註冊回調
        mRfidManager?.registerCallback(currentCallback)

        // 開始掃描
        try {
            val readId = mRfidManager?.readId ?: 0
            val result = mRfidManager?.customizedSessionTargetInventory(
                readId,
                1.toByte(),  // Session
                0.toByte(),  // Target
                1.toByte()   // Scan time (單位: 秒)
            )

            Timber.i("🚀 Scan started - Mode: $mode, Result: $result")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start scan")
            close(e)
        }

        // Flow 關閉時清理
        awaitClose {
            Timber.d("Closing scan flow...")
            stopInventory()
            if (currentCallback != null) {
                mRfidManager?.unregisterCallback(currentCallback)
                currentCallback = null
            }
        }
    }

    /**
     * 停止掃描
     */
    fun stopScan() {
        Timber.d("stopScan called")
        stopInventory()
    }

    private fun stopInventory() {
        try {
            mRfidManager?.stopInventory()
            Timber.d("Inventory stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping inventory")
        }
    }

    /**
     * 寫入標籤 EPC
     */
    suspend fun writeUid(newUid: String, password: String = "00000000"): Result<Unit> {
        return try {
            if (!isSdkInitialized || mRfidManager == null) {
                return Result.failure(IllegalStateException("SDK not initialized"))
            }

            val readId = mRfidManager?.readId ?: 0
            val result = mRfidManager?.writeEpcString(readId, newUid, password)

            if (result == 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Write failed with code: $result"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error writing UID")
            Result.failure(e)
        }
    }

    /**
     * 設定功率
     */
    suspend fun setPower(power: Int): Result<Unit> {
        return try {
            if (!isSdkInitialized || mRfidManager == null) {
                return Result.failure(IllegalStateException("SDK not initialized"))
            }

            if (power < 0 || power > 33) {
                return Result.failure(IllegalArgumentException("Power must be 0-33"))
            }

            val readId = mRfidManager?.readId ?: 0
            mRfidManager?.setOutputPower(readId, power.toByte())

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error setting power")
            Result.failure(e)
        }
    }

    fun isConnected(): Boolean {
        return try {
            // 根據你的實際 RFID SDK 實作
            // 例如：rfidDevice?.isConnected() ?: false
            true // 暫時返回 true，請根據實際情況修改
        } catch (e: Exception) {
            Timber.e(e, "Error checking RFID connection")
            false
        }
    }

    /**
     * 釋放資源
     */
    fun release() {
        Timber.i("Releasing RFIDManager resources")

        try {
            stopInventory()

            if (currentCallback != null) {
                mRfidManager?.unregisterCallback(currentCallback)
                currentCallback = null
            }

            mRfidManager?.disConnect()
            mRfidManager?.release()
            mRfidManager = null

            SoundTool.getInstance(context)?.release()

            isSdkInitialized = false
            Timber.i("✅ Resources released successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing resources")
        }
    }
}
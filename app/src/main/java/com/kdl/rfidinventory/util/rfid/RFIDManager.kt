package com.kdl.rfidinventory.util.rfid

import android.content.Context
import com.kdl.rfidinventory.util.barcode.BarcodeScanManager
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.SoundTool
import com.ubx.usdk.RFIDSDKManager
import com.ubx.usdk.USDKManager
import com.ubx.usdk.bean.RfidParameter
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
//@Singleton
//class RFIDManager @Inject constructor(
//    @ApplicationContext private val context: Context,
//    private val barcodeScanManager: BarcodeScanManager
//) {
//    private var mRfidManager: UsdkRfidManager? = null
//    private var isSdkInitialized = false
//    private var currentCallback: RfidCallbackBridge? = null
//
//    companion object {
//        private const val COM_PORT = "/dev/ttyHSL0"
//        private const val BAUD_RATE = 115200
//    }
//
//    init {
//        Timber.d("RFIDManager initializing...")
//        initializeSDK()
//    }
//
//    private fun initializeSDK() {
//        try {
//            // 初始化音效工具
//            SoundTool.getInstance(context)
//
//            // 初始化 USDK
//            USDKManager.getInstance().init(context) { status ->
//                when (status) {
//                    USDKManager.STATUS.SUCCESS -> {
//                        mRfidManager = USDKManager.getInstance().rfidManager
//
//                        if (mRfidManager == null) {
//                            Timber.e("Failed to get RfidManager instance")
//                            return@init
//                        }
//
//                        // 連接串口
//                        if (mRfidManager?.connectCom(COM_PORT, BAUD_RATE) == true) {
//                            isSdkInitialized = true
//                            Timber.i("✅ RFID SDK initialized successfully")
//
//                            // 獲取固件版本
//                            mRfidManager?.getFirmwareVersion(mRfidManager?.readId ?: 0)
//                        } else {
//                            Timber.e("❌ Failed to connect COM port: $COM_PORT")
//                        }
//                    }
//                    else -> {
//                        Timber.e("❌ USDKManager initialization failed: $status")
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            Timber.e(e, "Exception during SDK initialization")
//        }
//    }
//
//    /**
//     * 條碼掃描（單次觸發）
//     * 返回條碼字符串
//     */
//    fun startBarcodeScan(): Flow<String> {
//        Timber.d("🔍 Starting barcode scan...")
//        return barcodeScanManager.startScan()
//            .map { barcodeData ->
//                Timber.d("📱 Barcode: ${barcodeData.content}, Format: ${barcodeData.format}")
//                barcodeData.content
//            }
//    }
//
//    /**
//     * 開始 RFID 掃描（連續模式）
//     */
//    fun startScan(mode: ScanMode): Flow<RFIDTag> = callbackFlow {
//        if (!isSdkInitialized || mRfidManager == null) {
//            Timber.e("SDK not initialized")
//            close(IllegalStateException("RFID SDK 未初始化"))
//            return@callbackFlow
//        }
//
//        val seenTags = ConcurrentHashMap.newKeySet<String>()
//        val isContinuous = mode == ScanMode.CONTINUOUS
//
//        // 創建 Java 回調橋接
//        val dataListener = object : RfidDataListener {
//            override fun onTagScanned(tag: RFIDTag) {
//                Timber.d("📡 Tag scanned: ${tag.uid}")
//                trySend(tag).isSuccess
//            }
//
//            override fun onScanEnded() {
//                Timber.d("🛑 Scan ended")
//                close()
//            }
//        }
//
//        currentCallback = RfidCallbackBridge(
//            dataListener,
//            context,
//            seenTags,
//            isContinuous
//        )
//
//        // 註冊回調
//        mRfidManager?.registerCallback(currentCallback)
//
//        // 開始掃描
//        try {
//            val readId = mRfidManager?.readId ?: 0
//            val result = mRfidManager?.customizedSessionTargetInventory(
//                readId,
//                1.toByte(),  // Session
//                0.toByte(),  // Target
//                1.toByte()   // Scan time (單位: 秒)
//            )
//
//            Timber.i("🚀 Scan started - Mode: $mode, Result: $result")
//        } catch (e: Exception) {
//            Timber.e(e, "Failed to start scan")
//            close(e)
//        }
//
//        // Flow 關閉時清理
//        awaitClose {
//            Timber.d("Closing scan flow...")
//            stopInventory()
//            if (currentCallback != null) {
//                mRfidManager?.unregisterCallback(currentCallback)
//                currentCallback = null
//            }
//        }
//    }
//
//    /**
//     * 停止掃描
//     */
//    fun stopScan() {
//        Timber.d("stopScan called")
//        stopInventory()
//    }
//
//    private fun stopInventory() {
//        try {
//            mRfidManager?.stopInventory()
//            Timber.d("Inventory stopped")
//        } catch (e: Exception) {
//            Timber.e(e, "Error stopping inventory")
//        }
//    }
//
//    /**
//     * 寫入標籤 EPC
//     */
//    suspend fun writeUid(newUid: String, password: String = "00000000"): Result<Unit> {
//        return try {
//            if (!isSdkInitialized || mRfidManager == null) {
//                return Result.failure(IllegalStateException("SDK not initialized"))
//            }
//
//            val readId = mRfidManager?.readId ?: 0
//            val result = mRfidManager?.writeEpcString(readId, newUid, password)
//
//            if (result == 0) {
//                Result.success(Unit)
//            } else {
//                Result.failure(Exception("Write failed with code: $result"))
//            }
//        } catch (e: Exception) {
//            Timber.e(e, "Error writing UID")
//            Result.failure(e)
//        }
//    }
//
//    /**
//     * 設定功率
//     */
//    suspend fun setPower(power: Int): Result<Unit> {
//        return try {
//            if (!isSdkInitialized || mRfidManager == null) {
//                return Result.failure(IllegalStateException("SDK not initialized"))
//            }
//
//            if (power < 0 || power > 33) {
//                return Result.failure(IllegalArgumentException("Power must be 0-33"))
//            }
//
//            val readId = mRfidManager?.readId ?: 0
//            mRfidManager?.setOutputPower(readId, power.toByte())
//
//            Result.success(Unit)
//        } catch (e: Exception) {
//            Timber.e(e, "Error setting power")
//            Result.failure(e)
//        }
//    }
//
//    fun isConnected(): Boolean {
//        return try {
//            // 根據你的實際 RFID SDK 實作
//            // 例如：rfidDevice?.isConnected() ?: false
//            true // 暫時返回 true，請根據實際情況修改
//        } catch (e: Exception) {
//            Timber.e(e, "Error checking RFID connection")
//            false
//        }
//    }
//
//    /**
//     * 釋放資源
//     */
//    fun release() {
//        Timber.i("Releasing RFIDManager resources")
//
//        try {
//            stopInventory()
//
//            if (currentCallback != null) {
//                mRfidManager?.unregisterCallback(currentCallback)
//                currentCallback = null
//            }
//
//            mRfidManager?.disConnect()
//            mRfidManager?.release()
//            mRfidManager = null
//
//            SoundTool.getInstance(context)?.release()
//
//            isSdkInitialized = false
//            Timber.i("✅ Resources released successfully")
//        } catch (e: Exception) {
//            Timber.e(e, "Error releasing resources")
//        }
//    }
//}

/**
 * RFID 管理器 - 基於最新 UHF Code 更新
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
        private const val TAG = "RFIDManager"
    }

    init {
        Timber.d("$TAG initializing...")
        initializeSDK()
    }

    /**
     * 初始化 RFID SDK
     * 參考 BaseApplication.initRfid()
     */
    private fun initializeSDK() {
        try {
            // 1. 初始化音效工具
            SoundTool.getInstance(context)

            // 2. 模組上電
            RFIDSDKManager.getInstance().power(true)

            // 3. 延遲等待硬體準備（參考 UHF Code 的 1500ms）
            Thread.sleep(1500)

            // 4. 連接 RFID 設備
            val status = RFIDSDKManager.getInstance().connect()

            if (status) {
                mRfidManager = RFIDSDKManager.getInstance().rfidManager

                if (mRfidManager != null) {
                    isSdkInitialized = true
                    Timber.i("✅ RFID SDK initialized successfully")

                    // 獲取固件版本
                    val version = mRfidManager?.firmwareVersion
                    Timber.i("📟 Firmware Version: $version")

                    // 設置默認參數（參考 UHF Code）
                    setDefaultParameters()
                } else {
                    Timber.e("❌ RfidManager instance is null")
                }
            } else {
                Timber.e("❌ Failed to connect RFID device")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Exception during SDK initialization")
        }
    }

    /**
     * 設置默認參數
     * 參考 ReadParam() 和 SettingActivity
     */
    private fun setDefaultParameters() {
        try {
            val param = RfidParameter().apply {
                // 參考 UHF Code 的默認設置
                Session = 1          // Session 1
                QValue = 6           // Q值 = 6
                ScanTime = 50        // 掃描時間 50 * 100ms = 5秒
                IvtType = 0          // 查詢類型
                Memory = 2           // EPC 區域
                WordPtr = 0          // 起始地址
                Length = 6           // 長度
                Interval = 3         // 間隔時間 3 * 10ms = 30ms
            }

            mRfidManager?.inventoryParameter = param
            Timber.d("✅ Default parameters set")
        } catch (e: Exception) {
            Timber.e(e, "Error setting default parameters")
        }
    }

    /**
     * 條碼掃描（單次觸發）
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
     * 開始 RFID 掃描
     * 參考 ScanActivity.readTag()
     */
    fun startScan(mode: ScanMode): Flow<RFIDTag> = callbackFlow {
        Timber.i("🎯 startScan called - Mode: $mode, SDK initialized: $isSdkInitialized")

        if (!isSdkInitialized || mRfidManager == null) {
            Timber.e("❌ SDK not initialized")
            close(IllegalStateException("RFID SDK 未初始化"))
            return@callbackFlow
        }

        val seenTags = ConcurrentHashMap.newKeySet<String>()
        val isContinuous = mode == ScanMode.CONTINUOUS

        // 創建回調橋接
        val dataListener = object : RfidDataListener {
            override fun onTagScanned(tag: RFIDTag) {
                Timber.i("🎉 Tag received in Kotlin: ${tag.uid}, RSSI: ${tag.rssi}")
                val success = trySend(tag).isSuccess
                Timber.d("📤 trySend result: $success")
            }

            override fun onScanEnded() {
                Timber.i("🏁 Scan ended callback received")
                if (!isContinuous) {
                    close()
                }
            }
        }

        currentCallback = RfidCallbackBridge(
            dataListener,
            context,
            seenTags,
            isContinuous
        )

        // 註冊回調
        try {
            mRfidManager?.registerCallback(currentCallback)
            Timber.i("✅ Callback registered: $currentCallback")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to register callback")
            close(e)
            return@callbackFlow
        }

        // 開始掃描
        try {
            val result = if (isContinuous) {
                Timber.d("📡 Calling startRead()...")
                mRfidManager?.startRead()
            } else {
                Timber.d("📡 Calling inventorySingle()...")
                mRfidManager?.inventorySingle()
                0 // inventorySingle 沒有返回值
            }

            Timber.i("🚀 Scan started - Mode: $mode, Result: $result")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to start scan")
            close(e)
            return@callbackFlow
        }

        // Flow 關閉時清理
        awaitClose {
            Timber.i("🧹 awaitClose called - Cleaning up...")
            stopInventory()

            try {
                if (currentCallback != null) {
                    mRfidManager?.unregisterCallback(currentCallback)
                    Timber.d("✅ Callback unregistered")
                    currentCallback = null
                }
            } catch (e: Exception) {
                Timber.e(e, "⚠️ Error during cleanup")
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
            Timber.d("✅ Inventory stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping inventory")
        }
    }

    /**
     * 寫入標籤 EPC
     * 參考 ReadWriteActivity.onClick()
     */
    suspend fun writeUid(newUid: String, password: String = "00000000"): Result<Unit> {
        return try {
            if (!isSdkInitialized || mRfidManager == null) {
                return Result.failure(IllegalStateException("SDK not initialized"))
            }

            val result = mRfidManager?.writeEpcString(newUid, password)

            if (result == 0) {
                Timber.i("✅ EPC written successfully: $newUid")
                Result.success(Unit)
            } else {
                Timber.e("❌ Write failed with code: $result")
                Result.failure(Exception("Write failed with code: $result"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error writing UID")
            Result.failure(e)
        }
    }

    /**
     * 讀取標籤數據
     * 參考 ReadWriteActivity.onClick()
     */
    suspend fun readTag(
        epc: String,
        memory: Int = 3,  // EPC區域
        wordPtr: Int = 0,
        length: Int = 6,
        password: String = "00000000"
    ): Result<String> {
        return try {
            if (!isSdkInitialized || mRfidManager == null) {
                return Result.failure(IllegalStateException("SDK not initialized"))
            }

            val data = mRfidManager?.readTag(epc, memory, wordPtr, length, password)

            if (data != null) {
                Timber.i("✅ Tag read successfully: $data")
                Result.success(data)
            } else {
                Timber.e("❌ Read failed")
                Result.failure(Exception("Read failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading tag")
            Result.failure(e)
        }
    }

    /**
     * 設定功率
     * 參考 SettingActivity.onClick()
     */
    suspend fun setPower(power: Int): Result<Unit> {
        return try {
            if (!isSdkInitialized || mRfidManager == null) {
                return Result.failure(IllegalStateException("SDK not initialized"))
            }

            if (power < 0 || power > 33) {
                return Result.failure(IllegalArgumentException("Power must be 0-33 dBm"))
            }

            val result = mRfidManager?.setOutputPower(power)

            if (result == 0) {
                Timber.i("✅ Power set to $power dBm")
                Result.success(Unit)
            } else {
                Timber.e("❌ Set power failed with code: $result")
                Result.failure(Exception("Set power failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting power")
            Result.failure(e)
        }
    }

    /**
     * 獲取當前功率
     */
    fun getPower(): Int? {
        return try {
            mRfidManager?.outputPower
        } catch (e: Exception) {
            Timber.e(e, "Error getting power")
            null
        }
    }

    /**
     * 設置工作區域和頻率
     * 參考 SettingActivity.onClick()
     */
    suspend fun setFrequencyRegion(
        region: Int,
        minFreq: Int,
        maxFreq: Int
    ): Result<Unit> {
        return try {
            if (!isSdkInitialized || mRfidManager == null) {
                return Result.failure(IllegalStateException("SDK not initialized"))
            }

            val result = mRfidManager?.setFrequencyRegion(region, minFreq, maxFreq)

            if (result == 0) {
                Timber.i("✅ Frequency region set: region=$region, freq=$minFreq-$maxFreq")
                Result.success(Unit)
            } else {
                Timber.e("❌ Set frequency failed with code: $result")
                Result.failure(Exception("Set frequency failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error setting frequency")
            Result.failure(e)
        }
    }

    /**
     * 獲取固件版本
     */
    fun getFirmwareVersion(): String? {
        return try {
            mRfidManager?.firmwareVersion
        } catch (e: Exception) {
            Timber.e(e, "Error getting firmware version")
            null
        }
    }

    /**
     * 檢查連接狀態
     */
    fun isConnected(): Boolean {
        return try {
            mRfidManager?.isConnected ?: false
        } catch (e: Exception) {
            Timber.e(e, "Error checking connection")
            false
        }
    }

    /**
     * 釋放資源
     * 參考 MainActivity.onDestroy()
     */
    fun release() {
        Timber.i("Releasing RFIDManager resources...")

        try {
            // 1. 停止掃描
            stopInventory()

            // 2. 取消註冊回調
            if (currentCallback != null) {
                mRfidManager?.unregisterCallback(currentCallback)
                currentCallback = null
            }

            // 3. 釋放 RFID SDK
            RFIDSDKManager.getInstance().release()
            mRfidManager = null

            // 4. 釋放音效
            SoundTool.getInstance(context)?.release()

            isSdkInitialized = false
            Timber.i("✅ Resources released successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing resources")
        }
    }
}
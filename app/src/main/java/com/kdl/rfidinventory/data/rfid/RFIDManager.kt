package com.kdl.rfidinventory.data.rfid

import android.content.Context
import android.os.Binder
import android.os.IBinder
import com.kdl.rfidinventory.util.ScanMode
import com.kdl.rfidinventory.util.SoundTool
import com.ubx.usdk.USDKManager
import com.ubx.usdk.rfid.RfidManager
import com.ubx.usdk.rfid.aidl.IRfidCallback
import com.ubx.usdk.rfid.aidl.RfidDate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

import com.ubx.usdk.rfid.RfidManager as UsdkRfidManager

/**
 * RFID 管理器 - Mock 版本
 */
//@Singleton
//class RFIDManager @Inject constructor() {
////    ==== v1 ====
////    private var isScanning = false
////
////    /**
////     * 開始掃描
////     * @param mode 掃描模式（單個/連續）
////     * @return Flow<RFIDTag> 掃描到的標籤流
////     */
////    fun startScan(mode: ScanMode): Flow<RFIDTag> = flow {
////        isScanning = true
////
////        when (mode) {
////            ScanMode.SINGLE -> {
////                // 單個掃描：模擬延遲後返回一個標籤
////                delay(1000)
////                emit(generateMockTag())
////                isScanning = false
////            }
////            ScanMode.CONTINUOUS -> {
////                // 連續掃描：每 2 秒返回一個標籤
////                while (isScanning) {
////                    delay(2000)
////                    emit(generateMockTag())
////                }
////            }
////        }
////    }
////
////    /**
////     * 停止掃描
////     */
////    fun stopScan() {
////        isScanning = false
////    }
////
////    /**
////     * 寫入標籤 UID（需硬體支持）
////     */
////    suspend fun writeUid(newUid: String): Result<Unit> {
////        // Mock 實作
////        delay(500)
////        return Result.success(Unit)
////    }
////
////    /**
////     * 寫入用戶數據（需硬體支持）
////     */
////    suspend fun writeUserData(uid: String, data: ByteArray): Result<Unit> {
////        // Mock 實作
////        delay(500)
////        return Result.success(Unit)
////    }
////
////    // Mock 數據生成
////    private fun generateMockTag(): RFIDTag {
////        val mockUids = listOf(
////            "E2801170210021AA12345678",
////            "E2801170210021BB23456789",
////            "E2801170210021CC34567890",
////            "E2801170210021DD45678901",
////            "E2801170210021EE56789012"
////        )
////        return RFIDTag(
////            uid = mockUids.random(),
////            rssi = Random.nextInt(-80, -30)
////        )
////    }
//
//    private var isScanning = false
//    private val scannedUids = mutableSetOf<String>()
//
//    fun startScan(mode: ScanMode): Flow<RFIDTag> = flow {
//        isScanning = true
//        scannedUids.clear()
//
//        when (mode) {
//            ScanMode.SINGLE -> {
//                // 單次掃描：延遲 1-2 秒模擬掃描
//                delay(Random.nextLong(1000, 2000))
//                emit(generateMockTag())
//                isScanning = false
//            }
//            ScanMode.CONTINUOUS -> {
//                // 連續掃描：每 1.5-3 秒返回一個新標籤
//                while (isScanning) {
//                    delay(Random.nextLong(1500, 3000))
//                    if (isScanning) {
//                        emit(generateMockTag())
//                    }
//                }
//            }
//        }
//    }
//
//    fun stopScan() {
//        isScanning = false
//    }
//
//    suspend fun writeUid(newUid: String): Result<Unit> {
//        delay(500)
//        return Result.success(Unit)
//    }
//
//    suspend fun writeUserData(uid: String, data: ByteArray): Result<Unit> {
//        delay(500)
//        return Result.success(Unit)
//    }
//
//    private fun generateMockTag(): RFIDTag {
//        // 生成唯一的 UID
//        val uid = generateUniqueUid()
//        return RFIDTag(
//            uid = uid,
//            rssi = Random.nextInt(-80, -30)
//        )
//    }
//
//    private fun generateUniqueUid(): String {
//        val prefix = "E28011702100"
//        var uid: String
//
//        // 確保生成的 UID 是唯一的
//        do {
//            val randomPart = (1..12).map {
//                "0123456789ABCDEF"[Random.nextInt(16)]
//            }.joinToString("")
//            uid = prefix + randomPart
//        } while (scannedUids.contains(uid))
//
//        scannedUids.add(uid)
//        return uid
//    }
//}

/**
 * RFID 管理器 - 真實硬體 SDK (USDK) 版本
 */
@Singleton
class RFIDManager @Inject constructor(
    @ApplicationContext private val context: Context
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
     * 開始掃描
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
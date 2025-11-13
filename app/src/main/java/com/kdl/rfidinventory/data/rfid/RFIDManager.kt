package com.kdl.rfidinventory.data.rfid

import android.content.Context
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

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

    private var mRfidManager: RfidManager? = null
    private var isSdkInitialized = false
    private var currentScanMode = ScanMode.CONTINUOUS
    private var scanChannel: kotlinx.coroutines.channels.SendChannel<RFIDTag>? = null

    // 用於在連續掃描模式下過濾重複標籤
    private val seenTags = ConcurrentHashMap.newKeySet<String>()

    companion object {
        // 從 kdl-rfid-demo 中獲取的硬體連接埠和波特率
        private const val COM_PORT = "/dev/ttyHSL0"
        private const val BAUD_RATE = 115200
    }

    init {
        Timber.d("RFIDManager initializing...")
        try {
            USDKManager.getInstance().init(context) { status ->
                if (status == USDKManager.STATUS.SUCCESS) {
                    mRfidManager = USDKManager.getInstance().rfidManager
                    if (mRfidManager?.connectCom(COM_PORT, BAUD_RATE) == true) {
                        isSdkInitialized = true
                        mRfidManager?.registerCallback(rfidCallback)
                        Timber.i("RFID SDK Initialized and COM Port ($COM_PORT) Connected.")
                        // 可選：初始化時獲取一次版本和功率
                        mRfidManager?.getFirmwareVersion(mRfidManager!!.readId)
                        mRfidManager?.getOutputPower(mRfidManager!!.readId)
                    } else {
                        Timber.e("Failed to connect to RFID COM port ($COM_PORT).")
                    }
                } else {
                    Timber.e("Failed to initialize USDKManager. Status: $status")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during RFIDManager initialization.")
        }
    }

    /**
     * SDK 回調
     * 這裡處理所有來自硬體的非同步事件
     */
    private val rfidCallback = object : IRfidCallback.Stub() {
        override fun onInventoryTag(
            cmd: Byte,
            pc: String?,
            crc: String?,
            epc: String,
            ant: Byte,
            rssi: String,
            freq: String?,
            phase: Int,
            count: Int,
            readId: String?
        ) {
            Timber.d("onInventoryTag: EPC: $epc, RSSI: $rssi")

            // 在連續掃描模式下，過濾掉本次掃描已回報過的標籤
            if (currentScanMode == ScanMode.CONTINUOUS && !seenTags.add(epc)) {
                return // 已回報過，忽略
            }

            val tag = RFIDTag(
                uid = epc,
                rssi = rssi.toIntOrNull() ?: 0,
                timestamp = System.currentTimeMillis()
            )

            // 播放提示音 (來自 demo)
            SoundTool.getInstance(context).playBeep(1)

            // 將標籤發送到 Flow
            val sendResult = scanChannel?.trySend(tag)
            if (sendResult?.isFailure == true) {
                Timber.w(sendResult.exceptionOrNull(), "Failed to send tag to channel.")
            }

            // 如果是單次掃描模式，收到第一個標籤後立即停止
            if (currentScanMode == ScanMode.SINGLE) {
                stopScan() // 這將觸發 flow 的 awaitClose
            }
        }

        override fun onInventoryTagEnd(antId: Int, tagCount: Int, readSpeed: Int, totalCount: Int, cmd: Byte) {
            Timber.d("Inventory scan ended. TotalCount: $totalCount")
            // 當掃描被 stopInventory() 停止時，此回調會被觸發
            // 我們在這裡關閉 channel 來通知 Flow 收集器
            scanChannel?.close()
            scanChannel = null
        }

        override fun onOperationTag(pc: String?, crc: String?, epc: String?, data: String?, dataLen: Int, ant: Byte, cmd: Byte) {
            // 用於處理 Read/Write 操作的回調
            Timber.d("onOperationTag: EPC: $epc, Data: $data")
            // TODO: 實現 Read/Write 的協程回調
        }

        override fun onOperationTagEnd(tagCount: Int) {
            // Read/Write 操作結束
        }

        override fun refreshSetting(rfidDate: RfidDate?) {
            // 獲取設定 (如功率) 後的回調
            rfidDate?.let {
                val power = it.btAryOutputPower?.get(0)?.toInt() ?: -1
                Timber.i("RFID Settings Refreshed. Power: $power")
            }
        }

        override fun onExeCMDStatus(cmd: Byte, status: Byte) {
            // 執行命令的狀態回調
            if (status != ErrorCode.SUCCESS) {
                Timber.w("Command $cmd failed with status code: $status")
            } else {
                Timber.d("Command $cmd executed successfully.")
            }
            // TODO: 實現 Write/SetPower 的協程回調
        }
    }

    /**
     * 開始掃描
     * @param mode 掃描模式（單個/連續）
     * @return 一個 Flow，它會發出掃描到的 RFIDTag
     */
    fun startScan(mode: ScanMode): Flow<RFIDTag> = callbackFlow {
        if (!isSdkInitialized || mRfidManager == null) {
            Timber.e("startScan called but SDK not initialized.")
            close(IllegalStateException("RFID SDK 未初始化或連接埠失敗"))
            return@callbackFlow
        }

        // 確保上一個 flow 已關閉
        scanChannel?.close()
        scanChannel = channel // 將此 flow 的 channel 賦值給 scanChannel

        currentScanMode = mode

        // 如果是連續掃描，清空上一輪的標籤記錄
        if (mode == ScanMode.CONTINUOUS) {
            seenTags.clear()
        }

        // 開始盤點 (使用 demo 中的 `customizedSessionTargetInventory`)
        try {
            val readId = mRfidManager!!.readId
            mRfidManager?.customizedSessionTargetInventory(readId, 1, 0, 1) // 參數 1, 0, 1
            Timber.i("RFID scan started, Mode: $mode")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start scan.")
            close(e)
        }

        // flow 關閉時 (例如被 stopScan() 或從外部取消)
        awaitClose {
            Timber.d("Flow is closing. Stopping inventory.")
            mRfidManager?.stopInventory()
            scanChannel = null
        }
    }

    /**
     * 停止掃描
     * 這會觸發 `onInventoryTagEnd` 回調，並關閉 `startScan` 中 `callbackFlow`
     */
    fun stopScan() {
        if (!isSdkInitialized || mRfidManager == null) {
            Timber.w("stopScan called, but SDK not initialized.")
            return
        }
        Timber.d("stopScan called.")
        mRfidManager?.stopInventory()
    }

    /**
     * 寫入標籤 EPC (管理員功能)
     * TODO: 需要使用 suspendCancellableCoroutine 配合 onExeCMDStatus 回調來實現
     */
    suspend fun writeUid(oldUid: String, newUid: String, accessPwd: String = "00000000"): Result<Unit> {
        if (!isSdkInitialized || mRfidManager == null) {
            return Result.failure(IllegalStateException("SDK not initialized"))
        }
        Timber.w("writeUid: Not implemented yet. (Demo uses writeTag or writeEpcString)")
        // 實作邏輯:
        // 1. mRfidManager.setAccessEpcMatch(...) 鎖定 oldUid
        // 2. mRfidManager.writeTag(...) 或 writeEpcString(...) 寫入 newUid
        // 3. 使用 suspendCancellableCoroutine 包裹，並在 onExeCMDStatus 回調中 resume
        return Result.failure(Exception("Write function not implemented"))
    }

    /**
     * 設定掃描功率 (管理員功能)
     * TODO: 需要使用 suspendCancellableCoroutine 配合 onExeCMDStatus/refreshSetting 回調來實現
     */
    suspend fun setPower(power: Int): Result<Unit> {
        if (!isSdkInitialized || mRfidManager == null) {
            return Result.failure(IllegalStateException("SDK not initialized"))
        }
        Timber.w("setPower: Not implemented yet. (Demo uses setOutputPower)")
        // 實作邏輯:
        // 1. mRfidManager.setOutputPower(mRfidManager!!.readId, power.toByte())
        // 2. 使用 suspendCancellableCoroutine 包裹，並在 onExeCMDStatus 回調中 resume
        return Result.failure(Exception("Set power function not implemented"))
    }

    /**
     * 在 App 退出時釋放 SDK 資源
     */
    fun release() {
        Timber.i("Releasing RFIDManager resources.")
        if (mRfidManager != null) {
            mRfidManager?.stopInventory()
            mRfidManager?.disConnect()
            mRfidManager?.release()
            mRfidManager = null
        }
        SoundTool.getInstance(context).release()
        isSdkInitialized = false
    }
}
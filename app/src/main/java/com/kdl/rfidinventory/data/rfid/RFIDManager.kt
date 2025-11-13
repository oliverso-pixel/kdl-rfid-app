package com.kdl.rfidinventory.data.rfid

import com.kdl.rfidinventory.util.ScanMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * RFID 管理器 - Mock 版本
 * 實際使用時需替換為硬體廠商的 SDK
 */
@Singleton
class RFIDManager @Inject constructor() {

//    private var isScanning = false
//
//    /**
//     * 開始掃描
//     * @param mode 掃描模式（單個/連續）
//     * @return Flow<RFIDTag> 掃描到的標籤流
//     */
//    fun startScan(mode: ScanMode): Flow<RFIDTag> = flow {
//        isScanning = true
//
//        when (mode) {
//            ScanMode.SINGLE -> {
//                // 單個掃描：模擬延遲後返回一個標籤
//                delay(1000)
//                emit(generateMockTag())
//                isScanning = false
//            }
//            ScanMode.CONTINUOUS -> {
//                // 連續掃描：每 2 秒返回一個標籤
//                while (isScanning) {
//                    delay(2000)
//                    emit(generateMockTag())
//                }
//            }
//        }
//    }
//
//    /**
//     * 停止掃描
//     */
//    fun stopScan() {
//        isScanning = false
//    }
//
//    /**
//     * 寫入標籤 UID（需硬體支持）
//     */
//    suspend fun writeUid(newUid: String): Result<Unit> {
//        // Mock 實作
//        delay(500)
//        return Result.success(Unit)
//    }
//
//    /**
//     * 寫入用戶數據（需硬體支持）
//     */
//    suspend fun writeUserData(uid: String, data: ByteArray): Result<Unit> {
//        // Mock 實作
//        delay(500)
//        return Result.success(Unit)
//    }
//
//    // Mock 數據生成
//    private fun generateMockTag(): RFIDTag {
//        val mockUids = listOf(
//            "E2801170210021AA12345678",
//            "E2801170210021BB23456789",
//            "E2801170210021CC34567890",
//            "E2801170210021DD45678901",
//            "E2801170210021EE56789012"
//        )
//        return RFIDTag(
//            uid = mockUids.random(),
//            rssi = Random.nextInt(-80, -30)
//        )
//    }

    private var isScanning = false
    private val scannedUids = mutableSetOf<String>()

    fun startScan(mode: ScanMode): Flow<RFIDTag> = flow {
        isScanning = true
        scannedUids.clear()

        when (mode) {
            ScanMode.SINGLE -> {
                // 單次掃描：延遲 1-2 秒模擬掃描
                delay(Random.nextLong(1000, 2000))
                emit(generateMockTag())
                isScanning = false
            }
            ScanMode.CONTINUOUS -> {
                // 連續掃描：每 1.5-3 秒返回一個新標籤
                while (isScanning) {
                    delay(Random.nextLong(1500, 3000))
                    if (isScanning) {
                        emit(generateMockTag())
                    }
                }
            }
        }
    }

    fun stopScan() {
        isScanning = false
    }

    suspend fun writeUid(newUid: String): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    suspend fun writeUserData(uid: String, data: ByteArray): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    private fun generateMockTag(): RFIDTag {
        // 生成唯一的 UID
        val uid = generateUniqueUid()
        return RFIDTag(
            uid = uid,
            rssi = Random.nextInt(-80, -30)
        )
    }

    private fun generateUniqueUid(): String {
        val prefix = "E28011702100"
        var uid: String

        // 確保生成的 UID 是唯一的
        do {
            val randomPart = (1..12).map {
                "0123456789ABCDEF"[Random.nextInt(16)]
            }.joinToString("")
            uid = prefix + randomPart
        } while (scannedUids.contains(uid))

        scannedUids.add(uid)
        return uid
    }
}
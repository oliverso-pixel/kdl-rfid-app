package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.api.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.SamplingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamplingRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {

    suspend fun getBasketByUid(uid: String): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            // 使用正確的 DAO 方法
            val entity = basketDao.getBasketByUid(uid)
                ?: return@withContext Result.failure(Exception("籃子不存在"))

            Result.success(entity.toBasket())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun markForSampling(
        uids: List<String>,
        sampleQuantity: Int,
        remarks: String,
        isOnline: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

            if (isOnline) {
                // 線上模式：直接呼叫 API
                val request = SamplingRequest(
                    basketUids = uids,
                    sampleQuantity = sampleQuantity,
                    remarks = remarks,
                    timestamp = timestamp
                )
                val response = apiService.markForSampling(request)

                if (response.success) {
                    // 更新本地資料庫狀態
                    uids.forEach { uid ->
                        val entity = basketDao.getBasketByUid(uid)
                        entity?.let {
                            basketDao.updateBasket(
                                it.copy(
                                    status = BasketStatus.SAMPLING,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "標記抽樣失敗"))
                }
            } else {
                // 離線模式：儲存到待同步佇列
                uids.forEach { uid ->
                    val operation = PendingOperationEntity(
                        operationType = OperationType.SAMPLING,
                        uid = uid,
                        payload = """
                            {
                                "sampleQuantity": $sampleQuantity,
                                "remarks": "$remarks",
                                "timestamp": "$timestamp"
                            }
                        """.trimIndent(),
                        timestamp = System.currentTimeMillis()
                    )
                    pendingOperationDao.insertOperation(operation)

                    // 更新本地狀態
                    val entity = basketDao.getBasketByUid(uid)
                    entity?.let {
                        basketDao.updateBasket(
                            it.copy(
                                status = BasketStatus.SAMPLING,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
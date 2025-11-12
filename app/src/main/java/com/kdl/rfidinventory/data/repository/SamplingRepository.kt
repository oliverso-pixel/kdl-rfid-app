package com.kdl.rfidinventory.data.repository

import com.kdl.rfidinventory.data.local.AppDatabase
import com.kdl.rfidinventory.data.local.entity.PendingOperation
import com.kdl.rfidinventory.data.model.Basket
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.remote.ApiService
import com.kdl.rfidinventory.data.remote.request.SamplingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamplingRepository @Inject constructor(
    private val apiService: ApiService,
    private val database: AppDatabase
) {

    suspend fun getBasketByUid(uid: String): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            val entity = database.basketDao().getByUid(uid)
                ?: return@withContext Result.failure(Exception("籃子不存在"))

            Result.success(entity.toBasket())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                apiService.markForSampling(request)

                // 更新本地資料庫
                uids.forEach { uid ->
                    val entity = database.basketDao().getByUid(uid)
                    entity?.let {
                        database.basketDao().update(
                            it.copy(status = BasketStatus.SAMPLING.name)
                        )
                    }
                }
            } else {
                // 離線模式：儲存到待同步佇列
                uids.forEach { uid ->
                    val pendingOp = PendingOperation(
                        operationType = "SAMPLING",
                        data = """
                            {
                                "uid": "$uid",
                                "sampleQuantity": $sampleQuantity,
                                "remarks": "$remarks",
                                "timestamp": "$timestamp"
                            }
                        """.trimIndent(),
                        timestamp = timestamp
                    )
                    database.pendingOperationDao().insert(pendingOp)

                    // 更新本地狀態
                    val entity = database.basketDao().getByUid(uid)
                    entity?.let {
                        database.basketDao().update(
                            it.copy(status = BasketStatus.SAMPLING.name)
                        )
                    }
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
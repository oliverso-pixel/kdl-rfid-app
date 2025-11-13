package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ReceivingRequest
import com.kdl.rfidinventory.data.remote.dto.request.ShippingRequest
//import com.kdl.rfidinventory.data.remote.dto.response.toRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {

    suspend fun getRoutes(): Result<List<Route>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getRoutes()
            if (response.success && response.data != null) {
                val routes = response.data.map { it.toRoute() }
                Result.success(routes)
            } else {
                delay(500)
                Result.success(mockRoutes())
            }
        } catch (e: Exception) {
            delay(500)
            Result.success(mockRoutes())
        }
    }

    suspend fun getBasketByUid(uid: String): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            val entity = basketDao.getBasketByUid(uid)
                ?: return@withContext Result.failure(Exception("籃子不存在"))
            Result.success(entity.toBasket())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun receiveBaskets(uids: List<String>, isOnline: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

            if (isOnline) {
                uids.forEach { uid ->
                    val request = ReceivingRequest(uid, "default-route", timestamp)
                    val response = apiService.receiveBasket(request)
                    if (!response.success) {
                        return@withContext Result.failure(Exception(response.message ?: "收貨失敗"))
                    }
                }

                uids.forEach { uid ->
                    val entity = basketDao.getBasketByUid(uid)
                    entity?.let {
                        basketDao.updateBasket(
                            it.copy(
                                status = BasketStatus.RECEIVED,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                }
                Result.success(Unit)
            } else {
                uids.forEach { uid ->
                    val operation = PendingOperationEntity(
                        operationType = OperationType.WAREHOUSE_RECEIVE,
                        uid = uid,
                        payload = Json.encodeToString(ReceivingRequest(uid, "default-route", timestamp)),
                        timestamp = System.currentTimeMillis()
                    )
                    pendingOperationDao.insertOperation(operation)

                    val entity = basketDao.getBasketByUid(uid)
                    entity?.let {
                        basketDao.updateBasket(
                            it.copy(
                                status = BasketStatus.RECEIVED,
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

    suspend fun getWarehouseBaskets(): Result<List<Basket>> = withContext(Dispatchers.IO) {
        try {
            val allBaskets = basketDao.getAllBaskets().first()
            val warehouseBaskets = allBaskets.filter {
                it.status == BasketStatus.RECEIVED || it.status == BasketStatus.IN_STOCK
            }
            Result.success(warehouseBaskets.map { it.toBasket() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mockRoutes() = listOf(
        Route("R1", "路線 A", "倉庫 A", "true"),
        Route("R2", "路線 B", "倉庫 B", "true"),
        Route("R3", "路線 C", "倉庫 C", "false")
    )
}
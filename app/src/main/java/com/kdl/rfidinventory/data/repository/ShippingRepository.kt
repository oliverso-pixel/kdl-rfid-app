package com.kdl.rfidinventory.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import com.kdl.rfidinventory.data.model.*
import com.kdl.rfidinventory.data.remote.ApiService
import com.kdl.rfidinventory.data.remote.dto.request.ShipBasketsRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShippingRepository @Inject constructor(
    private val apiService: ApiService,
    private val basketDao: BasketDao,
    private val pendingOperationDao: PendingOperationDao
) {

    suspend fun getRoutes(): Result<List<Route>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getRoutes()
            if (response.success && response.data != null) {
                val routes = response.data.map { routeResponse ->
                    Route(
                        id = routeResponse.id,
                        name = routeResponse.name,
                        destination = routeResponse.destination,
                        isActive = routeResponse.isActive
                    )
                }
                Result.success(routes)
            } else {
                Result.success(getMockRoutes())
            }
        } catch (e: Exception) {
            Result.success(getMockRoutes())
        }
    }

    suspend fun getBasketByUid(uid: String): Result<Basket> = withContext(Dispatchers.IO) {
        try {
            val entity = basketDao.getBasketByUid(uid)
            if (entity != null) {
                Result.success(entity.toBasket())
            } else {
                Result.failure(Exception("籃子不存在"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun shipBaskets(
        uids: List<String>,
        routeId: String,
        isOnline: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

            if (isOnline) {
                val request = ShipBasketsRequest(
                    basketUids = uids,
                    routeId = routeId,
                    timestamp = timestamp
                )
                val response = apiService.shipBaskets(request)

                if (response.success) {
                    uids.forEach { uid ->
                        basketDao.getBasketByUid(uid)?.let { entity ->
                            basketDao.updateBasket(
                                entity.copy(
                                    status = BasketStatus.SHIPPED,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    Result.success(Unit)
                } else {
                    Result.failure(Exception(response.message ?: "出貨失敗"))
                }
            } else {
                uids.forEach { uid ->
                    val operation = PendingOperationEntity(
                        operationType = OperationType.SHIPPING_SHIP,
                        uid = uid,
                        payload = Json.encodeToString(
                            ShipBasketsRequest(
                                basketUids = listOf(uid),
                                routeId = routeId,
                                timestamp = timestamp
                            )
                        ),
                        timestamp = System.currentTimeMillis()
                    )
                    pendingOperationDao.insertOperation(operation)

                    basketDao.getBasketByUid(uid)?.let { entity ->
                        basketDao.updateBasket(
                            entity.copy(
                                status = BasketStatus.SHIPPED,
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

    private fun getMockRoutes() = listOf(
        Route("R001", "路線 A", "目的地 A", "true"),
        Route("R002", "路線 B", "目的地 B", "true"),
        Route("R003", "路線 C", "目的地 C", "true"),
        Route("R004", "路線 D", "目的地 D", "true")
    )
}
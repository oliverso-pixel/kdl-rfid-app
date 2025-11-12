package com.kdl.rfidinventory.data.remote.api

import com.kdl.rfidinventory.data.remote.dto.request.ProductionStartRequest
import com.kdl.rfidinventory.data.remote.dto.response.ApiResponse
import com.kdl.rfidinventory.data.remote.dto.response.ProductionOrderResponse
import retrofit2.http.*

interface ProductionApi {

    @GET("production/orders")
    suspend fun getProductionOrders(): ApiResponse<List<ProductionOrderResponse>>

    @GET("products/{id}")
    suspend fun getProductById(@Path("id") id: String): ApiResponse<ProductResponse>

    @POST("production/start")
    suspend fun startProduction(@Body request: ProductionStartRequest): ApiResponse<Unit>
}

// DTO 範例
data class ProductResponse(
    val id: String,
    val name: String,
    val maxBasketCapacity: Int
)
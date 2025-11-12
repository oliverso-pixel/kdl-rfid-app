package com.kdl.rfidinventory.data.remote.api

import com.kdl.rfidinventory.data.remote.dto.request.ReceiveRequest
import com.kdl.rfidinventory.data.remote.dto.response.ApiResponse
import com.kdl.rfidinventory.data.remote.dto.response.BasketResponse
import retrofit2.http.*

interface WarehouseApi {

    @GET("item/{uid}")
    suspend fun getBasketByUid(@Path("uid") uid: String): ApiResponse<BasketResponse>

    @PUT("warehouse/receive")
    suspend fun receiveBasket(@Body request: ReceiveRequest): ApiResponse<Unit>
}

data class BasketResponse(
    val uid: String,
    val productId: String?,
    val productName: String?,
    val batchId: String?,
    val quantity: Int,
    val status: String
)
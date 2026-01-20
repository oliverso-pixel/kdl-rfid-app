package com.kdl.rfidinventory.data.remote.api

import com.kdl.rfidinventory.data.remote.dto.request.BindBasketRequest
import com.kdl.rfidinventory.data.remote.dto.request.ClearRequest
import com.kdl.rfidinventory.data.remote.dto.request.CreateBasketRequest
import com.kdl.rfidinventory.data.remote.dto.request.ProductionStartRequest
import com.kdl.rfidinventory.data.remote.dto.request.ReceivingRequest
import com.kdl.rfidinventory.data.remote.dto.request.SamplingRequest
import com.kdl.rfidinventory.data.remote.dto.request.ScanRequest
import com.kdl.rfidinventory.data.remote.dto.request.ShipBasketsRequest
import com.kdl.rfidinventory.data.remote.dto.request.ShippingRequest
import com.kdl.rfidinventory.data.remote.dto.request.SyncRequest
import com.kdl.rfidinventory.data.remote.dto.request.UpdateBasketRequest
import com.kdl.rfidinventory.data.remote.dto.request.UpdateSettingsRequest
import com.kdl.rfidinventory.data.remote.dto.response.ApiBasketDto
import com.kdl.rfidinventory.data.remote.dto.response.ApiResponse
import com.kdl.rfidinventory.data.remote.dto.response.BasketDetailResponse
import com.kdl.rfidinventory.data.remote.dto.response.DailyProductResponse
import com.kdl.rfidinventory.data.remote.dto.response.GenericResponse
import com.kdl.rfidinventory.data.remote.dto.response.ProductDetailResponse
import com.kdl.rfidinventory.data.remote.dto.response.ProductionBatchResponse
import com.kdl.rfidinventory.data.remote.dto.response.ProductionOrderResponse
import com.kdl.rfidinventory.data.remote.dto.response.RouteResponse
import com.kdl.rfidinventory.data.remote.dto.response.SettingsResponse
import com.kdl.rfidinventory.data.remote.dto.response.SyncResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
//    // ==================== Production API ====================
    // 1. 獲取每日產品列表
    @GET("production/daily-products")
    suspend fun getDailyProducts(): retrofit2.Response<List<DailyProductResponse>>

    // 2. 獲取生產批次列表
    @GET("production/app-list")
    suspend fun getProductionBatches(
        @Query("target_date") targetDate: String
    ): retrofit2.Response<List<ProductionBatchResponse>>

    // 3. 綁定籃子 (開始生產)
    // 使用 PUT 更新籃子狀態
    @PUT("baskets/{uid}")
    suspend fun bindBasket(
        @Path("uid") uid: String,
        @Body request: BindBasketRequest
    ): retrofit2.Response<GenericResponse>

//    // ==================== Warehouse API ====================
//    // ==================== Shipping API ====================
//    // ==================== Sampling API ====================
//    // ==================== Clear API ====================
//    // ==================== Admin API ====================
    // 籃子查詢：GET /api/v1/baskets/{rfid}
    // 注意：這裡不使用 ApiResponse 包裝，因為 curl 顯示直接回傳 JSON 物件或 {"detail":...}
    @GET("baskets/{rfid}")
    suspend fun getBasketByRfid(@Path("rfid") rfid: String): retrofit2.Response<ApiBasketDto>

    // 籃子註冊：POST /api/v1/baskets/
    @POST("baskets/")
    suspend fun createBasket(@Body request: CreateBasketRequest): retrofit2.Response<GenericResponse>



    // 籃子相關
    @POST("baskets/scan")
    suspend fun scanBasket(@Body request: ScanRequest): ApiResponse<BasketDetailResponse>

    @GET("baskets/{uid}")
    suspend fun getBasketByUid(@Path("uid") uid: String): ApiResponse<BasketDetailResponse>

    @PUT("baskets/{uid}")
    suspend fun updateBasket(
        @Path("uid") uid: String,
        @Body request: UpdateBasketRequest
    ): ApiResponse<BasketDetailResponse>

    @DELETE("baskets/{uid}")
    suspend fun deleteBasket(@Path("uid") uid: String): ApiResponse<Unit>

    // 籃子註冊相關 - 使用 ApiResponse
//    @GET("baskets/{uid}/check")
//    suspend fun checkBasketRegistration(@Path("uid") uid: String): ApiResponse<BasketRegistrationResponse>
//
//    @POST("baskets/register")
//    suspend fun registerBasket(@Body request: RegisterBasketRequest): ApiResponse<BasketRegistrationResponse>

    // 生產相關
    @GET("production/orders")
    suspend fun getProductionOrders(): ApiResponse<List<ProductionOrderResponse>>

    @GET("products/{id}")
    suspend fun getProductById(@Path("id") productId: String): ApiResponse<ProductDetailResponse>

    @POST("production/start")
    suspend fun startProduction(@Body request: ProductionStartRequest): ApiResponse<Unit>

    // 倉庫相關
    @GET("warehouse/routes")
    suspend fun getRoutes(): ApiResponse<List<RouteResponse>>

    @POST("warehouse/receiving")
    suspend fun receiveBasket(@Body request: ReceivingRequest): ApiResponse<Unit>

    @POST("warehouse/shipping")
    suspend fun shipBasket(@Body request: ShippingRequest): ApiResponse<Unit>

    // 出貨相關
    @POST("shipping/ship")
    suspend fun shipBaskets(@Body request: ShipBasketsRequest): ApiResponse<Unit>

    // 抽樣相關
    @POST("sampling/mark")
    suspend fun markForSampling(@Body request: SamplingRequest): ApiResponse<Unit>

    // 清籃相關
    @POST("clear/mark")
    suspend fun markForClear(@Body request: ClearRequest): ApiResponse<Unit>

    // 同步相關
    @POST("sync/operations")
    suspend fun syncOperations(@Body request: SyncRequest): ApiResponse<SyncResponse>

    // 管理員相關
    @GET("admin/settings")
    suspend fun getSettings(): ApiResponse<SettingsResponse>

    @PUT("admin/settings")
    suspend fun updateSettings(@Body request: UpdateSettingsRequest): ApiResponse<Unit>
}

data class BasketRegistrationResponse(
    val isRegistered: Boolean,
    val uid: String,
    val registeredAt: String?,
    val status: String?
)

data class RegisterBasketRequest(
    val uid: String,
    val registeredAt: Long
)
package com.kdl.rfidinventory.data.remote

import com.kdl.rfidinventory.data.remote.dto.request.*
import com.kdl.rfidinventory.data.remote.dto.response.*
import retrofit2.http.*

interface ApiService {
//    // ==================== Production API ====================
//    @GET("production/orders")
//    suspend fun getProductionOrders(): ApiResponse<List<ProductionOrderResponse>>
//
//    @GET("products/{id}")
//    suspend fun getProductById(@Path("id") productId: String): ApiResponse<ProductDetailResponse>
//
//    @POST("production/start")
//    suspend fun startProduction(@Body request: ProductionStartRequest): ApiResponse<Unit>
//
//    // ==================== Warehouse API ====================
//    @GET("warehouse/routes")
//    suspend fun getRoutes(): ApiResponse<List<RouteResponse>>
//
//    @POST("warehouse/receiving")
//    suspend fun receiveBasket(@Body request: ReceivingRequest): ApiResponse<Unit>
//
//    @POST("warehouse/shipping")
//    suspend fun shipBasket(@Body request: ShippingRequest): ApiResponse<Unit>
//
////    // ==================== Shipping API ====================
////    @GET("shipping/routes")
////    suspend fun getRoutes(): ApiResponse<List<RouteResponse>>
////
////    @POST("shipping/ship")
////    suspend fun shipBaskets(@Body request: ShipBasketsRequest): ApiResponse<Unit>
//
//    // ==================== Sampling API ====================
//    @POST("sampling/mark")
//    suspend fun markForSampling(@Body request: SamplingRequest): ApiResponse<Unit>
//
//    // ==================== Clear API ====================
//    @POST("baskets/clear")
//    suspend fun clearBasketConfiguration(@Body request: ClearConfigurationRequest): ApiResponse<Unit>
//
//    @POST("clear/mark")
//    suspend fun markForClear(@Body request: ClearRequest): ApiResponse<Unit>
//
//    // ==================== Admin API ====================
//    @POST("admin/sync")
//    suspend fun syncPendingOperations(@Body request: SyncRequest): ApiResponse<SyncResponse>
//
//    @GET("admin/settings")
//    suspend fun getSettings(): ApiResponse<SettingsResponse>
//
//    @PUT("admin/settings")
//    suspend fun updateSettings(@Body request: UpdateSettingsRequest): ApiResponse<Unit>

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

package com.kdl.rfidinventory.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 上貨路線
 */
@Serializable
data class LoadingRoute(
    val id: String,                      // 路線ID (如 M01, M02)
    val name: String,                    // 路線名稱
    val vehiclePlate: String? = null,    // 車牌號 (預留)
    val deliveryDate: String,            // 送貨日期
    val items: List<LoadingItem>,        // 上貨項目列表
    val totalQuantity: Int,              // 總數量
    val totalBaskets: Int,               // 總格數
    val status: LoadingStatus = LoadingStatus.PENDING,
    val completionStatus: RouteCompletionStatus = RouteCompletionStatus()
)

/**
 * 上貨項目
 */
@Serializable
data class LoadingItem(
    val productId: String,               // 產品ID
    val productName: String,             // 產品名稱
    val totalQuantity: Int,              // 總數量
    val fullTrolley: Int,                   // 完整車數
    val fullBaskets: Int,               // 完整格數
    val looseQuantity: Int,             // 散數
    val completionStatus: ItemCompletionStatus = ItemCompletionStatus()
)

/**
 * 路線完成狀態
 */
@Serializable
data class RouteCompletionStatus(
    val fullBasketsCompleted: Boolean = false,      // 完整籃子是否完成
    val looseItemsCompleted: Boolean = false,       // 散貨是否完成
    val fullBasketsScannedCount: Int = 0,           // 已掃描完整籃子數
    val looseItemsScannedCount: Int = 0,            // 已掃描散貨數量
    val lastUpdated: Long = 0,                      // 最後更新時間
    val updatedBy: String? = null                   // 更新人
) {
    val isFullyCompleted: Boolean
        get() = fullBasketsCompleted && looseItemsCompleted
}

/**
 * 項目完成狀態
 */
@Serializable
data class ItemCompletionStatus(
    val fullBasketsCompleted: Boolean = false,      // 該產品的完整籃子是否完成
    val looseItemsCompleted: Boolean = false,       // 該產品的散貨是否完成
    val fullBasketsScanned: Int = 0,                // 已掃描完整籃子數
    val looseItemsScanned: Int = 0,                 // 已掃描散貨數量
    val scannedBasketUids: List<String> = emptyList() // 已掃描的籃子UID
) {
    val isFullyCompleted: Boolean
        get() = fullBasketsCompleted && looseItemsCompleted

    fun getProgress(mode: LoadingMode, expectedBaskets: Int, expectedQuantity: Int): Float {
        return when (mode) {
            LoadingMode.FULL_BASKETS -> {
                if (expectedBaskets == 0) 0f
                else fullBasketsScanned.toFloat() / expectedBaskets
            }
            LoadingMode.LOOSE_ITEMS -> {
                if (expectedQuantity == 0) 0f
                else looseItemsScanned.toFloat() / expectedQuantity
            }
        }
    }
}

/**
 * 上貨狀態
 */
enum class LoadingStatus {
    PENDING,      // 待上貨
    IN_PROGRESS,  // 上貨中
    COMPLETED,    // 已完成
    VERIFIED      // 已驗證
}

/**
 * 上貨模式
 */
enum class LoadingMode {
    FULL_BASKETS,  // 車+格 (完整)
    LOOSE_ITEMS    // 散貨
}

/**
 * 上貨掃描項目
 */
data class LoadingScannedItem(
    val basket: Basket,
    val loadingItem: LoadingItem,
    val isLoose: Boolean,              // 是否為散貨
    val scannedQuantity: Int,          // 掃描數量
    val expectedQuantity: Int          // 期望數量
)

/**
 * Mock 數據生成
 */
@RequiresApi(Build.VERSION_CODES.O)
fun mockLoadingRoutes() = listOf(
    LoadingRoute(
        id = "M01",
        name = "M01 路線",
        vehiclePlate = "TX1846",
        deliveryDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
        items = listOf(
            LoadingItem(
                productId = "MP01L",
                productName = "鮮奶(大)",
                totalQuantity = 237,
                fullTrolley = 2,
                fullBaskets = 1,
                looseQuantity = 17,
            ),
            LoadingItem(
                productId = "MP01S",
                productName = "鮮奶(小)",
                totalQuantity = 417,
                fullTrolley = 1,
                fullBaskets = 1,
                looseQuantity = 57,
            ),
            LoadingItem(
                productId = "MP02L",
                productName = "零乳糖(大)",
                totalQuantity = 58,
                fullTrolley = 0,
                fullBaskets = 2,
                looseQuantity = 18,
            )
        ),
        totalQuantity = 712,
        totalBaskets = 21,
        status = LoadingStatus.PENDING
    ),
    LoadingRoute(
        id = "M02",
        name = "M02 路線",
        vehiclePlate = "TN5923",
        deliveryDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
        items = listOf(
            LoadingItem(
                productId = "MP01L",
                productName = "鮮奶(大)",
                totalQuantity = 121,
                fullTrolley = 1,
                fullBaskets = 1,
                looseQuantity = 1,
            ),
            LoadingItem(
                productId = "MP01S",
                productName = "鮮奶(小)",
                totalQuantity = 394,
                fullTrolley = 1,
                fullBaskets = 1,
                looseQuantity = 34,
            )
        ),
        totalQuantity = 1135,
        totalBaskets = 31,
        status = LoadingStatus.PENDING
    )
)
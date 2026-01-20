package com.kdl.rfidinventory.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class Batch(
    val id: String,
    val productId: String,
    val totalQuantity: Int,
    val remainingQuantity: Int,
    val productionDate: String,
    val expireDate: String? = null
) {
    /**
     * 是否還有剩余數量
     */
    fun hasRemainingQuantity(): Boolean = remainingQuantity > 0

    /**
     * 剩余百分比
     */
    fun remainingPercentage(): Float =
        if (totalQuantity > 0) (remainingQuantity.toFloat() / totalQuantity * 100)
        else 0f

    /**
     * 是否即將用完（剩余少於20%）
     */
    fun isLowStock(): Boolean = remainingPercentage() < 20f
}

/**
 * 生成模擬批次數據
 * @param productId 可選的產品ID，如果提供則只返回該產品的批次
 * @return 批次列表，按剩余數量降序排序
 */
@RequiresApi(Build.VERSION_CODES.O)
fun mockBatches(productId: String? = null): List<Batch> {
    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

    val allBatches = listOf(
        // MP01L 的批次
        Batch(
            id = "BATCH-$today-001",
            productId = "MP01L",
            totalQuantity = 1000,
            remainingQuantity = 1000,
            productionDate = today
        ),

        // MP01S 的批次
        Batch(
            id = "BATCH-$today-002",
            productId = "MP01S",
            totalQuantity = 3000,
            remainingQuantity = 3000,
            productionDate = today
        ),

        // MP04S 的批次
        Batch(
            id = "BATCH-2025-003",
            productId = "MP04S",
            totalQuantity = 4000,
            remainingQuantity = 3000,
            productionDate = today
        ),

        // MP09L 的批次
        Batch(
            id = "BATCH-2025-004",
            productId = "MP09L",
            totalQuantity = 5000,
            remainingQuantity = 3000,
            productionDate = today
        )
    )

    // 過濾並排序
    val filteredBatches = if (productId != null) {
        allBatches.filter { it.productId == productId }
    } else {
        allBatches
    }

    // 按剩余數量降序排序
    return filteredBatches.sortedByDescending { it.remainingQuantity }
}

/**
 * 根據批次ID獲取批次
 */
@RequiresApi(Build.VERSION_CODES.O)
fun getBatchById(batchId: String): Batch? {
    return mockBatches().find { it.id == batchId }
}

/**
 * 獲取所有產品ID列表
 */
@RequiresApi(Build.VERSION_CODES.O)
fun getAllProductIds(): List<String> {
    return mockBatches().map { it.productId }.distinct().sorted()
}

/**
 * 獲取指定產品的批次數量
 */
@RequiresApi(Build.VERSION_CODES.O)
fun getBatchCountForProduct(productId: String): Int {
    return mockBatches(productId).size
}
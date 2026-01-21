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

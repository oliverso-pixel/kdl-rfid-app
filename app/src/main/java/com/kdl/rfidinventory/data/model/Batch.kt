package com.kdl.rfidinventory.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class Batch(
    val batch_code: String,
    val itemcode: String,
    val totalQuantity: Int = 0,
    val targetQuantity: Int = 0,
    val producedQuantity: Int = 0,
    val remainingQuantity: Int,
    val status: String = "PENDING",
    val maxRepairs: Int = 1,
    val productionDate: String,
    val expireDate: String? = null
) {
    /**
     * 計算生產進度
     */
    fun getProductionProgress(): Float {
        return if (targetQuantity > 0) {
            (producedQuantity.toFloat() / targetQuantity * 100)
        } else 0f
    }

    /**
     * 是否已達成目標
     */
    fun isTargetReached(): Boolean = producedQuantity >= targetQuantity

    /**
     * 剩餘目標數量
     */
    fun getRemainingTarget(): Int = (targetQuantity - producedQuantity).coerceAtLeast(0)

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

package com.kdl.rfidinventory.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kdl.rfidinventory.data.model.BasketStatus

@Entity(
    tableName = "baskets",
    indices = [
        Index(value = ["productId"]),
        Index(value = ["warehouseId"]),
        Index(value = ["status"]),
        Index(value = ["batchId"])
    ]
)
data class BasketEntity(
    @PrimaryKey
    val uid: String,
    val type: Int? = null,

    // ========== 扁平字段（用於查詢和索引）==========
    val productId: String?,
    val productName: String?,
    val batchId: String?,
    val warehouseId: String?,

    // ========== JSON 字段（存儲完整對象）==========
    val productJson: String?,
    val batchJson: String?,

    // ========== 其他字段 ==========
    val quantity: Int,
    val status: BasketStatus,
    val productionDate: String?,
    val expireDate: String?,
    val lastUpdated: Long,
    val updateBy: String?
)

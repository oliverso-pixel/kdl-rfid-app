package com.kdl.rfidinventory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kdl.rfidinventory.data.model.BasketStatus

@Entity(tableName = "baskets")
data class BasketEntity(
    @PrimaryKey
    val uid: String,
    val productID: String?,
    val productName: String?,
    val batchId: String?,
    val warehouseId: String?,
    val quantity: Int,
    val status: BasketStatus,
    val productionDate: String?,
    val expireDate: String?,
    val lastUpdated: Long,
    val updateBy: String?
)

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
    val quantity: Int,
    val status: BasketStatus,
    val productionDate: String?,
    val lastUpdated: Long
)

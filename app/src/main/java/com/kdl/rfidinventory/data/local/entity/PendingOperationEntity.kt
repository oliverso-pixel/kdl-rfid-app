package com.kdl.rfidinventory.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kdl.rfidinventory.data.model.OperationType

@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val operationType: OperationType,
    val uid: String,
    val payload: String,
    val timestamp: Long,
    val retryCount: Int = 0
)

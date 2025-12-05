package com.kdl.rfidinventory.data.local.dao

import androidx.room.*
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationDao {
    @Query("SELECT * FROM pending_operations ORDER BY timestamp ASC")
    fun getAllPendingOperations(): Flow<List<PendingOperationEntity>>

    @Query("SELECT * FROM pending_operations ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<PendingOperationEntity>

    @Query("SELECT COUNT(*) FROM pending_operations")
    fun getPendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: PendingOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity)

    @Delete
    suspend fun delete(operation: PendingOperationEntity)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteOperation(id: Int)

    @Query("UPDATE pending_operations SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Int)

    @Query("DELETE FROM pending_operations")
    suspend fun deleteAll()
}
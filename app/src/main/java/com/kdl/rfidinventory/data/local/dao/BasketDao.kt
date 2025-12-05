package com.kdl.rfidinventory.data.local.dao

import androidx.room.*
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.BasketStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BasketDao {
    @Query("SELECT * FROM baskets WHERE productId = :productId")
    suspend fun getBasketsByProductId(productId: String): List<BasketEntity>

    @Query("SELECT * FROM baskets WHERE warehouseId = :warehouseId AND status IN (:statuses)")
    suspend fun getBasketsByWarehouse(
        warehouseId: String,
        statuses: BasketStatus
    ): List<BasketEntity>

    @Query("""
        SELECT * FROM baskets 
        WHERE productId = :productId 
        AND warehouseId = :warehouseId 
        AND status = :status
        ORDER BY quantity DESC, lastUpdated ASC
    """)
    suspend fun getBasketsByProductAndWarehouse(
        productId: String,
        warehouseId: String,
        status: BasketStatus = BasketStatus.IN_STOCK
    ): List<BasketEntity>

    @Query("""
    SELECT productId, COUNT(*) as count 
    FROM baskets 
    WHERE warehouseId = :warehouseId 
    GROUP BY productId
""")
    fun getProductCountsByWarehouseFlow(warehouseId: String): Flow<List<ProductCount>>

    @Query("SELECT * FROM baskets WHERE uid = :uid")
    suspend fun getBasketByUid(uid: String): BasketEntity?

    @Query("SELECT * FROM baskets WHERE uid = :uid")
    fun getBasketByUidFlow(uid: String): Flow<BasketEntity?>

    @Query("SELECT * FROM baskets WHERE status = :status")
    fun getBasketsByStatus(status: BasketStatus): Flow<List<BasketEntity>>

    @Query("SELECT * FROM baskets ORDER BY lastUpdated DESC")
    fun getAllBaskets(): Flow<List<BasketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBasket(basket: BasketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaskets(baskets: List<BasketEntity>)

    @Update
    suspend fun updateBasket(basket: BasketEntity)

    @Query("DELETE FROM baskets WHERE uid = :uid")
    suspend fun deleteBasket(uid: String)

    @Query("DELETE FROM baskets")
    suspend fun deleteAll()

    /**
     * 批量更新籃子狀態
     */
    @Query("UPDATE baskets SET status = :newStatus WHERE uid IN (:uids)")
    suspend fun updateBasketsStatus(uids: List<String>, newStatus: BasketStatus)

    /**
     * 獲取指定批次的所有籃子
     */
    @Query("SELECT * FROM baskets WHERE batchId = :batchId")
    suspend fun getBasketsByBatch(batchId: String): List<BasketEntity>

    /**
     * 統計指定狀態的籃子數量
     */
    @Query("SELECT COUNT(*) FROM baskets WHERE status = :status")
    suspend fun countBasketsByStatus(status: BasketStatus): Int

    /**
     * 獲取即將過期的籃子（批次過期日期在指定天數內）
     */
//    @Query("""
//        SELECT * FROM baskets
//        WHERE expiryDate IS NOT NULL
//        AND expiryDate <= :expiryThreshold
//        ORDER BY expiryDate ASC
//    """)
//    suspend fun getExpiringBaskets(expiryThreshold: String): List<BasketEntity>
}
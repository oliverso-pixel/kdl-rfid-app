package com.kdl.rfidinventory.data.local.dao

import androidx.room.*
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.model.BasketStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BasketDao {
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
}
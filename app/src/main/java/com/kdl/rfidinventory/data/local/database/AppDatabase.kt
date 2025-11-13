package com.kdl.rfidinventory.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kdl.rfidinventory.data.local.dao.BasketDao
import com.kdl.rfidinventory.data.local.dao.PendingOperationDao
import com.kdl.rfidinventory.data.local.entity.BasketEntity
import com.kdl.rfidinventory.data.local.entity.PendingOperationEntity

@Database(
    entities = [
        BasketEntity::class,
        PendingOperationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun basketDao(): BasketDao
    abstract fun pendingOperationDao(): PendingOperationDao
}
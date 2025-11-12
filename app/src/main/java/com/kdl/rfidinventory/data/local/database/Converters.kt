package com.kdl.rfidinventory.data.local.database

import androidx.room.TypeConverter
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.OperationType

class Converters {
    @TypeConverter
    fun fromBasketStatus(value: BasketStatus): String = value.name

    @TypeConverter
    fun toBasketStatus(value: String): BasketStatus = BasketStatus.valueOf(value)

    @TypeConverter
    fun fromOperationType(value: OperationType): String = value.name

    @TypeConverter
    fun toOperationType(value: String): OperationType = OperationType.valueOf(value)
}
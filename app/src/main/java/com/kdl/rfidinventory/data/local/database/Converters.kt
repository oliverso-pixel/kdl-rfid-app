package com.kdl.rfidinventory.data.local.database

import androidx.room.TypeConverter
import com.kdl.rfidinventory.data.model.BasketStatus
import com.kdl.rfidinventory.data.model.Batch
import com.kdl.rfidinventory.data.model.OperationType
import com.kdl.rfidinventory.data.model.Product
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @TypeConverter
    fun fromBasketStatus(value: BasketStatus): String = value.name

    @TypeConverter
    fun toBasketStatus(value: String): BasketStatus = BasketStatus.valueOf(value)

    @TypeConverter
    fun fromOperationType(value: OperationType): String = value.name

    @TypeConverter
    fun toOperationType(value: String): OperationType = OperationType.valueOf(value)

    // ========== Product JSON 轉換 ==========
    @TypeConverter
    fun fromProduct(product: Product?): String? {
        return product?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toProduct(jsonString: String?): Product? {
        return jsonString?.let {
            try {
                json.decodeFromString<Product>(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    // ========== Batch JSON 轉換 ==========
    @TypeConverter
    fun fromBatch(batch: Batch?): String? {
        return batch?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toBatch(jsonString: String?): Batch? {
        return jsonString?.let {
            try {
                json.decodeFromString<Batch>(it)
            } catch (e: Exception) {
                null
            }
        }
    }
}
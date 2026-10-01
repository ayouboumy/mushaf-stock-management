package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["productId"]),
        Index(value = ["variantId"]),
        Index(value = ["dateMillis"]),
        Index(value = ["movementType"]),
        Index(value = ["isDeleted"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val variantId: Long? = null,
    val movementType: String, // "STOCK_IN", "STOCK_OUT", "ADJUSTMENT", "OPENING_BALANCE"
    val quantity: Int, // Positive for IN/OUT; For ADJUSTMENT: signed (+/-)
    val packageCount: Int = 0,
    val dateMillis: Long,
    val dateFormatted: String, // e.g. "2026-09-29"
    val destinationId: Long? = null,
    val destinationName: String = "",
    val destinationType: String = "",
    val source: String = "", // Used for STOCK_IN
    val responsiblePerson: String = "",
    val referenceNumber: String = "",
    val reason: String = "", // Required for ADJUSTMENT
    val notes: String = "",
    val isReversed: Boolean = false,
    val reversedByMovementId: Long? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

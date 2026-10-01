package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["action"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // "CREATE_PRODUCT", "STOCK_IN", "STOCK_OUT", "ADJUSTMENT", "REVERSE", "DELETE", "IMPORT_EXCEL", "INITIAL_STOCK"
    val entityName: String,
    val entityId: Long = 0,
    val details: String,
    val userRole: String = "المشرف"
)

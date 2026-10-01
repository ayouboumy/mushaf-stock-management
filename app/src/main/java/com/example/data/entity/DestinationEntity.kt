package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "destinations",
    indices = [
        Index(value = ["name"]),
        Index(value = ["type"])
    ]
)
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String = "مسجد", // مسجد، مؤسسة، جمعية، مجلس علمي، إدارة، حفل، إمام، حاج، جالية، أخرى
    val commune: String = "",
    val province: String = "",
    val address: String = "",
    val contactPerson: String = "",
    val phone: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

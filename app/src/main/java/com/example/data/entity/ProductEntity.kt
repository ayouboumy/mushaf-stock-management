package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nameArabic: String,
    val nameFrench: String = "",
    val category: String = "مصحف شريف", // مصحف كامل، أجزاء، برايل، ضعاف البصر، مترجم، كتب إسلامية
    val language: String = "العربية",
    val formatType: String = "عادي", // قياس عادي، مجزء، جيبي، كبير، برايل
    val unit: String = "نسخة", // نسخة، مجلد، كرتونة
    val packageQuantity: Int = 10, // Units per package/box by default
    val minimumStock: Int = 50,
    val initialStock: Int = 0,
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "product_variants",
    indices = [Index(value = ["productId"])]
)
data class ProductVariantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val nameArabic: String, // e.g. "الفرنسية", "الإنجليزية", "الإسبانية"
    val nameFrench: String = "", // "Français", "English", "Español"
    val code: String = "", // "FR", "EN", "ES"
    val initialStock: Int = 0,
    val minimumStock: Int = 20,
    val packageQuantity: Int = 10,
    val notes: String = "",
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

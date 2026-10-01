package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ProductVariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductVariantDao {
    @Query("SELECT * FROM product_variants WHERE productId = :productId AND active = 1 ORDER BY nameArabic ASC")
    fun getVariantsForProduct(productId: Long): Flow<List<ProductVariantEntity>>

    @Query("SELECT * FROM product_variants WHERE productId = :productId AND active = 1")
    suspend fun getVariantsListForProduct(productId: Long): List<ProductVariantEntity>

    @Query("SELECT * FROM product_variants WHERE active = 1")
    fun getAllVariants(): Flow<List<ProductVariantEntity>>

    @Query("SELECT * FROM product_variants WHERE active = 1")
    suspend fun getAllVariantsList(): List<ProductVariantEntity>

    @Query("SELECT * FROM product_variants WHERE id = :id LIMIT 1")
    suspend fun getVariantById(id: Long): ProductVariantEntity?

    @Query("SELECT * FROM product_variants WHERE productId = :productId AND (nameArabic = :name OR nameFrench = :name) LIMIT 1")
    suspend fun getVariantByName(productId: Long, name: String): ProductVariantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ProductVariantEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(variants: List<ProductVariantEntity>): List<Long>

    @Update
    suspend fun updateVariant(variant: ProductVariantEntity)

    @Delete
    suspend fun deleteVariant(variant: ProductVariantEntity)

    @Query("DELETE FROM product_variants WHERE productId = :productId")
    suspend fun deleteVariantsByProduct(productId: Long)

    @Query("DELETE FROM product_variants")
    suspend fun clearAllVariants()
}

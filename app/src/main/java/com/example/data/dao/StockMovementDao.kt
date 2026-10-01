package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE isDeleted = 0 ORDER BY dateMillis DESC, id DESC")
    fun getAllActiveMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY dateMillis DESC, id DESC")
    fun getAllMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE isDeleted = 0 AND productId = :productId ORDER BY dateMillis DESC, id DESC")
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE isDeleted = 0 AND productId = :productId AND (:variantId IS NULL OR variantId = :variantId) ORDER BY dateMillis DESC, id DESC")
    fun getMovementsForProductAndVariant(productId: Long, variantId: Long?): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE isDeleted = 0 ORDER BY dateMillis DESC, id DESC LIMIT :limit")
    fun getLatestMovements(limit: Int = 10): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE id = :id LIMIT 1")
    suspend fun getMovementById(id: Long): StockMovementEntity?

    @Query("SELECT * FROM stock_movements WHERE isDeleted = 0")
    suspend fun getAllActiveMovementsList(): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements")
    suspend fun getAllMovementsList(): List<StockMovementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movements: List<StockMovementEntity>): List<Long>

    @Update
    suspend fun updateMovement(movement: StockMovementEntity)

    @Delete
    suspend fun deleteMovement(movement: StockMovementEntity)

    @Query("DELETE FROM stock_movements WHERE id = :id")
    suspend fun deleteMovementById(id: Long)

    @Query("UPDATE stock_movements SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteMovement(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE stock_movements SET isReversed = 1, reversedByMovementId = :reversedById, updatedAt = :timestamp WHERE id = :id")
    suspend fun markReversed(id: Long, reversedById: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM stock_movements")
    suspend fun clearAllMovements()
}

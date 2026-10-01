package com.example.data.model

import com.example.data.entity.StockMovementEntity

enum class StockStatus {
    AVAILABLE,     // متوفر 🟢
    LOW_STOCK,     // مخزون منخفض 🟠
    OUT_OF_STOCK   // نفد المخزون 🔴
}

data class CalculatedProductStock(
    val productId: Long,
    val variantId: Long? = null,
    val productNameArabic: String,
    val productNameFrench: String = "",
    val variantNameArabic: String = "",
    val variantNameFrench: String = "",
    val category: String = "",
    val formatType: String = "",
    val unit: String = "نسخة",
    val packageQuantity: Int = 10,
    val initialStock: Int = 0,
    val totalIncoming: Int = 0,
    val totalOutgoing: Int = 0,
    val totalAdjustments: Int = 0,
    val currentStock: Int = 0,
    val minimumStock: Int = 50,
    val status: StockStatus = StockStatus.AVAILABLE,
    val lastMovementDate: Long? = null,
    val lastMovementType: String? = null
) {
    val displayName: String
        get() = if (variantNameArabic.isNotBlank()) "$productNameArabic - $variantNameArabic" else productNameArabic

    val isLowStock: Boolean
        get() = currentStock <= minimumStock && currentStock > 0

    val isOutOfStock: Boolean
        get() = currentStock <= 0
}

data class DashboardSummary(
    val totalProductsCount: Int = 0,
    val totalStockQuantity: Int = 0,
    val monthlyIncoming: Int = 0,
    val monthlyOutgoing: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val totalMovementsCount: Int = 0
)

data class PeriodStockResult(
    val productId: Long,
    val variantId: Long? = null,
    val productName: String,
    val variantName: String = "",
    val openingStock: Int,
    val incoming: Int,
    val outgoing: Int,
    val adjustments: Int,
    val closingStock: Int
) {
    val displayName: String
        get() = if (variantName.isNotBlank()) "$productName - $variantName" else productName
}

data class StockHistoryPoint(
    val timestamp: Long,
    val dateFormatted: String,
    val stockAfter: Int,
    val change: Int,
    val movementType: String
)

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

object StockCalculator {

    /**
     * Calculates the current stock from initial stock and a list of movements.
     * Soft-deleted and reversed movements are excluded.
     */
    fun calculateStock(
        initialStock: Int,
        movements: List<StockMovementEntity>
    ): Int {
        var current = initialStock
        for (m in movements) {
            if (m.isDeleted || m.isReversed) continue
            when (m.movementType) {
                "STOCK_IN" -> current += m.quantity
                "STOCK_OUT" -> current -= m.quantity
                "ADJUSTMENT" -> current += m.quantity // quantity is positive for excess, negative for deficit
                "OPENING_BALANCE" -> {}
            }
        }
        return current
    }

    /**
     * Validates if an outgoing movement is allowed given current stock and policy.
     */
    fun validateStockOut(
        currentStock: Int,
        requestedQuantity: Int,
        allowNegativeStock: Boolean
    ): ValidationResult {
        if (requestedQuantity <= 0) {
            return ValidationResult(false, "الكمية يجب أن تكون أكبر من الصفر")
        }
        if (!allowNegativeStock && requestedQuantity > currentStock) {
            return ValidationResult(
                false,
                "الكمية المطلوبة ($requestedQuantity) أكبر من المخزون المتوفر ($currentStock)"
            )
        }
        return ValidationResult(true)
    }

    /**
     * Calculates period report:
     * - Opening stock: Initial stock + all non-deleted movements with dateMillis < fromMillis
     * - Incoming: movements with dateMillis between fromMillis and toMillis
     * - Outgoing: movements with dateMillis between fromMillis and toMillis
     * - Adjustments: movements with dateMillis between fromMillis and toMillis
     * - Closing: Opening + Incoming - Outgoing + Adjustments
     */
    fun calculatePeriodReport(
        productId: Long,
        variantId: Long? = null,
        productName: String,
        variantName: String = "",
        initialStock: Int,
        movements: List<StockMovementEntity>,
        fromMillis: Long,
        toMillis: Long
    ): PeriodStockResult {
        val validMovements = movements.filter {
            !it.isDeleted && !it.isReversed &&
            it.productId == productId &&
            (variantId == null || it.variantId == variantId)
        }

        // Opening stock is initial stock + all movements strictly before fromMillis
        var opening = initialStock
        var periodIn = 0
        var periodOut = 0
        var periodAdj = 0

        for (m in validMovements) {
            if (m.dateMillis < fromMillis) {
                when (m.movementType) {
                    "STOCK_IN" -> opening += m.quantity
                    "STOCK_OUT" -> opening -= m.quantity
                    "ADJUSTMENT" -> opening += m.quantity
                }
            } else if (m.dateMillis in fromMillis..toMillis) {
                when (m.movementType) {
                    "STOCK_IN" -> periodIn += m.quantity
                    "STOCK_OUT" -> periodOut += m.quantity
                    "ADJUSTMENT" -> periodAdj += m.quantity
                }
            }
        }

        val closing = opening + periodIn - periodOut + periodAdj

        return PeriodStockResult(
            productId = productId,
            variantId = variantId,
            productName = productName,
            variantName = variantName,
            openingStock = opening,
            incoming = periodIn,
            outgoing = periodOut,
            adjustments = periodAdj,
            closingStock = closing
        )
    }

    /**
     * Calculates stock evolution chronologically for charts.
     */
    fun calculateStockEvolution(
        initialStock: Int,
        movements: List<StockMovementEntity>
    ): List<StockHistoryPoint> {
        val validMovements = movements
            .filter { !it.isDeleted && !it.isReversed }
            .sortedWith(compareBy({ it.dateMillis }, { it.id }))

        val points = mutableListOf<StockHistoryPoint>()
        var runningStock = initialStock

        points.add(
            StockHistoryPoint(
                timestamp = if (validMovements.isNotEmpty()) validMovements.first().dateMillis - 86400000L else System.currentTimeMillis() - 86400000L,
                dateFormatted = "الرصيد الأولي",
                stockAfter = runningStock,
                change = initialStock,
                movementType = "OPENING"
            )
        )

        for (m in validMovements) {
            val change = when (m.movementType) {
                "STOCK_IN" -> m.quantity
                "STOCK_OUT" -> -m.quantity
                "ADJUSTMENT" -> m.quantity
                else -> 0
            }
            runningStock += change
            points.add(
                StockHistoryPoint(
                    timestamp = m.dateMillis,
                    dateFormatted = m.dateFormatted,
                    stockAfter = runningStock,
                    change = change,
                    movementType = m.movementType
                )
            )
        }

        return points
    }
}

package com.example

import com.example.data.entity.StockMovementEntity
import com.example.data.model.StockCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockCalculationTest {

    private fun createMovement(
        id: Long,
        productId: Long = 1L,
        variantId: Long? = null,
        type: String,
        qty: Int,
        dateMillis: Long = System.currentTimeMillis(),
        dateFormatted: String = "2026-09-29"
    ) = StockMovementEntity(
        id = id,
        productId = productId,
        variantId = variantId,
        movementType = type,
        quantity = qty,
        dateMillis = dateMillis,
        dateFormatted = dateFormatted
    )

    /**
     * Test 1 (from Section 34):
     * Opening = 1,000
     * IN = 500
     * OUT = 200
     * Expected = 1,300
     */
    @Test
    fun test1_basicStockCalculation() {
        val initialStock = 1000
        val movements = listOf(
            createMovement(1, type = "STOCK_IN", qty = 500),
            createMovement(2, type = "STOCK_OUT", qty = 200)
        )
        val result = StockCalculator.calculateStock(initialStock, movements)
        assertEquals(1300, result)
    }

    /**
     * Test 2 (from Section 34):
     * Opening = 1,000
     * OUT = 200
     * IN = 500
     * OUT = 100
     * Expected = 1,200
     */
    @Test
    fun test2_sequentialStockCalculation() {
        val initialStock = 1000
        val movements = listOf(
            createMovement(1, type = "STOCK_OUT", qty = 200),
            createMovement(2, type = "STOCK_IN", qty = 500),
            createMovement(3, type = "STOCK_OUT", qty = 100)
        )
        val result = StockCalculator.calculateStock(initialStock, movements)
        assertEquals(1200, result)
    }

    /**
     * Test 3 (from Section 34):
     * Opening = 100
     * OUT = 150
     * Expected: Reject transaction if negative stock disabled.
     */
    @Test
    fun test3_negativeStockValidation() {
        val currentStock = 100
        val requestedQty = 150

        // Negative stock OFF -> Reject
        val rejection = StockCalculator.validateStockOut(
            currentStock = currentStock,
            requestedQuantity = requestedQty,
            allowNegativeStock = false
        )
        assertFalse(rejection.isValid)

        // Negative stock ON -> Allow
        val approval = StockCalculator.validateStockOut(
            currentStock = currentStock,
            requestedQuantity = requestedQty,
            allowNegativeStock = true
        )
        assertTrue(approval.isValid)
    }

    /**
     * Test 4 (from Section 34):
     * Opening = 100
     * Adjustment = -10
     * Expected = 90
     */
    @Test
    fun test4_adjustmentCalculation() {
        val initialStock = 100
        val movements = listOf(
            createMovement(1, type = "ADJUSTMENT", qty = -10)
        )
        val result = StockCalculator.calculateStock(initialStock, movements)
        assertEquals(90, result)
    }

    /**
     * Test 5 (from Section 34):
     * Opening = 1,000
     * IN = 500
     * OUT = 200
     * Report for period should show:
     * Opening: 1,000
     * Incoming: 500
     * Outgoing: 200
     * Closing: 1,300
     */
    @Test
    fun test5_periodReportCalculation() {
        val initialStock = 1000
        val periodStart = 1774000000000L // e.g. Day 10
        val periodEnd = 1775000000000L   // e.g. Day 20

        val movements = listOf(
            // Movement inside period
            createMovement(1, type = "STOCK_IN", qty = 500, dateMillis = 1774500000000L),
            createMovement(2, type = "STOCK_OUT", qty = 200, dateMillis = 1774600000000L)
        )

        val report = StockCalculator.calculatePeriodReport(
            productId = 1L,
            productName = "المصحف المحمدي",
            initialStock = initialStock,
            movements = movements,
            fromMillis = periodStart,
            toMillis = periodEnd
        )

        assertEquals(1000, report.openingStock)
        assertEquals(500, report.incoming)
        assertEquals(200, report.outgoing)
        assertEquals(0, report.adjustments)
        assertEquals(1300, report.closingStock)
    }
}

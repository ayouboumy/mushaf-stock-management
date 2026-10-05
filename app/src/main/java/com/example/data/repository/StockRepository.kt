package com.example.data.repository

import com.example.data.db.AppDatabase
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.DestinationEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ProductVariantEntity
import com.example.data.entity.StockMovementEntity
import com.example.data.model.CalculatedProductStock
import com.example.data.model.DashboardSummary
import com.example.data.model.PeriodStockResult
import com.example.data.model.StockCalculator
import com.example.data.model.StockStatus
import com.example.data.model.ValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StockRepository(private val database: AppDatabase) {

    private val productDao = database.productDao()
    private val variantDao = database.productVariantDao()
    private val movementDao = database.stockMovementDao()
    private val destinationDao = database.destinationDao()
    private val auditLogDao = database.auditLogDao()
    val syncManager = com.example.data.sync.FirebaseSyncManager(database)

    init {
        syncManager.startRealtimeListeners()
        syncManager.startAutoSync()
    }

    // Diagnostic & Health Verification
    suspend fun runComprehensiveFirestoreDiagnostic(): com.example.data.sync.FirestoreDiagnosticReport {
        return syncManager.runComprehensiveDiagnostic()
    }

    suspend fun clearAndVerifyItemsCollection(): String {
        return syncManager.clearAndVerifyItemsCollection()
    }

    fun ensureCollectionListenersActive() {
        syncManager.ensureCollectionListenersActive()
    }

    val activeListenersMap = syncManager.activeListenersMap
    val diagnosticReport = syncManager.diagnosticReport

    val allProducts: Flow<List<ProductEntity>> = productDao.getActiveProducts()
    val distinctCategories: Flow<List<String>> = productDao.getDistinctCategories()
    val allDestinations: Flow<List<DestinationEntity>> = destinationDao.getAllDestinations()
    val allMovements: Flow<List<StockMovementEntity>> = movementDao.getAllActiveMovements()
    val latestMovements: Flow<List<StockMovementEntity>> = movementDao.getLatestMovements(10)
    val auditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllLogs()

    // Reactive calculated stock list combining products, variants, and movements
    val calculatedStockList: Flow<List<CalculatedProductStock>> = combine(
        productDao.getActiveProducts(),
        variantDao.getAllVariants(),
        movementDao.getAllActiveMovements()
    ) { products, variants, movements ->
        val results = mutableListOf<CalculatedProductStock>()

        for (product in products) {
            val productVariants = variants.filter { it.productId == product.id }

            if (productVariants.isEmpty()) {
                // Product without variants
                val prodMovements = movements.filter { it.productId == product.id }
                var totalIn = 0
                var totalOut = 0
                var totalAdj = 0
                var lastDate: Long? = null
                var lastType: String? = null

                for (m in prodMovements) {
                    if (m.isDeleted || m.isReversed) continue
                    when (m.movementType) {
                        "STOCK_IN" -> totalIn += m.quantity
                        "STOCK_OUT" -> totalOut += m.quantity
                        "ADJUSTMENT" -> totalAdj += m.quantity
                    }
                    if (lastDate == null || m.dateMillis > lastDate) {
                        lastDate = m.dateMillis
                        lastType = m.movementType
                    }
                }

                val rawCurrent = product.initialStock + totalIn - totalOut + totalAdj
                val current = maxOf(0, rawCurrent)
                val status = when {
                    current <= 0 -> StockStatus.OUT_OF_STOCK
                    current <= product.minimumStock -> StockStatus.LOW_STOCK
                    else -> StockStatus.AVAILABLE
                }

                results.add(
                    CalculatedProductStock(
                        productId = product.id,
                        variantId = null,
                        productNameArabic = product.nameArabic,
                        productNameFrench = product.nameFrench,
                        category = product.category,
                        formatType = product.formatType,
                        unit = product.unit,
                        packageQuantity = product.packageQuantity,
                        initialStock = product.initialStock,
                        totalIncoming = totalIn,
                        totalOutgoing = totalOut,
                        totalAdjustments = totalAdj,
                        currentStock = current,
                        minimumStock = product.minimumStock,
                        status = status,
                        lastMovementDate = lastDate,
                        lastMovementType = lastType
                    )
                )
            } else {
                // Product with variants (e.g. translated Mushafs)
                for (variant in productVariants) {
                    val varMovements = movements.filter { it.productId == product.id && it.variantId == variant.id }
                    var totalIn = 0
                    var totalOut = 0
                    var totalAdj = 0
                    var lastDate: Long? = null
                    var lastType: String? = null

                    for (m in varMovements) {
                        if (m.isDeleted || m.isReversed) continue
                        when (m.movementType) {
                            "STOCK_IN" -> totalIn += m.quantity
                            "STOCK_OUT" -> totalOut += m.quantity
                            "ADJUSTMENT" -> totalAdj += m.quantity
                        }
                        if (lastDate == null || m.dateMillis > lastDate) {
                            lastDate = m.dateMillis
                            lastType = m.movementType
                        }
                    }

                    val rawCurrent = variant.initialStock + totalIn - totalOut + totalAdj
                    val current = maxOf(0, rawCurrent)
                    val status = when {
                        current <= 0 -> StockStatus.OUT_OF_STOCK
                        current <= variant.minimumStock -> StockStatus.LOW_STOCK
                        else -> StockStatus.AVAILABLE
                    }

                    results.add(
                        CalculatedProductStock(
                            productId = product.id,
                            variantId = variant.id,
                            productNameArabic = product.nameArabic,
                            productNameFrench = product.nameFrench,
                            variantNameArabic = variant.nameArabic,
                            variantNameFrench = variant.nameFrench,
                            category = product.category,
                            formatType = product.formatType,
                            unit = product.unit,
                            packageQuantity = variant.packageQuantity,
                            initialStock = variant.initialStock,
                            totalIncoming = totalIn,
                            totalOutgoing = totalOut,
                            totalAdjustments = totalAdj,
                            currentStock = current,
                            minimumStock = variant.minimumStock,
                            status = status,
                            lastMovementDate = lastDate,
                            lastMovementType = lastType
                        )
                    )
                }
            }
        }

        results
    }

    // Dashboard summary statistics
    val dashboardSummary: Flow<DashboardSummary> = combine(
        calculatedStockList,
        movementDao.getAllActiveMovements()
    ) { stockList, movements ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStartMillis = cal.timeInMillis

        var monthIn = 0
        var monthOut = 0

        for (m in movements) {
            if (m.isDeleted || m.isReversed) continue
            if (m.dateMillis >= monthStartMillis) {
                when (m.movementType) {
                    "STOCK_IN" -> monthIn += m.quantity
                    "STOCK_OUT" -> monthOut += m.quantity
                }
            }
        }

        val totalStock = stockList.sumOf { it.currentStock }
        val lowStock = stockList.count { it.status == StockStatus.LOW_STOCK }
        val outOfStock = stockList.count { it.status == StockStatus.OUT_OF_STOCK }

        DashboardSummary(
            totalProductsCount = stockList.size,
            totalStockQuantity = totalStock,
            monthlyIncoming = monthIn,
            monthlyOutgoing = monthOut,
            lowStockCount = lowStock,
            outOfStockCount = outOfStock,
            totalMovementsCount = movements.size
        )
    }

    // Record Stock In
    suspend fun recordStockIn(
        productId: Long,
        variantId: Long?,
        quantity: Int,
        packageCount: Int,
        dateMillis: Long,
        dateFormatted: String,
        source: String,
        responsiblePerson: String,
        referenceNumber: String,
        notes: String
    ): Long {
        val movement = StockMovementEntity(
            productId = productId,
            variantId = variantId,
            movementType = "STOCK_IN",
            quantity = quantity,
            packageCount = packageCount,
            dateMillis = dateMillis,
            dateFormatted = dateFormatted,
            source = source,
            responsiblePerson = responsiblePerson,
            referenceNumber = referenceNumber,
            notes = notes
        )
        val id = movementDao.insertMovement(movement)
        syncManager.pushMovement(movement.copy(id = id))
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "STOCK_IN",
                entityName = "حركة إدخال",
                entityId = id,
                details = "إدخال كمية: $quantity | المصدر: $source | المرجع: $referenceNumber"
            )
        )
        return id
    }

    // Record Stock Out with validation check
    suspend fun recordStockOut(
        productId: Long,
        variantId: Long?,
        quantity: Int,
        packageCount: Int,
        dateMillis: Long,
        dateFormatted: String,
        destinationId: Long?,
        destinationName: String,
        destinationType: String,
        responsiblePerson: String,
        referenceNumber: String,
        notes: String,
        allowNegativeStock: Boolean
    ): Pair<Long, ValidationResult> {
        val currentStock = getCurrentStockForProduct(productId, variantId)
        val validation = StockCalculator.validateStockOut(currentStock, quantity, allowNegativeStock)
        if (!validation.isValid) {
            return Pair(-1L, validation)
        }

        val movement = StockMovementEntity(
            productId = productId,
            variantId = variantId,
            movementType = "STOCK_OUT",
            quantity = quantity,
            packageCount = packageCount,
            dateMillis = dateMillis,
            dateFormatted = dateFormatted,
            destinationId = destinationId,
            destinationName = destinationName,
            destinationType = destinationType,
            responsiblePerson = responsiblePerson,
            referenceNumber = referenceNumber,
            notes = notes
        )
        val id = movementDao.insertMovement(movement)
        syncManager.pushMovement(movement.copy(id = id))
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "STOCK_OUT",
                entityName = "حركة إخراج",
                entityId = id,
                details = "توزيع كمية: $quantity | الوجهة: $destinationName | المكلف: $responsiblePerson"
            )
        )
        return Pair(id, ValidationResult(true))
    }

    // Record Stock Adjustment
    suspend fun recordStockAdjustment(
        productId: Long,
        variantId: Long?,
        quantity: Int, // Signed: + for excess, - for damage/shortage
        packageCount: Int,
        dateMillis: Long,
        dateFormatted: String,
        reason: String,
        responsiblePerson: String,
        notes: String
    ): Long {
        val movement = StockMovementEntity(
            productId = productId,
            variantId = variantId,
            movementType = "ADJUSTMENT",
            quantity = quantity,
            packageCount = packageCount,
            dateMillis = dateMillis,
            dateFormatted = dateFormatted,
            reason = reason,
            responsiblePerson = responsiblePerson,
            notes = notes
        )
        val id = movementDao.insertMovement(movement)
        syncManager.pushMovement(movement.copy(id = id))
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "ADJUSTMENT",
                entityName = "تعديل مخزون",
                entityId = id,
                details = "تعديل بالكمية: $quantity | السبب: $reason | المسؤول: $responsiblePerson"
            )
        )
        return id
    }

    // Reverse a movement (creates an opposite adjustment and marks movement as reversed)
    suspend fun reverseMovement(movementId: Long, reason: String, responsiblePerson: String): Long {
        val movement = movementDao.getMovementById(movementId) ?: return -1L
        if (movement.isReversed || movement.isDeleted) return -1L

        val reverseQuantity = when (movement.movementType) {
            "STOCK_IN" -> -movement.quantity
            "STOCK_OUT" -> movement.quantity
            "ADJUSTMENT" -> -movement.quantity
            else -> 0
        }

        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val adjMovement = StockMovementEntity(
            productId = movement.productId,
            variantId = movement.variantId,
            movementType = "ADJUSTMENT",
            quantity = reverseQuantity,
            packageCount = movement.packageCount,
            dateMillis = now,
            dateFormatted = sdf.format(Date(now)),
            reason = "إلغاء الحركة #${movement.id}: $reason",
            responsiblePerson = responsiblePerson,
            notes = "عكس الحركة الأصلية رقم #${movement.id}"
        )
        val newAdjId = movementDao.insertMovement(adjMovement)
        movementDao.markReversed(movementId, newAdjId, now)
        syncManager.pushMovement(adjMovement.copy(id = newAdjId))
        movementDao.getMovementById(movementId)?.let { syncManager.pushMovement(it) }

        auditLogDao.insertLog(
            AuditLogEntity(
                action = "REVERSE_MOVEMENT",
                entityName = "إلغاء حركة",
                entityId = movementId,
                details = "إلغاء الحركة #${movement.id} بإنشاء حركة تصحيحية #${newAdjId} بالكمية $reverseQuantity"
            )
        )
        return newAdjId
    }

    // Soft delete movement
    suspend fun softDeleteMovement(movementId: Long): Boolean {
        val movement = movementDao.getMovementById(movementId) ?: return false
        movementDao.softDeleteMovement(movementId)
        movementDao.getMovementById(movementId)?.let { syncManager.pushMovement(it) }
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "DELETE_MOVEMENT",
                entityName = "حذف حركة (حذف ناعم)",
                entityId = movementId,
                details = "حذف الحركة #${movement.id} (${movement.movementType} بمقدار ${movement.quantity})"
            )
        )
        return true
    }

    // Get current stock for product/variant
    suspend fun getCurrentStockForProduct(productId: Long, variantId: Long?): Int {
        val product = productDao.getProductById(productId) ?: return 0
        val baseInitial = if (variantId != null) {
            val variant = variantDao.getVariantById(variantId)
            variant?.initialStock ?: 0
        } else {
            product.initialStock
        }

        val movements = movementDao.getAllActiveMovementsList()
        val relevantMovements = movements.filter {
            it.productId == productId && (variantId == null || it.variantId == variantId)
        }
        return StockCalculator.calculateStock(baseInitial, relevantMovements)
    }

    // Generate period report
    suspend fun generatePeriodReport(
        fromMillis: Long,
        toMillis: Long,
        filterProductId: Long? = null,
        filterVariantId: Long? = null
    ): List<PeriodStockResult> {
        val products = productDao.getActiveProducts().first()
        val variants = variantDao.getAllVariants().first()
        val movements = movementDao.getAllMovementsList()

        val results = mutableListOf<PeriodStockResult>()

        for (product in products) {
            if (filterProductId != null && product.id != filterProductId) continue

            val productVariants = variants.filter { it.productId == product.id }
            if (productVariants.isEmpty()) {
                val report = StockCalculator.calculatePeriodReport(
                    productId = product.id,
                    variantId = null,
                    productName = product.nameArabic,
                    variantName = "",
                    initialStock = product.initialStock,
                    movements = movements,
                    fromMillis = fromMillis,
                    toMillis = toMillis
                )
                results.add(report)
            } else {
                for (v in productVariants) {
                    if (filterVariantId != null && v.id != filterVariantId) continue
                    val report = StockCalculator.calculatePeriodReport(
                        productId = product.id,
                        variantId = v.id,
                        productName = product.nameArabic,
                        variantName = v.nameArabic,
                        initialStock = v.initialStock,
                        movements = movements,
                        fromMillis = fromMillis,
                        toMillis = toMillis
                    )
                    results.add(report)
                }
            }
        }

        return results
    }

    // Product CRUD
    suspend fun createProduct(product: ProductEntity, variants: List<ProductVariantEntity> = emptyList()): Long {
        val id = productDao.insertProduct(product)
        val createdProduct = product.copy(id = id)
        syncManager.pushProduct(createdProduct)

        if (variants.isNotEmpty()) {
            val variantsWithId = variants.map { it.copy(productId = id) }
            val variantIds = variantDao.insertAll(variantsWithId)
            variantsWithId.forEachIndexed { index, v ->
                val vId = variantIds.getOrNull(index) ?: v.id
                syncManager.pushVariant(v.copy(id = vId))
            }
        }
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "CREATE_PRODUCT",
                entityName = "صنف جديد",
                entityId = id,
                details = "إضافة صنف: ${product.nameArabic} | رصيد أولي: ${product.initialStock}"
            )
        )
        return id
    }

    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product)
        syncManager.pushProduct(product)
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "UPDATE_PRODUCT",
                entityName = "تعديل صنف",
                entityId = product.id,
                details = "تعديل بيانات الصنف: ${product.nameArabic}"
            )
        )
    }

    suspend fun deleteProduct(productId: Long) {
        val prod = productDao.getProductById(productId)
        val productMovements = movementDao.getAllMovementsList().filter { it.productId == productId }
        for (m in productMovements) {
            syncManager.deleteMovement(m.id)
        }
        movementDao.deleteMovementsByProduct(productId)
        variantDao.deleteVariantsByProduct(productId)
        productDao.deleteProductById(productId)
        syncManager.deleteProduct(productId)
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "DELETE_PRODUCT",
                entityName = "حذف صنف",
                entityId = productId,
                details = "حذف الصنف: ${prod?.nameArabic ?: productId} مع كافة حركاته وتفريعاته"
            )
        )
    }

    suspend fun renameCategory(oldCategory: String, newCategory: String) {
        val trimmedOld = oldCategory.trim()
        val trimmedNew = newCategory.trim()
        if (trimmedOld.isBlank() || trimmedNew.isBlank() || trimmedOld == trimmedNew) return
        productDao.updateCategoryName(trimmedOld, trimmedNew)
        // Sync updated products in this category
        val updatedProducts = productDao.getActiveProductsList().filter { it.category == trimmedNew }
        for (p in updatedProducts) {
            syncManager.pushProduct(p)
        }
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "RENAME_CATEGORY",
                entityName = "تعديل اسم التصنيف",
                details = "تغيير اسم التصنيف من '$trimmedOld' إلى '$trimmedNew'"
            )
        )
    }

    // Destination CRUD
    suspend fun addDestination(destination: DestinationEntity): Long {
        val id = destinationDao.insertDestination(destination)
        syncManager.pushDestination(destination.copy(id = id))
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "CREATE_DESTINATION",
                entityName = "وجهة توزيع",
                entityId = id,
                details = "إضافة وجهة جديدة: ${destination.name} (${destination.type})"
            )
        )
        return id
    }

    suspend fun updateDestination(destination: DestinationEntity) {
        destinationDao.updateDestination(destination)
        syncManager.pushDestination(destination)
    }

    suspend fun deleteDestination(destinationId: Long) {
        destinationDao.deleteDestinationById(destinationId)
        syncManager.deleteDestination(destinationId)
    }

    suspend fun getOrCreateDestination(name: String, type: String, address: String = ""): DestinationEntity {
        val existing = destinationDao.getDestinationByName(name)
        if (existing != null) {
            if (address.isNotBlank() && existing.address != address) {
                val updated = existing.copy(address = address)
                destinationDao.updateDestination(updated)
                syncManager.pushDestination(updated)
                return updated
            }
            return existing
        }
        val newDest = DestinationEntity(name = name, type = type, address = address)
        val id = destinationDao.insertDestination(newDest)
        val withId = newDest.copy(id = id)
        syncManager.pushDestination(withId)
        return withId
    }

    suspend fun getNextVoucherSequenceNumber(): String {
        val movements = movementDao.getAllActiveMovementsList()
        val outMovements = movements.filter { it.movementType == "STOCK_OUT" }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        var maxSeq = 0
        val regex = Regex("""(\d+)""")
        for (m in outMovements) {
            val ref = m.referenceNumber
            if (ref.isNotBlank()) {
                val matches = regex.findAll(ref).mapNotNull { it.value.toIntOrNull() }.filter { it != currentYear }.toList()
                for (num in matches) {
                    if (num in 1..99999 && num > maxSeq) {
                        maxSeq = num
                    }
                }
            }
        }

        val nextSeq = if (maxSeq > 0) maxSeq + 1 else (outMovements.size + 1)
        return String.format(Locale.US, "%03d/%d", nextSeq, currentYear)
    }

    // Update Opening / Initial Stock
    suspend fun updateInitialStock(productId: Long, variantId: Long?, newInitialStock: Int) {
        if (variantId != null) {
            val variant = variantDao.getVariantById(variantId)
            if (variant != null) {
                val updated = variant.copy(initialStock = newInitialStock)
                variantDao.updateVariant(updated)
                syncManager.pushVariant(updated)
            }
        } else {
            val prod = productDao.getProductById(productId)
            if (prod != null) {
                val updated = prod.copy(initialStock = newInitialStock)
                productDao.updateProduct(updated)
                syncManager.pushProduct(updated)
            }
        }
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "INITIAL_STOCK",
                entityName = "تعديل الرصيد الافتتاحي",
                entityId = productId,
                details = "تعديل الرصيد الافتتاحي إلى: $newInitialStock"
            )
        )
    }

    suspend fun updateProductDetails(
        productId: Long,
        variantId: Long?,
        nameArabic: String,
        category: String,
        formatType: String,
        variantNameArabic: String,
        minimumStock: Int
    ) {
        val prod = productDao.getProductById(productId)
        if (prod != null) {
            val updated = prod.copy(
                nameArabic = nameArabic.ifBlank { prod.nameArabic },
                category = category.ifBlank { prod.category },
                formatType = formatType.ifBlank { prod.formatType },
                minimumStock = minimumStock
            )
            productDao.updateProduct(updated)
            syncManager.pushProduct(updated)
        }
        if (variantId != null) {
            val variant = variantDao.getVariantById(variantId)
            if (variant != null) {
                val updatedV = variant.copy(
                    nameArabic = variantNameArabic.ifBlank { variant.nameArabic },
                    minimumStock = minimumStock
                )
                variantDao.updateVariant(updatedV)
                syncManager.pushVariant(updatedV)
            }
        }
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "UPDATE_PRODUCT",
                entityName = "تعديل بيانات الصنف",
                entityId = productId,
                details = "تعديل بيانات الصنف: $nameArabic"
            )
        )
    }

    // Comprehensive Initial Cloud Sync
    suspend fun initialCloudSyncAndSeed() = withContext(Dispatchers.IO) {
        try {
            syncManager.startRealtimeListeners()
            syncManager.pullAllFromCloud()
        } catch (e: Exception) {
            android.util.Log.e("StockRepository", "initialCloudSync error", e)
        }
    }

    // Clear all data safely in child-to-parent order on IO dispatcher (local and cloud)
    suspend fun clearAllData(clearCloud: Boolean = true) = withContext(Dispatchers.IO) {
        try {
            syncManager.isClearingInProgress = true
            syncManager.stopListeners()
            // 1. Initial local wipe
            movementDao.clearAllMovements()
            variantDao.clearAllVariants()
            productDao.clearAllProducts()
            destinationDao.clearAllDestinations()
            auditLogDao.clearLogs()

            // 2. Cloud wipe if enabled
            if (clearCloud) {
                try {
                    syncManager.clearAllCloudData()
                } catch (e: Throwable) {
                    android.util.Log.e("StockRepository", "clearAllCloudData error", e)
                }
            }

            // 3. Final local confirmation wipe so 0 records remain
            movementDao.clearAllMovements()
            variantDao.clearAllVariants()
            productDao.clearAllProducts()
            destinationDao.clearAllDestinations()
            auditLogDao.clearLogs()
        } finally {
            syncManager.isClearingInProgress = false
            syncManager.startRealtimeListeners()
        }
    }
}

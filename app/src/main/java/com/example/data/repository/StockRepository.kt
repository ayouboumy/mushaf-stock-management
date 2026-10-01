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

    fun generateUniqueId(): Long {
        val now = System.currentTimeMillis()
        val rand = (100..999).random()
        return now * 1000L + rand
    }

    init {
        syncManager.startRealtimeListeners()
    }

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

                val current = product.initialStock + totalIn - totalOut + totalAdj
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

                    val current = variant.initialStock + totalIn - totalOut + totalAdj
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
        val uniqueId = generateUniqueId()
        val movement = StockMovementEntity(
            id = uniqueId,
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
        movementDao.insertMovement(movement)
        syncManager.pushMovement(movement)
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "STOCK_IN",
                entityName = "حركة إدخال",
                entityId = uniqueId,
                details = "إدخال كمية: $quantity | المصدر: $source | المرجع: $referenceNumber"
            )
        )
        return uniqueId
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

        val uniqueId = generateUniqueId()
        val movement = StockMovementEntity(
            id = uniqueId,
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
        movementDao.insertMovement(movement)
        syncManager.pushMovement(movement)
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "STOCK_OUT",
                entityName = "حركة إخراج",
                entityId = uniqueId,
                details = "توزيع كمية: $quantity | الوجهة: $destinationName | المكلف: $responsiblePerson"
            )
        )
        return Pair(uniqueId, ValidationResult(true))
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
        val uniqueId = generateUniqueId()
        val movement = StockMovementEntity(
            id = uniqueId,
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
        movementDao.insertMovement(movement)
        syncManager.pushMovement(movement)
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "ADJUSTMENT",
                entityName = "تعديل مخزون",
                entityId = uniqueId,
                details = "تعديل بالكمية: $quantity | السبب: $reason | المسؤول: $responsiblePerson"
            )
        )
        return uniqueId
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

        val uniqueAdjId = generateUniqueId()
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val adjMovement = StockMovementEntity(
            id = uniqueAdjId,
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
        movementDao.insertMovement(adjMovement)
        movementDao.markReversed(movementId, uniqueAdjId, now)
        syncManager.pushMovement(adjMovement)
        movementDao.getMovementById(movementId)?.let { syncManager.pushMovement(it) }

        auditLogDao.insertLog(
            AuditLogEntity(
                action = "REVERSE_MOVEMENT",
                entityName = "إلغاء حركة",
                entityId = movementId,
                details = "إلغاء الحركة #${movement.id} بإنشاء حركة تصحيحية #${uniqueAdjId} بالكمية $reverseQuantity"
            )
        )
        return uniqueAdjId
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
        val uniqueId = if (product.id > 0) product.id else generateUniqueId()
        val createdProduct = product.copy(id = uniqueId)
        productDao.insertProduct(createdProduct)
        syncManager.pushProduct(createdProduct)

        if (variants.isNotEmpty()) {
            val variantsWithId = variants.map {
                it.copy(
                    id = if (it.id > 0) it.id else generateUniqueId(),
                    productId = uniqueId
                )
            }
            variantDao.insertAll(variantsWithId)
            for (v in variantsWithId) {
                syncManager.pushVariant(v)
            }
        }
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "CREATE_PRODUCT",
                entityName = "صنف جديد",
                entityId = uniqueId,
                details = "إضافة صنف: ${product.nameArabic} | رصيد أولي: ${product.initialStock}"
            )
        )
        return uniqueId
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
        productDao.deleteProductById(productId)
        variantDao.deleteVariantsByProduct(productId)
        syncManager.deleteProduct(productId)
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "DELETE_PRODUCT",
                entityName = "حذف صنف",
                entityId = productId,
                details = "حذف الصنف: ${prod?.nameArabic ?: productId}"
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
        val uniqueId = if (destination.id > 0) destination.id else generateUniqueId()
        val destWithId = destination.copy(id = uniqueId)
        destinationDao.insertDestination(destWithId)
        syncManager.pushDestination(destWithId)
        auditLogDao.insertLog(
            AuditLogEntity(
                action = "CREATE_DESTINATION",
                entityName = "وجهة توزيع",
                entityId = uniqueId,
                details = "إضافة وجهة جديدة: ${destination.name} (${destination.type})"
            )
        )
        return uniqueId
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
        val uniqueId = generateUniqueId()
        val newDest = DestinationEntity(id = uniqueId, name = name, type = type, address = address)
        destinationDao.insertDestination(newDest)
        syncManager.pushDestination(newDest)
        return newDest
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

    // Comprehensive Initial Cloud Sync & Fallback Seeding
    suspend fun initialCloudSyncAndSeed() = withContext(Dispatchers.IO) {
        try {
            syncManager.startRealtimeListeners()
            val cloudHasData = syncManager.pullAllFromCloud()
            val localCount = productDao.getActiveProductsList().size
            if (!cloudHasData && localCount == 0) {
                seedDemoDataIfEmpty()
                syncManager.pushAllToCloud()
            }
        } catch (e: Exception) {
            android.util.Log.e("StockRepository", "initialCloudSyncAndSeed error", e)
            val localCount = productDao.getActiveProductsList().size
            if (localCount == 0) {
                seedDemoDataIfEmpty()
            }
        }
    }

    // Seed realistic demo data reflecting the actual 2026 Excel workbook
    suspend fun seedDemoDataIfEmpty() {
        val count = productDao.getActiveProductsList().size
        if (count > 0) return

        // Create standard Moroccan products from the workbook
        val p1 = ProductEntity(
            id = 1L,
            nameArabic = "المصحف المحمدي",
            nameFrench = "Le Noble Coran Mohammadéen",
            category = "مصحف شريف",
            formatType = "حجم عادي كبير (رواية ورش)",
            unit = "نسخة",
            packageQuantity = 10,
            minimumStock = 300,
            initialStock = 5000,
            notes = "طبعة وزارة الأوقاف والشؤون الإسلامية المغربية"
        )
        val p1Id = productDao.insertProduct(p1)

        val p2 = ProductEntity(
            id = 2L,
            nameArabic = "المصحف المجزأ",
            nameFrench = "Coran Fractionné (Parties)",
            category = "أجزاء",
            formatType = "أجزاء منفصلة (30 جزء)",
            unit = "طقم",
            packageQuantity = 5,
            minimumStock = 150,
            initialStock = 1500,
            notes = "مصحف في علبة 30 جزء للكتاتيب والمساجد"
        )
        val p2Id = productDao.insertProduct(p2)

        val p3 = ProductEntity(
            id = 3L,
            nameArabic = "الهدايا",
            nameFrench = "Édition Cadeau de Luxe",
            category = "خاص",
            formatType = "طبعة فاخرة بغلاف مخملي ومذهب",
            unit = "نسخة",
            packageQuantity = 5,
            minimumStock = 50,
            initialStock = 600,
            notes = "إصدار التشريفات والهدايا الرسمية"
        )
        val p3Id = productDao.insertProduct(p3)

        val p4 = ProductEntity(
            id = 4L,
            nameArabic = "جزء عم",
            nameFrench = "Juz' Amma",
            category = "أجزاء",
            formatType = "كتيب مستقل",
            unit = "نسخة",
            packageQuantity = 50,
            minimumStock = 200,
            initialStock = 2000,
            notes = "مخصص لطلبة الكتاتيب والمدارس العتيقة"
        )
        val p4Id = productDao.insertProduct(p4)

        val p5 = ProductEntity(
            id = 5L,
            nameArabic = "جزء عم - خمسة أحزاب",
            nameFrench = "Cinq Ahzab",
            category = "أجزاء",
            formatType = "كتيب 5 أحزاب",
            unit = "نسخة",
            packageQuantity = 30,
            minimumStock = 150,
            initialStock = 1200,
            notes = "الأحزاب الخمسة الأخيرة"
        )
        val p5Id = productDao.insertProduct(p5)

        val p6 = ProductEntity(
            id = 6L,
            nameArabic = "ضعاف البصر",
            nameFrench = "Grands Caractères (Malvoyants)",
            category = "خاص",
            formatType = "خط كبير جداً عالي التباين",
            unit = "نسخة",
            packageQuantity = 5,
            minimumStock = 80,
            initialStock = 400,
            notes = "مخصص للمسنين وضعاف البصر"
        )
        val p6Id = productDao.insertProduct(p6)

        val p7 = ProductEntity(
            id = 7L,
            nameArabic = "برايل",
            nameFrench = "Coran en Braille",
            category = "خاص",
            formatType = "مجلدات برايل للمكفوفين (6 مجلدات)",
            unit = "طقم",
            packageQuantity = 1,
            minimumStock = 20,
            initialStock = 100,
            notes = "طبعة مسبوكة للمكفوفين"
        )
        val p7Id = productDao.insertProduct(p7)

        val p8 = ProductEntity(
            id = 8L,
            nameArabic = "المصحف الجيبي",
            nameFrench = "Format Poche",
            category = "مصحف شريف",
            formatType = "قياس الجيب الصغير",
            unit = "نسخة",
            packageQuantity = 20,
            minimumStock = 100,
            initialStock = 800,
            notes = "سهل الحمل للمسافرين والحجاج"
        )
        val p8Id = productDao.insertProduct(p8)

        // Translated Mushafs with real figures from the Excel workbook
        val p9 = ProductEntity(
            id = 9L,
            nameArabic = "المصحف المترجم",
            nameFrench = "Traductions du Noble Coran",
            category = "مترجم",
            formatType = "نص عربي مع ترجمة المعاني",
            unit = "نسخة",
            packageQuantity = 10,
            minimumStock = 100,
            initialStock = 0,
            notes = "تراجم معتمدة بلغات متعددة للجاليات والبعثات"
        )
        val p9Id = productDao.insertProduct(p9)

        // Variants for translated Mushaf
        val vFr = ProductVariantEntity(
            id = 101L,
            productId = p9Id,
            nameArabic = "الفرنسية",
            nameFrench = "Français",
            code = "FR",
            initialStock = 384,
            minimumStock = 50,
            packageQuantity = 10
        )
        val vEn = ProductVariantEntity(
            id = 102L,
            productId = p9Id,
            nameArabic = "الإنجليزية",
            nameFrench = "English",
            code = "EN",
            initialStock = 767,
            minimumStock = 80,
            packageQuantity = 10
        )
        val vEs = ProductVariantEntity(
            id = 103L,
            productId = p9Id,
            nameArabic = "الإسبانية",
            nameFrench = "Español",
            code = "ES",
            initialStock = 270,
            minimumStock = 40,
            packageQuantity = 10
        )
        val vFrId = variantDao.insertVariant(vFr)
        val vEnId = variantDao.insertVariant(vEn)
        val vEsId = variantDao.insertVariant(vEs)

        // Add typical destinations
        val destList = listOf(
            DestinationEntity(id = 1L, name = "مسجد السنة - الرباط", type = "مسجد", commune = "حسان", province = "الرباط"),
            DestinationEntity(id = 2L, name = "مسجد حسان - الرباط", type = "مسجد", commune = "حسان", province = "الرباط"),
            DestinationEntity(id = 3L, name = "المجلس العلمي المحلي - الصخيرات تمارة", type = "مجلس علمي", commune = "تمارة", province = "الصخيرات تمارة"),
            DestinationEntity(id = 4L, name = "المجلس العلمي المحلي - سلا", type = "مجلس علمي", commune = "سلا المدينة", province = "سلا"),
            DestinationEntity(id = 5L, name = "مؤسسة محمد السادس للنهوض بالأعمال الاجتماعية للقيمين الدينيين", type = "مؤسسة", commune = "أكدال", province = "الرباط"),
            DestinationEntity(id = 6L, name = "جمعية رعاية الكتاتيب القرآنية", type = "جمعية", commune = "سلا الجديدة", province = "سلا"),
            DestinationEntity(id = 7L, name = "معهد محمد السادس للقراءات والدراسات القرآنية", type = "مؤسسة", commune = "الرباط", province = "الرباط"),
            DestinationEntity(id = 8L, name = "حفل تكريم حفظة كتاب الله السنوي", type = "حفل", commune = "الرباط", province = "الرباط"),
            DestinationEntity(id = 9L, name = "بعثة الحجاج المغاربة إلى الديار المقدسة", type = "حاج", commune = "الدار البيضاء", province = "الدار البيضاء"),
            DestinationEntity(id = 10L, name = "المراكز الإسلامية للمغاربة المقيمين بالخارج", type = "جالية", commune = "باريس / بروكسيل", province = "أوروبا")
        )
        val destIds = destinationDao.insertAll(destList)

        // Historical dates
        val now = System.currentTimeMillis()
        val dayMillis = 86400000L
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val sampleMovements = listOf(
            StockMovementEntity(
                id = 1001L,
                productId = p1Id,
                movementType = "STOCK_IN",
                quantity = 1000,
                packageCount = 100,
                dateMillis = now - 20 * dayMillis,
                dateFormatted = sdf.format(Date(now - 20 * dayMillis)),
                source = "مطبعة فضالة - المحمدية",
                responsiblePerson = "عبد الله المرابط",
                referenceNumber = "BL-2026/041",
                notes = "دفعة جديدة من المصحف المحمدي"
            ),
            StockMovementEntity(
                id = 1002L,
                productId = p1Id,
                movementType = "STOCK_OUT",
                quantity = 350,
                packageCount = 35,
                dateMillis = now - 15 * dayMillis,
                dateFormatted = sdf.format(Date(now - 15 * dayMillis)),
                destinationId = 1L,
                destinationName = "مسجد السنة - الرباط",
                destinationType = "مسجد",
                responsiblePerson = "أحمد التازي",
                referenceNumber = "BS-2026/102",
                notes = "تزويد المسجد بالمصاحف بمناسبة حلول شهر رمضان"
            ),
            StockMovementEntity(
                id = 1003L,
                productId = p1Id,
                movementType = "STOCK_OUT",
                quantity = 150,
                packageCount = 15,
                dateMillis = now - 8 * dayMillis,
                dateFormatted = sdf.format(Date(now - 8 * dayMillis)),
                destinationId = 3L,
                destinationName = "المجلس العلمي المحلي - الصخيرات تمارة",
                destinationType = "مجلس علمي",
                responsiblePerson = "محمد الفاسي",
                referenceNumber = "BS-2026/109",
                notes = "توزيع للمساجد التابعة للمجلس"
            ),
            StockMovementEntity(
                productId = p2Id,
                movementType = "STOCK_OUT",
                quantity = 100,
                packageCount = 20,
                dateMillis = now - 12 * dayMillis,
                dateFormatted = sdf.format(Date(now - 12 * dayMillis)),
                destinationId = destIds.getOrNull(5) ?: 6L,
                destinationName = "جمعية رعاية الكتاتيب القرآنية",
                destinationType = "جمعية",
                responsiblePerson = "عمر الكتاني",
                referenceNumber = "BS-2026/105",
                notes = "توزيع لكتاتيب تحفيظ القرآن الكريم"
            ),
            StockMovementEntity(
                productId = p3Id,
                movementType = "STOCK_OUT",
                quantity = 30,
                packageCount = 6,
                dateMillis = now - 5 * dayMillis,
                dateFormatted = sdf.format(Date(now - 5 * dayMillis)),
                destinationId = destIds.getOrNull(7) ?: 8L,
                destinationName = "حفل تكريم حفظة كتاب الله السنوي",
                destinationType = "حفل",
                responsiblePerson = "إدريس بنجلون",
                referenceNumber = "BS-2026/114",
                notes = "جوائز للمتفوقين في مسابقة الحفظ الوطنية"
            ),
            StockMovementEntity(
                productId = p9Id,
                variantId = vFrId,
                movementType = "STOCK_OUT",
                quantity = 40,
                packageCount = 4,
                dateMillis = now - 3 * dayMillis,
                dateFormatted = sdf.format(Date(now - 3 * dayMillis)),
                destinationId = destIds.getOrNull(9) ?: 10L,
                destinationName = "المراكز الإسلامية للمغاربة المقيمين بالخارج",
                destinationType = "جالية",
                responsiblePerson = "كريم السبتي",
                referenceNumber = "BS-2026/118",
                notes = "إرسال للمراكز الإسلامية بفرنسا"
            ),
            StockMovementEntity(
                productId = p7Id,
                movementType = "ADJUSTMENT",
                quantity = -2,
                packageCount = 0,
                dateMillis = now - 2 * dayMillis,
                dateFormatted = sdf.format(Date(now - 2 * dayMillis)),
                reason = "أجزاء تالفة أثناء النقل والتخزين",
                responsiblePerson = "مراقب الجودة عبد الحق",
                notes = "تسجيل تلف مجلدين من طبعة برايل"
            )
        )
        movementDao.insertAll(sampleMovements)

        auditLogDao.insertLog(
            AuditLogEntity(
                action = "IMPORT_EXCEL",
                entityName = "البيانات الأولية",
                entityId = 0,
                details = "تهيئة بيانات مخزون المصحف المحمدي لعام 2026 بنجاح"
            )
        )
    }

    // Clear all data safely in child-to-parent order on IO dispatcher
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        movementDao.clearAllMovements()
        variantDao.clearAllVariants()
        productDao.clearAllProducts()
        destinationDao.clearAllDestinations()
        auditLogDao.clearLogs()
    }
}

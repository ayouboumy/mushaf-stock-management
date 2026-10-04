package com.example.data.sync

import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.entity.DestinationEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ProductVariantEntity
import com.example.data.entity.StockMovementEntity
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class CloudSyncDiagnostic {
    object Idle : CloudSyncDiagnostic()
    object Checking : CloudSyncDiagnostic()
    object Connected : CloudSyncDiagnostic()
    data class DatabaseNotFound(val message: String, val technicalDetail: String) : CloudSyncDiagnostic()
    data class PermissionDenied(val message: String, val technicalDetail: String) : CloudSyncDiagnostic()
    data class NetworkError(val message: String, val technicalDetail: String) : CloudSyncDiagnostic()
    data class GeneralError(val message: String, val technicalDetail: String) : CloudSyncDiagnostic()
}

class FirebaseSyncManager(private val database: AppDatabase) {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val coroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e("FirebaseSync", "Unhandled sync error caught safely", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)

    private val listeners = mutableListOf<ListenerRegistration>()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _syncDiagnostic = MutableStateFlow<CloudSyncDiagnostic>(CloudSyncDiagnostic.Idle)
    val syncDiagnostic: StateFlow<CloudSyncDiagnostic> = _syncDiagnostic.asStateFlow()

    fun parseErrorToDiagnostic(e: Throwable): CloudSyncDiagnostic {
        val msg = e.message ?: e.localizedMessage ?: "Unknown error"
        return when {
            msg.contains("NOT_FOUND", ignoreCase = true) || msg.contains("does not exist", ignoreCase = true) || msg.contains("404", ignoreCase = true) -> {
                CloudSyncDiagnostic.DatabaseNotFound(
                    message = "قاعدة بيانات Firestore الافتراضية (default) محذوفة أو غير مفعلة في مشروع Firebase.",
                    technicalDetail = msg
                )
            }
            msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("Missing or insufficient permissions", ignoreCase = true) -> {
                CloudSyncDiagnostic.PermissionDenied(
                    message = "قواعد الأمان (Rules) في Firestore تمنع قراءة أو كتابة البيانات.",
                    technicalDetail = msg
                )
            }
            msg.contains("UNAVAILABLE", ignoreCase = true) || msg.contains("network", ignoreCase = true) || msg.contains("offline", ignoreCase = true) -> {
                CloudSyncDiagnostic.NetworkError(
                    message = "تعذر الاتصال بخوادم Firebase (يرجى التحقق من اتصال الإنترنت).",
                    technicalDetail = msg
                )
            }
            else -> {
                CloudSyncDiagnostic.GeneralError(
                    message = "خطأ في المزامنة السحابية: $msg",
                    technicalDetail = msg
                )
            }
        }
    }

    suspend fun diagnoseConnection(): CloudSyncDiagnostic = withContext(Dispatchers.IO) {
        _syncDiagnostic.value = CloudSyncDiagnostic.Checking
        try {
            ensureAuth()
            firestore.collection("products").limit(1).get().await()
            _syncError.value = null
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            CloudSyncDiagnostic.Connected
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncError.value = e.localizedMessage ?: e.message
            _syncDiagnostic.value = diag
            diag
        }
    }

    @Volatile
    private var authAttempted = false
    @Volatile
    private var isAuthDisabled = false

    // Ensure user is signed in to Firebase Auth if available, otherwise direct Firestore access
    suspend fun ensureAuth(): Boolean {
        if (isAuthDisabled) return true
        if (auth.currentUser != null) return true

        return try {
            auth.signInAnonymously().await()
            Log.d("FirebaseSync", "Firebase anonymous auth successful: ${auth.currentUser?.uid}")
            true
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: ""
            Log.w("FirebaseSync", "Firebase Auth not active ($msg), using direct Firestore sync")
            isAuthDisabled = true
            true
        }
    }

    // Register or sign in user with email & password in Firebase Auth + Firestore
    suspend fun registerOrAuthUser(profile: UserProfile, password: String = ""): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (profile.email.isNotBlank() && password.length >= 6) {
                    try {
                        auth.createUserWithEmailAndPassword(profile.email.trim(), password).await()
                        Log.d("FirebaseSync", "User registered in Firebase Auth: ${auth.currentUser?.uid}")
                    } catch (e: Exception) {
                        // User might already exist, try sign in
                        try {
                            auth.signInWithEmailAndPassword(profile.email.trim(), password).await()
                            Log.d("FirebaseSync", "User signed in to Firebase Auth: ${auth.currentUser?.uid}")
                        } catch (e2: Exception) {
                            Log.w("FirebaseSync", "Auth fallback to anonymous: ${e2.localizedMessage}")
                            ensureAuth()
                        }
                    }
                } else {
                    ensureAuth()
                }

                syncUserProfile(profile)
                true
            } catch (e: Exception) {
                Log.e("FirebaseSync", "registerOrAuthUser error", e)
                true // proceed gracefully so local usage is not blocked
            }
        }
    }

    // Start Realtime Firestore Listeners to receive changes from other devices instantly
    fun startRealtimeListeners() {
        scope.launch {
            try {
                ensureAuth()
                stopListeners()

                // 1. Listen to Products
                val prodListener = firestore.collection("products")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirebaseSync", "Products listen error: ${error.message}")
                            _syncDiagnostic.value = parseErrorToDiagnostic(error)
                            _syncError.value = error.message
                            return@addSnapshotListener
                        }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null) {
                            scope.launch {
                                try {
                                    if (snapshot.isEmpty) {
                                        database.productDao().clearAllProducts()
                                    } else {
                                        val remoteIds = mutableSetOf<Long>()
                                        for (doc in snapshot.documents) {
                                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                                            val isActive = doc.getBoolean("active") ?: true
                                            if (isActive) {
                                                remoteIds.add(id)
                                                val entity = ProductEntity(
                                                    id = id,
                                                    nameArabic = doc.getString("nameArabic") ?: "",
                                                    nameFrench = doc.getString("nameFrench") ?: "",
                                                    category = doc.getString("category") ?: "",
                                                    formatType = doc.getString("formatType") ?: "",
                                                    unit = doc.getString("unit") ?: "نسخة",
                                                    packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 1,
                                                    minimumStock = doc.getLong("minimumStock")?.toInt() ?: 0,
                                                    initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                                                    notes = doc.getString("notes") ?: "",
                                                    active = true
                                                )
                                                database.productDao().insertProduct(entity)
                                            } else {
                                                database.productDao().deleteProductById(id)
                                            }
                                        }
                                        val localProducts = database.productDao().getAllProductsList()
                                        for (lp in localProducts) {
                                            if (!remoteIds.contains(lp.id)) {
                                                database.productDao().deleteProductById(lp.id)
                                            }
                                        }
                                    }
                                    _lastSyncTimestamp.value = System.currentTimeMillis()
                                } catch (e: Exception) {
                                    Log.e("FirebaseSync", "Error syncing products from Firestore", e)
                                }
                            }
                        }
                    }
                listeners.add(prodListener)

                // 2. Listen to Product Variants
                val variantListener = firestore.collection("variants")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirebaseSync", "Variants listen error: ${error.message}")
                            _syncDiagnostic.value = parseErrorToDiagnostic(error)
                            _syncError.value = error.message
                            return@addSnapshotListener
                        }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null) {
                            scope.launch {
                                try {
                                    if (snapshot.isEmpty) {
                                        database.productVariantDao().clearAllVariants()
                                    } else {
                                        val remoteIds = mutableSetOf<Long>()
                                        for (doc in snapshot.documents) {
                                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                                            val isActive = doc.getBoolean("active") ?: true
                                            if (isActive) {
                                                remoteIds.add(id)
                                                val entity = ProductVariantEntity(
                                                    id = id,
                                                    productId = doc.getLong("productId") ?: 0L,
                                                    nameArabic = doc.getString("nameArabic") ?: "",
                                                    nameFrench = doc.getString("nameFrench") ?: "",
                                                    code = doc.getString("code") ?: "",
                                                    initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                                                    minimumStock = doc.getLong("minimumStock")?.toInt() ?: 20,
                                                    packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 10,
                                                    notes = doc.getString("notes") ?: "",
                                                    active = true
                                                )
                                                database.productVariantDao().insertVariant(entity)
                                            } else {
                                                database.productVariantDao().deleteVariantById(id)
                                            }
                                        }
                                        val localVariants = database.productVariantDao().getAllVariantsList()
                                        for (lv in localVariants) {
                                            if (!remoteIds.contains(lv.id)) {
                                                database.productVariantDao().deleteVariantById(lv.id)
                                            }
                                        }
                                    }
                                    _lastSyncTimestamp.value = System.currentTimeMillis()
                                } catch (e: Exception) {
                                    Log.e("FirebaseSync", "Error syncing variants from Firestore", e)
                                }
                            }
                        }
                    }
                listeners.add(variantListener)

                // 3. Listen to Stock Movements
                val moveListener = firestore.collection("movements")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirebaseSync", "Movements listen error: ${error.message}")
                            _syncDiagnostic.value = parseErrorToDiagnostic(error)
                            _syncError.value = error.message
                            return@addSnapshotListener
                        }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null) {
                            scope.launch {
                                try {
                                    if (snapshot.isEmpty) {
                                        database.stockMovementDao().clearAllMovements()
                                    } else {
                                        val remoteIds = mutableSetOf<Long>()
                                        for (doc in snapshot.documents) {
                                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                                            val isDel = doc.getBoolean("isDeleted") ?: false
                                            if (!isDel) {
                                                remoteIds.add(id)
                                                val entity = StockMovementEntity(
                                                    id = id,
                                                    productId = doc.getLong("productId") ?: 0L,
                                                    variantId = doc.getLong("variantId"),
                                                    movementType = doc.getString("movementType") ?: "STOCK_IN",
                                                    quantity = doc.getLong("quantity")?.toInt() ?: 0,
                                                    packageCount = doc.getLong("packageCount")?.toInt() ?: 0,
                                                    dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                                                    dateFormatted = doc.getString("dateFormatted") ?: "",
                                                    source = doc.getString("source") ?: "",
                                                    destinationId = doc.getLong("destinationId"),
                                                    destinationName = doc.getString("destinationName") ?: "",
                                                    destinationType = doc.getString("destinationType") ?: "",
                                                    reason = doc.getString("reason") ?: "",
                                                    responsiblePerson = doc.getString("responsiblePerson") ?: "",
                                                    referenceNumber = doc.getString("referenceNumber") ?: "",
                                                    notes = doc.getString("notes") ?: "",
                                                    isReversed = doc.getBoolean("isReversed") ?: false,
                                                    reversedByMovementId = doc.getLong("reversedByMovementId"),
                                                    isDeleted = false
                                                )
                                                database.stockMovementDao().insertMovement(entity)
                                            } else {
                                                database.stockMovementDao().deleteMovementById(id)
                                            }
                                        }
                                        val localMovements = database.stockMovementDao().getAllActiveMovementsList()
                                        for (lm in localMovements) {
                                            if (!remoteIds.contains(lm.id)) {
                                                database.stockMovementDao().deleteMovementById(lm.id)
                                            }
                                        }
                                    }
                                    _lastSyncTimestamp.value = System.currentTimeMillis()
                                } catch (e: Exception) {
                                    Log.e("FirebaseSync", "Error syncing movements from Firestore", e)
                                }
                            }
                        }
                    }
                listeners.add(moveListener)

                // 4. Listen to Destinations
                val destListener = firestore.collection("destinations")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirebaseSync", "Destinations listen error: ${error.message}")
                            _syncDiagnostic.value = parseErrorToDiagnostic(error)
                            _syncError.value = error.message
                            return@addSnapshotListener
                        }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null) {
                            scope.launch {
                                try {
                                    if (snapshot.isEmpty) {
                                        database.destinationDao().clearAllDestinations()
                                    } else {
                                        val remoteIds = mutableSetOf<Long>()
                                        for (doc in snapshot.documents) {
                                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                                            remoteIds.add(id)
                                            val entity = DestinationEntity(
                                                id = id,
                                                name = doc.getString("name") ?: "",
                                                type = doc.getString("type") ?: "مسجد",
                                                commune = doc.getString("commune") ?: "",
                                                province = doc.getString("province") ?: "",
                                                address = doc.getString("address") ?: "",
                                                contactPerson = doc.getString("contactPerson") ?: "",
                                                phone = doc.getString("phone") ?: ""
                                            )
                                            database.destinationDao().insertDestination(entity)
                                        }
                                        val localDestinations = database.destinationDao().getAllDestinationsList()
                                        for (ld in localDestinations) {
                                            if (!remoteIds.contains(ld.id)) {
                                                database.destinationDao().deleteDestinationById(ld.id)
                                            }
                                        }
                                    }
                                    _lastSyncTimestamp.value = System.currentTimeMillis()
                                } catch (e: Exception) {
                                    Log.e("FirebaseSync", "Error syncing destinations from Firestore", e)
                                }
                            }
                        }
                    }
                listeners.add(destListener)
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Failed to start Firebase listeners", e)
            }
        }
    }

    fun stopListeners() {
        for (l in listeners) {
            l.remove()
        }
        listeners.clear()
    }

    // Pull ALL collections from Firestore into local Room database
    // Returns true if remote catalog (products) was found in Firestore
    suspend fun pullAllFromCloud(): Boolean = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncError.value = null
        try {
            ensureAuth()

            // 1. Pull Products
            val prodDocs = firestore.collection("products").get().await()
            val hasRemoteProducts = !prodDocs.isEmpty
            if (prodDocs.isEmpty) {
                database.productDao().clearAllProducts()
            } else {
                val remoteIds = mutableSetOf<Long>()
                for (doc in prodDocs.documents) {
                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                    val isActive = doc.getBoolean("active") ?: true
                    if (isActive) {
                        remoteIds.add(id)
                        val entity = ProductEntity(
                            id = id,
                            nameArabic = doc.getString("nameArabic") ?: "",
                            nameFrench = doc.getString("nameFrench") ?: "",
                            category = doc.getString("category") ?: "",
                            formatType = doc.getString("formatType") ?: "",
                            unit = doc.getString("unit") ?: "نسخة",
                            packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 1,
                            minimumStock = doc.getLong("minimumStock")?.toInt() ?: 0,
                            initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                            notes = doc.getString("notes") ?: "",
                            active = true
                        )
                        database.productDao().insertProduct(entity)
                    } else {
                        database.productDao().deleteProductById(id)
                    }
                }
                val localProducts = database.productDao().getAllProductsList()
                for (lp in localProducts) {
                    if (!remoteIds.contains(lp.id)) {
                        database.productDao().deleteProductById(lp.id)
                    }
                }
            }

            // 2. Pull Variants
            val varDocs = firestore.collection("variants").get().await()
            if (varDocs.isEmpty) {
                database.productVariantDao().clearAllVariants()
            } else {
                val remoteIds = mutableSetOf<Long>()
                for (doc in varDocs.documents) {
                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                    val isActive = doc.getBoolean("active") ?: true
                    if (isActive) {
                        remoteIds.add(id)
                        val entity = ProductVariantEntity(
                            id = id,
                            productId = doc.getLong("productId") ?: 0L,
                            nameArabic = doc.getString("nameArabic") ?: "",
                            nameFrench = doc.getString("nameFrench") ?: "",
                            code = doc.getString("code") ?: "",
                            initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                            minimumStock = doc.getLong("minimumStock")?.toInt() ?: 20,
                            packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 10,
                            notes = doc.getString("notes") ?: "",
                            active = true
                        )
                        database.productVariantDao().insertVariant(entity)
                    } else {
                        database.productVariantDao().deleteVariantById(id)
                    }
                }
                val localVariants = database.productVariantDao().getAllVariantsList()
                for (lv in localVariants) {
                    if (!remoteIds.contains(lv.id)) {
                        database.productVariantDao().deleteVariantById(lv.id)
                    }
                }
            }

            // 3. Pull Movements
            val moveDocs = firestore.collection("movements").get().await()
            if (moveDocs.isEmpty) {
                database.stockMovementDao().clearAllMovements()
            } else {
                val remoteIds = mutableSetOf<Long>()
                for (doc in moveDocs.documents) {
                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                    val isDel = doc.getBoolean("isDeleted") ?: false
                    if (!isDel) {
                        remoteIds.add(id)
                        val entity = StockMovementEntity(
                            id = id,
                            productId = doc.getLong("productId") ?: 0L,
                            variantId = doc.getLong("variantId"),
                            movementType = doc.getString("movementType") ?: "STOCK_IN",
                            quantity = doc.getLong("quantity")?.toInt() ?: 0,
                            packageCount = doc.getLong("packageCount")?.toInt() ?: 0,
                            dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                            dateFormatted = doc.getString("dateFormatted") ?: "",
                            source = doc.getString("source") ?: "",
                            destinationId = doc.getLong("destinationId"),
                            destinationName = doc.getString("destinationName") ?: "",
                            destinationType = doc.getString("destinationType") ?: "",
                            reason = doc.getString("reason") ?: "",
                            responsiblePerson = doc.getString("responsiblePerson") ?: "",
                            referenceNumber = doc.getString("referenceNumber") ?: "",
                            notes = doc.getString("notes") ?: "",
                            isReversed = doc.getBoolean("isReversed") ?: false,
                            reversedByMovementId = doc.getLong("reversedByMovementId"),
                            isDeleted = false
                        )
                        database.stockMovementDao().insertMovement(entity)
                    } else {
                        database.stockMovementDao().deleteMovementById(id)
                    }
                }
                val localMovements = database.stockMovementDao().getAllActiveMovementsList()
                for (lm in localMovements) {
                    if (!remoteIds.contains(lm.id)) {
                        database.stockMovementDao().deleteMovementById(lm.id)
                    }
                }
            }

            // 4. Pull Destinations
            val destDocs = firestore.collection("destinations").get().await()
            if (destDocs.isEmpty) {
                database.destinationDao().clearAllDestinations()
            } else {
                val remoteIds = mutableSetOf<Long>()
                for (doc in destDocs.documents) {
                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                    remoteIds.add(id)
                    val entity = DestinationEntity(
                        id = id,
                        name = doc.getString("name") ?: "",
                        type = doc.getString("type") ?: "مسجد",
                        commune = doc.getString("commune") ?: "",
                        province = doc.getString("province") ?: "",
                        address = doc.getString("address") ?: "",
                        contactPerson = doc.getString("contactPerson") ?: "",
                        phone = doc.getString("phone") ?: ""
                    )
                    database.destinationDao().insertDestination(entity)
                }
                val localDestinations = database.destinationDao().getAllDestinationsList()
                for (ld in localDestinations) {
                    if (!remoteIds.contains(ld.id)) {
                        database.destinationDao().deleteDestinationById(ld.id)
                    }
                }
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            hasRemoteProducts
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "pullAllFromCloud error", e)
            false
        } finally {
            _isSyncing.value = false
        }
    }

    // Push local data to Firestore
    suspend fun pushProduct(product: ProductEntity) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val data = hashMapOf(
                "id" to product.id,
                "nameArabic" to product.nameArabic,
                "nameFrench" to product.nameFrench,
                "category" to product.category,
                "formatType" to product.formatType,
                "unit" to product.unit,
                "packageQuantity" to product.packageQuantity,
                "minimumStock" to product.minimumStock,
                "initialStock" to product.initialStock,
                "notes" to product.notes,
                "active" to product.active,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("products").document(product.id.toString()).set(data, SetOptions.merge()).await()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "Push product failed", e)
        }
    }

    suspend fun deleteProduct(productId: Long) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            firestore.collection("products").document(productId.toString()).update("active", false, "updatedAt", System.currentTimeMillis()).await()
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "Delete product in Firestore failed", e)
        }
    }

    suspend fun pushVariant(variant: ProductVariantEntity) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val data = hashMapOf(
                "id" to variant.id,
                "productId" to variant.productId,
                "nameArabic" to variant.nameArabic,
                "nameFrench" to variant.nameFrench,
                "code" to variant.code,
                "initialStock" to variant.initialStock,
                "minimumStock" to variant.minimumStock,
                "packageQuantity" to variant.packageQuantity,
                "notes" to variant.notes,
                "active" to variant.active,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("variants").document(variant.id.toString()).set(data, SetOptions.merge()).await()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "Push variant failed", e)
        }
    }

    suspend fun deleteVariant(variantId: Long) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            firestore.collection("variants").document(variantId.toString()).update("active", false, "updatedAt", System.currentTimeMillis()).await()
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "Delete variant in Firestore failed", e)
        }
    }

    suspend fun pushMovement(movement: StockMovementEntity) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val data = hashMapOf(
                "id" to movement.id,
                "productId" to movement.productId,
                "variantId" to movement.variantId,
                "movementType" to movement.movementType,
                "quantity" to movement.quantity,
                "packageCount" to movement.packageCount,
                "dateMillis" to movement.dateMillis,
                "dateFormatted" to movement.dateFormatted,
                "source" to movement.source,
                "destinationId" to movement.destinationId,
                "destinationName" to movement.destinationName,
                "destinationType" to movement.destinationType,
                "reason" to movement.reason,
                "responsiblePerson" to movement.responsiblePerson,
                "referenceNumber" to movement.referenceNumber,
                "notes" to movement.notes,
                "isReversed" to movement.isReversed,
                "reversedByMovementId" to movement.reversedByMovementId,
                "isDeleted" to movement.isDeleted,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("movements").document(movement.id.toString()).set(data, SetOptions.merge()).await()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "Push movement failed", e)
        }
    }

    suspend fun pushDestination(destination: DestinationEntity) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val data = hashMapOf(
                "id" to destination.id,
                "name" to destination.name,
                "type" to destination.type,
                "commune" to destination.commune,
                "province" to destination.province,
                "address" to destination.address,
                "contactPerson" to destination.contactPerson,
                "phone" to destination.phone,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("destinations").document(destination.id.toString()).set(data, SetOptions.merge()).await()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "Push destination failed", e)
        }
    }

    suspend fun deleteDestination(destinationId: Long) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            firestore.collection("destinations").document(destinationId.toString()).delete().await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Delete destination in Firestore failed", e)
        }
    }

    suspend fun syncUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val docId = if (profile.id.isNotBlank()) profile.id else (auth.currentUser?.uid ?: "user_default")
            val data = hashMapOf(
                "id" to docId,
                "fullName" to profile.fullName,
                "role" to profile.role,
                "email" to profile.email,
                "phone" to profile.phone,
                "lastActive" to System.currentTimeMillis()
            )
            firestore.collection("users").document(docId).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Sync user profile failed", e)
        }
    }

    suspend fun pushAllToCloud() = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val products = database.productDao().getActiveProductsList()
            for (p in products) {
                pushProduct(p)
            }
            val variants = database.productVariantDao().getAllVariantsList()
            for (v in variants) {
                pushVariant(v)
            }
            val movements = database.stockMovementDao().getAllActiveMovementsList()
            for (m in movements) {
                pushMovement(m)
            }
            val destinations = database.destinationDao().getAllDestinationsList()
            for (d in destinations) {
                pushDestination(d)
            }
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "pushAllToCloud error", e)
        }
    }

    suspend fun clearAllCloudData() = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val prodDocs = firestore.collection("products").get().await()
            for (doc in prodDocs.documents) {
                doc.reference.delete().await()
            }
            val varDocs = firestore.collection("variants").get().await()
            for (doc in varDocs.documents) {
                doc.reference.delete().await()
            }
            val moveDocs = firestore.collection("movements").get().await()
            for (doc in moveDocs.documents) {
                doc.reference.delete().await()
            }
            val destDocs = firestore.collection("destinations").get().await()
            for (doc in destDocs.documents) {
                doc.reference.delete().await()
            }
            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            Log.d("FirebaseSync", "All cloud data cleared successfully")
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "clearAllCloudData error", e)
        }
    }

    suspend fun fullBidirectionalSync(): Boolean = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncError.value = null
        try {
            ensureAuth()
            // 1. Pull latest changes from Firestore
            val pullOk = pullAllFromCloud()
            if (!pullOk && _syncDiagnostic.value !is CloudSyncDiagnostic.Connected && _syncDiagnostic.value !is CloudSyncDiagnostic.Idle) {
                return@withContext false
            }
            // 2. Push any local changes to Firestore
            pushAllToCloud()
            if (_syncDiagnostic.value !is CloudSyncDiagnostic.Connected && _syncDiagnostic.value !is CloudSyncDiagnostic.Idle) {
                return@withContext false
            }
            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            true
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.e("FirebaseSync", "fullBidirectionalSync error", e)
            false
        } finally {
            _isSyncing.value = false
        }
    }
}

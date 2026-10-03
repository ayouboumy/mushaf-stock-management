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

    @Volatile
    private var authAttempted = false
    @Volatile
    private var isAuthDisabled = false

    // Ensure user is signed in to Firebase Auth (anonymous or real)
    suspend fun ensureAuth(): Boolean {
        if (isAuthDisabled) return true
        if (auth.currentUser != null) return true

        return try {
            auth.signInAnonymously().await()
            Log.d("FirebaseSync", "Firebase anonymous auth successful: ${auth.currentUser?.uid}")
            true
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
                msg.contains("ADMIN_ONLY_OPERATION", ignoreCase = true)
            ) {
                Log.w("FirebaseSync", "Firebase Auth not enabled in console, using direct Firestore sync: $msg")
                isAuthDisabled = true
            } else {
                Log.w("FirebaseSync", "Firebase Auth warning: $msg")
            }
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
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            scope.launch {
                                try {
                                    for (doc in snapshot.documents) {
                                        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
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
                                            active = doc.getBoolean("active") ?: true
                                        )
                                        database.productDao().insertProduct(entity)
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
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            scope.launch {
                                try {
                                    for (doc in snapshot.documents) {
                                        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
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
                                            active = doc.getBoolean("active") ?: true
                                        )
                                        database.productVariantDao().insertVariant(entity)
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
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            scope.launch {
                                try {
                                    for (doc in snapshot.documents) {
                                        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
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
                                            isDeleted = doc.getBoolean("isDeleted") ?: false
                                        )
                                        database.stockMovementDao().insertMovement(entity)
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
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            scope.launch {
                                try {
                                    for (doc in snapshot.documents) {
                                        val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
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
            if (hasRemoteProducts) {
                for (doc in prodDocs.documents) {
                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
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
                        active = doc.getBoolean("active") ?: true
                    )
                    database.productDao().insertProduct(entity)
                }
            }

            // 2. Pull Variants
            val varDocs = firestore.collection("variants").get().await()
            for (doc in varDocs.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
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
                    active = doc.getBoolean("active") ?: true
                )
                database.productVariantDao().insertVariant(entity)
            }

            // 3. Pull Movements
            val moveDocs = firestore.collection("movements").get().await()
            for (doc in moveDocs.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
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
                    isDeleted = doc.getBoolean("isDeleted") ?: false
                )
                database.stockMovementDao().insertMovement(entity)
            }

            // 4. Pull Destinations
            val destDocs = firestore.collection("destinations").get().await()
            for (doc in destDocs.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
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

            _lastSyncTimestamp.value = System.currentTimeMillis()
            hasRemoteProducts
        } catch (e: Exception) {
            _syncError.value = e.localizedMessage
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
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Push product failed", e)
        }
    }

    suspend fun deleteProduct(productId: Long) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            firestore.collection("products").document(productId.toString()).update("active", false, "updatedAt", System.currentTimeMillis()).await()
        } catch (e: Exception) {
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
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Push variant failed", e)
        }
    }

    suspend fun deleteVariant(variantId: Long) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            firestore.collection("variants").document(variantId.toString()).update("active", false, "updatedAt", System.currentTimeMillis()).await()
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
        } catch (e: Exception) {
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
            Log.d("FirebaseSync", "All cloud data cleared successfully")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "clearAllCloudData error", e)
        }
    }

    suspend fun fullBidirectionalSync(): Boolean = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncError.value = null
        try {
            ensureAuth()
            // 1. Pull latest changes from Firestore
            pullAllFromCloud()
            // 2. Push any local changes to Firestore
            pushAllToCloud()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            _syncError.value = e.localizedMessage
            Log.e("FirebaseSync", "fullBidirectionalSync error", e)
            false
        } finally {
            _isSyncing.value = false
        }
    }
}

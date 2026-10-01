package com.example.data.sync

import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.entity.AuditLogEntity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirebaseSyncManager(private val database: AppDatabase) {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val scope = CoroutineScope(Dispatchers.IO)

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

    // Sign in anonymously or with credentials to Firebase Auth if enabled/needed
    fun ensureAuth(onComplete: (Boolean) -> Unit = {}) {
        if (isAuthDisabled) {
            onComplete(true)
            return
        }

        try {
            if (auth.currentUser != null) {
                onComplete(true)
                return
            }

            if (authAttempted) {
                onComplete(true)
                return
            }

            authAttempted = true
            auth.signInAnonymously()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FirebaseSync", "Firebase Auth successful: ${auth.currentUser?.uid}")
                    } else {
                        val exc = task.exception
                        val msg = exc?.message ?: ""
                        if (msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
                            Log.w("FirebaseSync", "Firebase Auth is not enabled in Firebase Console (CONFIGURATION_NOT_FOUND). Proceeding with direct Firestore sync.")
                            isAuthDisabled = true
                        } else {
                            Log.w("FirebaseSync", "Firebase Auth warning: ${exc?.localizedMessage}")
                        }
                    }
                    onComplete(true)
                }
        } catch (e: Exception) {
            Log.w("FirebaseSync", "Firebase Auth not initialized or unconfigured: ${e.localizedMessage}")
            isAuthDisabled = true
            onComplete(true)
        }
    }

    // Start Realtime Firestore Listeners to receive changes from other devices
    fun startRealtimeListeners() {
        try {
            ensureAuth { success ->
                if (!success) return@ensureAuth
                stopListeners()

                // Listen to Products
                val prodListener = firestore.collection("products")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirebaseSync", "Products listen error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            scope.launch {
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
                            }
                        }
                    }
                listeners.add(prodListener)

                // Listen to Stock Movements
                val moveListener = firestore.collection("movements")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirebaseSync", "Movements listen error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            scope.launch {
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
                            }
                        }
                    }
                listeners.add(moveListener)

                // Listen to Destinations
                val destListener = firestore.collection("destinations")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirebaseSync", "Destinations listen error: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null && !snapshot.isEmpty) {
                            scope.launch {
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
                            }
                        }
                    }
                listeners.add(destListener)
            }
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Failed to start Firebase listeners", e)
        }
    }

    fun stopListeners() {
        for (l in listeners) {
            l.remove()
        }
        listeners.clear()
    }

    // Push local data to Firestore
    suspend fun pushProduct(product: ProductEntity) {
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

    suspend fun pushMovement(movement: StockMovementEntity) {
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

    suspend fun pushDestination(destination: DestinationEntity) {
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

    suspend fun syncUserProfile(profile: UserProfile) {
        try {
            ensureAuth()
            val data = hashMapOf(
                "id" to profile.id,
                "fullName" to profile.fullName,
                "role" to profile.role,
                "email" to profile.email,
                "phone" to profile.phone,
                "lastActive" to System.currentTimeMillis()
            )
            firestore.collection("users").document(profile.id).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Sync user profile failed", e)
        }
    }

    suspend fun fullSyncAll() {
        _isSyncing.value = true
        _syncError.value = null
        try {
            ensureAuth()
            val products = database.productDao().getActiveProductsList()
            for (p in products) {
                pushProduct(p)
            }
            val movements = database.stockMovementDao().getAllActiveMovementsList()
            for (m in movements) {
                pushMovement(m)
            }
            val destinations = database.destinationDao().getAllDestinationsList()
            for (d in destinations) {
                pushDestination(d)
            }
            _lastSyncTimestamp.value = System.currentTimeMillis()
        } catch (e: Exception) {
            _syncError.value = e.message
            Log.e("FirebaseSync", "Full sync error", e)
        } finally {
            _isSyncing.value = false
        }
    }
}

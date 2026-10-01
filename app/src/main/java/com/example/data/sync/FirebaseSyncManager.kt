package com.example.data.sync

import android.util.Log
import com.example.data.db.AppDatabase
import com.example.data.entity.DestinationEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ProductVariantEntity
import com.example.data.entity.StockMovementEntity
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
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
import java.util.concurrent.CopyOnWriteArrayList

class FirebaseSyncManager(private val database: AppDatabase) {

    private val firestore: FirebaseFirestore by lazy {
        val db = FirebaseFirestore.getInstance()
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            db.firestoreSettings = settings
            Log.d("FirebaseSync", "SYNC: Firestore initialized with offline persistence enabled")
        } catch (e: Exception) {
            Log.d("FirebaseSync", "SYNC: Firestore settings note: ${e.message}")
        }
        db
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val coroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e("FirebaseSync", "SYNC: Unhandled sync coroutine error caught safely", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler)

    private val listeners = CopyOnWriteArrayList<ListenerRegistration>()

    @Volatile
    private var areListenersActive = false

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp: StateFlow<Long?> = _lastSyncTimestamp.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    @Volatile
    private var isAuthDisabled = false

    // Ensure user is signed in to Firebase Auth (anonymous or real credential)
    suspend fun ensureAuth(): Boolean = withContext(Dispatchers.IO) {
        if (isAuthDisabled) return@withContext true
        val current = auth.currentUser
        if (current != null) {
            Log.d("FirebaseSync", "SYNC: Authenticated user = ${current.uid}, email = ${current.email ?: "none"}")
            return@withContext true
        }

        try {
            auth.signInAnonymously().await()
            Log.d("FirebaseSync", "SYNC: Firebase anonymous auth successful: UID = ${auth.currentUser?.uid}")
            true
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            if (msg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
                msg.contains("ADMIN_ONLY_OPERATION", ignoreCase = true)
            ) {
                Log.w("FirebaseSync", "SYNC: Anonymous Auth not enabled in console, using direct Firestore sync: $msg")
                isAuthDisabled = true
            } else {
                Log.w("FirebaseSync", "SYNC: Firebase Auth warning: $msg")
            }
            true
        }
    }

    // Register or sign in user with email & password in Firebase Auth + Firestore
    suspend fun registerOrAuthUser(profile: UserProfile, password: String = ""): Boolean = withContext(Dispatchers.IO) {
        try {
            if (profile.email.isNotBlank() && password.length >= 6) {
                try {
                    auth.createUserWithEmailAndPassword(profile.email.trim(), password).await()
                    Log.d("FirebaseSync", "SYNC: User registered in Firebase Auth: ${auth.currentUser?.uid}")
                } catch (e: Exception) {
                    try {
                        auth.signInWithEmailAndPassword(profile.email.trim(), password).await()
                        Log.d("FirebaseSync", "SYNC: User signed in to Firebase Auth: ${auth.currentUser?.uid}")
                    } catch (e2: Exception) {
                        Log.w("FirebaseSync", "SYNC: Auth fallback: ${e2.localizedMessage}")
                        ensureAuth()
                    }
                }
            } else {
                ensureAuth()
            }

            syncUserProfile(profile)
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: registerOrAuthUser error", e)
            true
        }
    }

    // Start Realtime Firestore Listeners - thread-safe, keeps listeners alive continuously
    @Synchronized
    fun startRealtimeListeners() {
        if (areListenersActive) {
            Log.d("FirebaseSync", "SYNC: Realtime listeners are already active. Skipping redundant start.")
            return
        }
        areListenersActive = true
        Log.d("FirebaseSync", "SYNC: Starting real-time Firestore listeners for all collections")

        scope.launch {
            ensureAuth()

            // 1. Listen to Products Collection
            try {
                Log.d("FirebaseSync", "SYNC: Attaching products snapshot listener")
                val prodListener = firestore.collection("products")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            handleListenerError("products", error)
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener

                        val isFromCache = snapshot.metadata.isFromCache
                        Log.d("FirebaseSync", "SYNC: Products snapshot received. count=${snapshot.size()}, fromCache=$isFromCache")

                        scope.launch {
                            try {
                                for (change in snapshot.documentChanges) {
                                    val doc = change.document
                                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue

                                    when (change.type) {
                                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                            val entity = ProductEntity(
                                                id = id,
                                                nameArabic = doc.getString("nameArabic") ?: "",
                                                nameFrench = doc.getString("nameFrench") ?: "",
                                                category = doc.getString("category") ?: "مصحف شريف",
                                                language = doc.getString("language") ?: "العربية",
                                                formatType = doc.getString("formatType") ?: "عادي",
                                                unit = doc.getString("unit") ?: "نسخة",
                                                packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 10,
                                                minimumStock = doc.getLong("minimumStock")?.toInt() ?: 50,
                                                initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                                                notes = doc.getString("notes") ?: "",
                                                active = doc.getBoolean("active") ?: true,
                                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                            )
                                            database.productDao().insertProduct(entity)
                                            Log.d("FirebaseSync", "SYNC: Product ${change.type} id=$id name=${entity.nameArabic} initialStock=${entity.initialStock}")
                                        }
                                        DocumentChange.Type.REMOVED -> {
                                            database.productDao().deleteProductById(id)
                                            Log.d("FirebaseSync", "SYNC: Product REMOVED id=$id from Room")
                                        }
                                    }
                                }
                                _lastSyncTimestamp.value = System.currentTimeMillis()
                            } catch (e: Exception) {
                                Log.e("FirebaseSync", "SYNC: Error updating products in Room", e)
                            }
                        }
                    }
                listeners.add(prodListener)
            } catch (e: Exception) {
                Log.e("FirebaseSync", "SYNC: Failed to attach products listener", e)
            }

            // 2. Listen to Product Variants Collection
            try {
                Log.d("FirebaseSync", "SYNC: Attaching variants snapshot listener")
                val variantListener = firestore.collection("variants")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            handleListenerError("variants", error)
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener

                        val isFromCache = snapshot.metadata.isFromCache
                        Log.d("FirebaseSync", "SYNC: Variants snapshot received. count=${snapshot.size()}, fromCache=$isFromCache")

                        scope.launch {
                            try {
                                for (change in snapshot.documentChanges) {
                                    val doc = change.document
                                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue

                                    when (change.type) {
                                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
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
                                            Log.d("FirebaseSync", "SYNC: Variant ${change.type} id=$id name=${entity.nameArabic}")
                                        }
                                        DocumentChange.Type.REMOVED -> {
                                            database.productVariantDao().deleteVariantById(id)
                                            Log.d("FirebaseSync", "SYNC: Variant REMOVED id=$id from Room")
                                        }
                                    }
                                }
                                _lastSyncTimestamp.value = System.currentTimeMillis()
                            } catch (e: Exception) {
                                Log.e("FirebaseSync", "SYNC: Error updating variants in Room", e)
                            }
                        }
                    }
                listeners.add(variantListener)
            } catch (e: Exception) {
                Log.e("FirebaseSync", "SYNC: Failed to attach variants listener", e)
            }

            // 3. Listen to Stock Movements Collection (SOURCE OF TRUTH FOR REAL-TIME INVENTORY)
            try {
                Log.d("FirebaseSync", "SYNC: Attaching movements snapshot listener")
                val moveListener = firestore.collection("movements")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            handleListenerError("movements", error)
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener

                        val isFromCache = snapshot.metadata.isFromCache
                        Log.d("FirebaseSync", "SYNC: Movements snapshot received. count=${snapshot.size()}, fromCache=$isFromCache")

                        scope.launch {
                            try {
                                for (change in snapshot.documentChanges) {
                                    val doc = change.document
                                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue

                                    when (change.type) {
                                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
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
                                                isDeleted = doc.getBoolean("isDeleted") ?: false,
                                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                            )
                                            database.stockMovementDao().insertMovement(entity)
                                            Log.d("FirebaseSync", "SYNC: Movement ${change.type} id=$id type=${entity.movementType} qty=${entity.quantity} prodId=${entity.productId}")
                                        }
                                        DocumentChange.Type.REMOVED -> {
                                            database.stockMovementDao().deleteMovementById(id)
                                            Log.d("FirebaseSync", "SYNC: Movement REMOVED id=$id from Room")
                                        }
                                    }
                                }
                                _lastSyncTimestamp.value = System.currentTimeMillis()
                            } catch (e: Exception) {
                                Log.e("FirebaseSync", "SYNC: Error updating movements in Room", e)
                            }
                        }
                    }
                listeners.add(moveListener)
            } catch (e: Exception) {
                Log.e("FirebaseSync", "SYNC: Failed to attach movements listener", e)
            }

            // 4. Listen to Destinations Collection
            try {
                Log.d("FirebaseSync", "SYNC: Attaching destinations snapshot listener")
                val destListener = firestore.collection("destinations")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            handleListenerError("destinations", error)
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener

                        val isFromCache = snapshot.metadata.isFromCache
                        Log.d("FirebaseSync", "SYNC: Destinations snapshot received. count=${snapshot.size()}, fromCache=$isFromCache")

                        scope.launch {
                            try {
                                for (change in snapshot.documentChanges) {
                                    val doc = change.document
                                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue

                                    when (change.type) {
                                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
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
                                            Log.d("FirebaseSync", "SYNC: Destination ${change.type} id=$id name=${entity.name}")
                                        }
                                        DocumentChange.Type.REMOVED -> {
                                            database.destinationDao().deleteDestinationById(id)
                                            Log.d("FirebaseSync", "SYNC: Destination REMOVED id=$id from Room")
                                        }
                                    }
                                }
                                _lastSyncTimestamp.value = System.currentTimeMillis()
                            } catch (e: Exception) {
                                Log.e("FirebaseSync", "SYNC: Error updating destinations in Room", e)
                            }
                        }
                    }
                listeners.add(destListener)
            } catch (e: Exception) {
                Log.e("FirebaseSync", "SYNC: Failed to attach destinations listener", e)
            }
        }
    }

    private fun handleListenerError(collection: String, error: FirebaseFirestoreException) {
        val msg = "SYNC: Listener ERROR on [$collection] code=${error.code}: ${error.message}"
        Log.e("FirebaseSync", msg)
        _syncError.value = "خطأ في مزامنة $collection: ${error.code}"
        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            Log.e("FirebaseSync", "SYNC: PERMISSION_DENIED! Ensure Firestore security rules allow read/write for authenticated users.")
        }
    }

    @Synchronized
    fun stopListeners() {
        for (l in listeners) {
            try {
                l.remove()
            } catch (e: Exception) {
                Log.e("FirebaseSync", "SYNC: Error removing listener", e)
            }
        }
        listeners.clear()
        areListenersActive = false
        Log.d("FirebaseSync", "SYNC: Stopped all realtime listeners")
    }

    // Pull ALL collections from Firestore into local Room database
    // Returns true if remote catalog (products) was found in Firestore
    suspend fun pullAllFromCloud(): Boolean = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncError.value = null
        try {
            ensureAuth()
            Log.d("FirebaseSync", "SYNC: Initiating full pull from Cloud Firestore")

            // 1. Pull Products
            val prodDocs = firestore.collection("products").get().await()
            val hasRemoteProducts = !prodDocs.isEmpty
            Log.d("FirebaseSync", "SYNC: Pulled ${prodDocs.size()} products from cloud")
            if (hasRemoteProducts) {
                for (doc in prodDocs.documents) {
                    val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                    val entity = ProductEntity(
                        id = id,
                        nameArabic = doc.getString("nameArabic") ?: "",
                        nameFrench = doc.getString("nameFrench") ?: "",
                        category = doc.getString("category") ?: "مصحف شريف",
                        language = doc.getString("language") ?: "العربية",
                        formatType = doc.getString("formatType") ?: "عادي",
                        unit = doc.getString("unit") ?: "نسخة",
                        packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 10,
                        minimumStock = doc.getLong("minimumStock")?.toInt() ?: 50,
                        initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                        notes = doc.getString("notes") ?: "",
                        active = doc.getBoolean("active") ?: true,
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                    )
                    database.productDao().insertProduct(entity)
                }
            }

            // 2. Pull Variants
            val varDocs = firestore.collection("variants").get().await()
            Log.d("FirebaseSync", "SYNC: Pulled ${varDocs.size()} variants from cloud")
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
            Log.d("FirebaseSync", "SYNC: Pulled ${moveDocs.size()} movements from cloud")
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
                    isDeleted = doc.getBoolean("isDeleted") ?: false,
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
                database.stockMovementDao().insertMovement(entity)
            }

            // 4. Pull Destinations
            val destDocs = firestore.collection("destinations").get().await()
            Log.d("FirebaseSync", "SYNC: Pulled ${destDocs.size()} destinations from cloud")
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
            Log.e("FirebaseSync", "SYNC: pullAllFromCloud error", e)
            false
        } finally {
            _isSyncing.value = false
        }
    }

    // Push product to Firestore with detailed status logging
    suspend fun pushProduct(product: ProductEntity): Boolean = withContext(Dispatchers.IO) {
        val docId = product.id.toString()
        Log.d("FirebaseSync", "SYNC: Product write started id=$docId name=${product.nameArabic}")
        try {
            ensureAuth()
            val data = hashMapOf(
                "id" to product.id,
                "nameArabic" to product.nameArabic,
                "nameFrench" to product.nameFrench,
                "category" to product.category,
                "language" to product.language,
                "formatType" to product.formatType,
                "unit" to product.unit,
                "packageQuantity" to product.packageQuantity,
                "minimumStock" to product.minimumStock,
                "initialStock" to product.initialStock,
                "notes" to product.notes,
                "active" to product.active,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("products").document(docId).set(data, SetOptions.merge()).await()
            Log.d("FirebaseSync", "SYNC: Product write SUCCESS id=$docId")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: Product write FAILED id=$docId: ${e.message}")
            _syncError.value = "فشل مزامنة الصنف: ${e.localizedMessage}"
            false
        }
    }

    suspend fun deleteProduct(productId: Long): Boolean = withContext(Dispatchers.IO) {
        val docId = productId.toString()
        Log.d("FirebaseSync", "SYNC: Product delete started id=$docId")
        try {
            ensureAuth()
            firestore.collection("products").document(docId).update("active", false, "updatedAt", System.currentTimeMillis()).await()
            Log.d("FirebaseSync", "SYNC: Product delete SUCCESS id=$docId")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: Product delete FAILED id=$docId: ${e.message}")
            false
        }
    }

    suspend fun pushVariant(variant: ProductVariantEntity): Boolean = withContext(Dispatchers.IO) {
        val docId = variant.id.toString()
        Log.d("FirebaseSync", "SYNC: Variant write started id=$docId")
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
            firestore.collection("variants").document(docId).set(data, SetOptions.merge()).await()
            Log.d("FirebaseSync", "SYNC: Variant write SUCCESS id=$docId")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: Variant write FAILED id=$docId: ${e.message}")
            false
        }
    }

    suspend fun deleteVariant(variantId: Long): Boolean = withContext(Dispatchers.IO) {
        val docId = variantId.toString()
        try {
            ensureAuth()
            firestore.collection("variants").document(docId).update("active", false, "updatedAt", System.currentTimeMillis()).await()
            Log.d("FirebaseSync", "SYNC: Variant delete SUCCESS id=$docId")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: Variant delete FAILED id=$docId: ${e.message}")
            false
        }
    }

    // Push movement to Firestore (CRITICAL SOURCE OF TRUTH FOR REAL-TIME INVENTORY)
    suspend fun pushMovement(movement: StockMovementEntity): Boolean = withContext(Dispatchers.IO) {
        val docId = movement.id.toString()
        Log.d("FirebaseSync", "SYNC: Movement write started id=$docId type=${movement.movementType} qty=${movement.quantity} prodId=${movement.productId}")
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
            firestore.collection("movements").document(docId).set(data, SetOptions.merge()).await()
            Log.d("FirebaseSync", "SYNC: Movement write SUCCESS id=$docId")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: Movement write FAILED id=$docId: ${e.message}")
            _syncError.value = "فشل مزامنة الحركة: ${e.localizedMessage}"
            false
        }
    }

    suspend fun pushDestination(destination: DestinationEntity): Boolean = withContext(Dispatchers.IO) {
        val docId = destination.id.toString()
        Log.d("FirebaseSync", "SYNC: Destination write started id=$docId name=${destination.name}")
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
            firestore.collection("destinations").document(docId).set(data, SetOptions.merge()).await()
            Log.d("FirebaseSync", "SYNC: Destination write SUCCESS id=$docId")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: Destination write FAILED id=$docId: ${e.message}")
            false
        }
    }

    suspend fun deleteDestination(destinationId: Long): Boolean = withContext(Dispatchers.IO) {
        val docId = destinationId.toString()
        Log.d("FirebaseSync", "SYNC: Destination delete started id=$docId")
        try {
            ensureAuth()
            firestore.collection("destinations").document(docId).delete().await()
            Log.d("FirebaseSync", "SYNC: Destination delete SUCCESS id=$docId")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: Destination delete FAILED id=$docId: ${e.message}")
            false
        }
    }

    suspend fun syncUserProfile(profile: UserProfile): Boolean = withContext(Dispatchers.IO) {
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
            Log.d("FirebaseSync", "SYNC: User profile synced for $docId")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: Sync user profile failed", e)
            false
        }
    }

    suspend fun pushAllToCloud(): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            Log.d("FirebaseSync", "SYNC: Pushing all local records to Cloud Firestore")
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
            Log.d("FirebaseSync", "SYNC: pushAllToCloud completed successfully")
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "SYNC: pushAllToCloud error", e)
            false
        }
    }

    suspend fun fullBidirectionalSync(): Boolean = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        _syncError.value = null
        try {
            ensureAuth()
            Log.d("FirebaseSync", "SYNC: Starting full bidirectional sync")
            // 1. Pull latest changes from Firestore
            pullAllFromCloud()
            // 2. Push any local changes to Firestore
            pushAllToCloud()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            Log.d("FirebaseSync", "SYNC: Full bidirectional sync completed successfully")
            true
        } catch (e: Exception) {
            _syncError.value = e.localizedMessage
            Log.e("FirebaseSync", "SYNC: fullBidirectionalSync error", e)
            false
        } finally {
            _isSyncing.value = false
        }
    }
}

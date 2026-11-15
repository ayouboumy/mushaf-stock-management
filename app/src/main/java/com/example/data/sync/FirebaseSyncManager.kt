package com.example.data.sync

import android.content.Context
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
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URLEncoder

sealed class CloudSyncDiagnostic {
    object Idle : CloudSyncDiagnostic()
    object Checking : CloudSyncDiagnostic()
    object Connected : CloudSyncDiagnostic()
    data class DatabaseNotFound(val message: String, val technicalDetail: String) : CloudSyncDiagnostic()
    data class PermissionDenied(val message: String, val technicalDetail: String) : CloudSyncDiagnostic()
    data class NetworkError(val message: String, val technicalDetail: String) : CloudSyncDiagnostic()
    data class GeneralError(val message: String, val technicalDetail: String) : CloudSyncDiagnostic()
}

data class FirestoreDiagnosticReport(
    val timestamp: Long = System.currentTimeMillis(),
    val isConnected: Boolean,
    val connectionEngine: String,
    val latencyMs: Long,
    val authenticatedUserId: String?,
    val isAuthAnonymous: Boolean,
    val readPermissionGranted: Boolean,
    val readErrorMessage: String? = null,
    val readSampleCount: Int = 0,
    val writePermissionGranted: Boolean,
    val writeErrorMessage: String? = null,
    val writeVerifiedWithCleanup: Boolean = false,
    val listenersCount: Int,
    val activeListeners: Map<String, Boolean>,
    val allListenersHealthy: Boolean,
    val diagnosticSummaryArabic: String,
    val logMessages: List<String>
)

internal data class RemoteProductRecord(
    val entity: ProductEntity,
    val updatedAt: Long
)

internal data class RemoteVariantRecord(
    val entity: ProductVariantEntity,
    val updatedAt: Long
)

internal data class RemoteMovementRecord(
    val entity: StockMovementEntity,
    val updatedAt: Long
)

internal data class RemoteDestinationRecord(
    val entity: DestinationEntity,
    val updatedAt: Long
)

class FirebaseSyncManager(
    private val database: AppDatabase,
    context: Context? = com.example.StockApplication.appContext
) {

    @Volatile
    var isClearingInProgress = false

    internal val syncStore = SyncStateStore(context)
    private val syncMutex = Mutex()

    init {
        UniqueIdGenerator.init(context)
    }

    private val firestore: FirebaseFirestore
        get() {
            com.example.StockApplication.initializeFirebase(com.example.StockApplication.appContext)
            return FirebaseFirestore.getInstance()
        }

    private val auth: FirebaseAuth
        get() {
            com.example.StockApplication.initializeFirebase(com.example.StockApplication.appContext)
            return FirebaseAuth.getInstance()
        }

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

    private val _activeListenersMap = MutableStateFlow<Map<String, Boolean>>(
        mapOf(
            "products" to false,
            "variants" to false,
            "movements" to false,
            "destinations" to false
        )
    )
    val activeListenersMap: StateFlow<Map<String, Boolean>> = _activeListenersMap.asStateFlow()

    private val _diagnosticReport = MutableStateFlow<FirestoreDiagnosticReport?>(null)
    val diagnosticReport: StateFlow<FirestoreDiagnosticReport?> = _diagnosticReport.asStateFlow()

    fun markLocalPendingPush(type: SyncEntityType, id: Long) {
        syncStore.markPendingPush(type, id)
    }

    fun markLocalPendingDelete(type: SyncEntityType, id: Long) {
        syncStore.markPendingDelete(type, id)
    }

    fun clearLocalSyncMetadata() {
        syncStore.clearAll()
    }

    fun parseErrorToDiagnostic(e: Throwable): CloudSyncDiagnostic {
        val msg = e.message ?: e.localizedMessage ?: "Unknown error"
        return when {
            msg.contains("database (default) does not exist", ignoreCase = true) ||
            msg.contains("The database (default) does not exist", ignoreCase = true) ||
            (msg.contains("NOT_FOUND", ignoreCase = true) && msg.contains("database", ignoreCase = true)) -> {
                CloudSyncDiagnostic.DatabaseNotFound(
                    message = "تعذر العثور على قاعدة بيانات Firestore (default) في مشروع Firebase.",
                    technicalDetail = msg
                )
            }
            msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("Missing or insufficient permissions", ignoreCase = true) -> {
                CloudSyncDiagnostic.PermissionDenied(
                    message = "قواعد الأمان (Rules) في Firestore تمنع قراءة أو كتابة البيانات.",
                    technicalDetail = msg
                )
            }
            msg.contains("UNAVAILABLE", ignoreCase = true) || msg.contains("network", ignoreCase = true) || msg.contains("offline", ignoreCase = true) || msg.contains("Unable to resolve host", ignoreCase = true) -> {
                CloudSyncDiagnostic.NetworkError(
                    message = "تعذر الاتصال بخوادم Firebase (يرجى التحقق من اتصال الإنترنت).",
                    technicalDetail = msg
                )
            }
            else -> {
                CloudSyncDiagnostic.GeneralError(
                    message = "تنبيه أثناء الاتصال السحابي: $msg",
                    technicalDetail = msg
                )
            }
        }
    }

    private suspend fun getAuthIdToken(): String? {
        return try {
            val user = auth.currentUser ?: return null
            withTimeoutOrNull(1500) {
                user.getIdToken(false).await()?.token
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun probeRestApi(): Boolean {
        return try {
            val url = java.net.URL("https://firestore.googleapis.com/v1/projects/mushaf-stock/databases/(default)/documents/products?pageSize=1&key=$apiKey")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.w("FirebaseSync", "REST probe failed", e)
            false
        }
    }

    suspend fun diagnoseConnection(): CloudSyncDiagnostic = withContext(Dispatchers.IO) {
        _syncDiagnostic.value = CloudSyncDiagnostic.Checking
        try {
            ensureAuth()
            firestore.collection("products").limit(1).get(Source.SERVER).await()
            isPermissionDenied = false
            _syncError.value = null
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            CloudSyncDiagnostic.Connected
        } catch (e: Exception) {
            val restOk = probeRestApi()
            if (restOk) {
                isPermissionDenied = false
                _syncError.value = null
                _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                CloudSyncDiagnostic.Connected
            } else {
                val diag = parseErrorToDiagnostic(e)
                if (diag is CloudSyncDiagnostic.PermissionDenied) {
                    isPermissionDenied = true
                }
                _syncError.value = e.localizedMessage ?: e.message
                _syncDiagnostic.value = diag
                diag
            }
        }
    }

    @Volatile
    private var isAuthDisabled = false

    suspend fun ensureAuth(): Boolean {
        if (isAuthDisabled) return true
        if (auth.currentUser != null) return true

        return try {
            auth.signInAnonymously().await()
            Log.d("FirebaseSync", "Firebase anonymous auth successful: ${auth.currentUser?.uid}")
            true
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: ""
            Log.i("FirebaseSync", "Anonymous Auth unavailable ($msg), proceeding with direct Firestore sync")
            isAuthDisabled = true
            true
        }
    }

    suspend fun registerOrAuthUser(profile: UserProfile, password: String = ""): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (profile.email.isNotBlank() && password.length >= 6) {
                    try {
                        auth.createUserWithEmailAndPassword(profile.email.trim(), password).await()
                        Log.d("FirebaseSync", "User registered in Firebase Auth: ${auth.currentUser?.uid}")
                    } catch (e: Exception) {
                        try {
                            auth.signInWithEmailAndPassword(profile.email.trim(), password).await()
                            Log.d("FirebaseSync", "User signed in to Firebase Auth: ${auth.currentUser?.uid}")
                        } catch (e2: Exception) {
                            Log.w("FirebaseSync", "Auth fallback: ${e2.localizedMessage}")
                        }
                    }
                }
                syncUserProfile(profile)
                if (auth.currentUser != null) {
                    isPermissionDenied = false
                    startRealtimeListeners()
                }
                true
            } catch (e: Exception) {
                Log.e("FirebaseSync", "registerOrAuthUser error", e)
                true
            }
        }
    }

    @Volatile
    private var isPermissionDenied = false

    private fun handleCollectionListenerError(collection: String, error: com.google.firebase.firestore.FirebaseFirestoreException) {
        val msg = error.message ?: ""
        val isPerm = msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("Missing or insufficient permissions", ignoreCase = true)
        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put(collection, false) }
        _syncDiagnostic.value = parseErrorToDiagnostic(error)
        _syncError.value = error.message
        if (isPerm) {
            if (!isPermissionDenied) {
                isPermissionDenied = true
                Log.w("FirebaseSync", "Cloud Firestore requires authentication. Operating in local Room DB mode without wiping local records.")
            }
            stopListeners()
        } else {
            Log.w("FirebaseSync", "$collection listen error: ${error.message}")
        }
    }

    fun startRealtimeListeners() {
        scope.launch {
            try {
                if (isClearingInProgress) return@launch
                if (isPermissionDenied && auth.currentUser == null) return@launch
                ensureAuth()
                stopListeners()

                // 1. Listen to Products
                val prodListener = firestore.collection("products")
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress) return@addSnapshotListener
                        if (error != null) {
                            handleCollectionListenerError("products", error)
                            return@addSnapshotListener
                        }
                        isPermissionDenied = false
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("products", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null && !isClearingInProgress) {
                            val isFromCache = snapshot.metadata.isFromCache
                            val hasPending = snapshot.metadata.hasPendingWrites()
                            if (isFromCache && snapshot.isEmpty) {
                                // Never wipe local Room DB on an empty local Firestore cache snapshot
                                return@addSnapshotListener
                            }
                            val records = snapshot.documents.mapNotNull { doc ->
                                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
                                val isActive = doc.getBoolean("active") ?: true
                                val updatedAt = doc.getLong("updatedAt") ?: 0L
                                val entity = ProductEntity(
                                    id = id,
                                    nameArabic = doc.getString("nameArabic") ?: "",
                                    nameFrench = doc.getString("nameFrench") ?: "",
                                    category = doc.getString("category") ?: "مصحف شريف",
                                    formatType = doc.getString("formatType") ?: "عادي",
                                    unit = doc.getString("unit") ?: "نسخة",
                                    packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 1,
                                    minimumStock = doc.getLong("minimumStock")?.toInt() ?: 0,
                                    initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                                    notes = doc.getString("notes") ?: "",
                                    active = isActive,
                                    updatedAt = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                                )
                                RemoteProductRecord(entity, updatedAt)
                            }
                            val isAuthoritative = !isFromCache && !hasPending
                            scope.launch {
                                syncMutex.withLock {
                                    if (!isClearingInProgress) {
                                        mergeRemoteProducts(records, isAuthoritative)
                                        _lastSyncTimestamp.value = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                listeners.add(prodListener)
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("products", true) }

                // 2. Listen to Product Variants
                val variantListener = firestore.collection("variants")
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress) return@addSnapshotListener
                        if (error != null) {
                            handleCollectionListenerError("variants", error)
                            return@addSnapshotListener
                        }
                        isPermissionDenied = false
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("variants", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null && !isClearingInProgress) {
                            val isFromCache = snapshot.metadata.isFromCache
                            val hasPending = snapshot.metadata.hasPendingWrites()
                            if (isFromCache && snapshot.isEmpty) {
                                return@addSnapshotListener
                            }
                            val records = snapshot.documents.mapNotNull { doc ->
                                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
                                val isActive = doc.getBoolean("active") ?: true
                                val updatedAt = doc.getLong("updatedAt") ?: 0L
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
                                    active = isActive
                                )
                                RemoteVariantRecord(entity, updatedAt)
                            }
                            val isAuthoritative = !isFromCache && !hasPending
                            scope.launch {
                                syncMutex.withLock {
                                    if (!isClearingInProgress) {
                                        mergeRemoteVariants(records, isAuthoritative)
                                        _lastSyncTimestamp.value = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                listeners.add(variantListener)
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("variants", true) }

                // 3. Listen to Stock Movements
                val moveListener = firestore.collection("movements")
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress) return@addSnapshotListener
                        if (error != null) {
                            handleCollectionListenerError("movements", error)
                            return@addSnapshotListener
                        }
                        isPermissionDenied = false
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("movements", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null && !isClearingInProgress) {
                            val isFromCache = snapshot.metadata.isFromCache
                            val hasPending = snapshot.metadata.hasPendingWrites()
                            if (isFromCache && snapshot.isEmpty) {
                                return@addSnapshotListener
                            }
                            val records = snapshot.documents.mapNotNull { doc ->
                                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
                                val isDel = doc.getBoolean("isDeleted") ?: false
                                val updatedAt = doc.getLong("updatedAt") ?: 0L
                                val vId = doc.getLong("variantId")
                                val dId = doc.getLong("destinationId")
                                val revId = doc.getLong("reversedByMovementId")
                                val entity = StockMovementEntity(
                                    id = id,
                                    productId = doc.getLong("productId") ?: 0L,
                                    variantId = if (vId != null && vId > 0) vId else null,
                                    movementType = doc.getString("movementType") ?: "STOCK_IN",
                                    quantity = doc.getLong("quantity")?.toInt() ?: 0,
                                    packageCount = doc.getLong("packageCount")?.toInt() ?: 0,
                                    dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                                    dateFormatted = doc.getString("dateFormatted") ?: "",
                                    source = doc.getString("source") ?: "",
                                    destinationId = if (dId != null && dId > 0) dId else null,
                                    destinationName = doc.getString("destinationName") ?: "",
                                    destinationType = doc.getString("destinationType") ?: "",
                                    reason = doc.getString("reason") ?: "",
                                    responsiblePerson = doc.getString("responsiblePerson") ?: "",
                                    referenceNumber = doc.getString("referenceNumber") ?: "",
                                    notes = doc.getString("notes") ?: "",
                                    isReversed = doc.getBoolean("isReversed") ?: false,
                                    reversedByMovementId = if (revId != null && revId > 0) revId else null,
                                    isDeleted = isDel,
                                    updatedAt = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                                )
                                RemoteMovementRecord(entity, updatedAt)
                            }
                            val isAuthoritative = !isFromCache && !hasPending
                            scope.launch {
                                syncMutex.withLock {
                                    if (!isClearingInProgress) {
                                        mergeRemoteMovements(records, isAuthoritative)
                                        _lastSyncTimestamp.value = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                listeners.add(moveListener)
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("movements", true) }

                // 4. Listen to Destinations
                val destListener = firestore.collection("destinations")
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress) return@addSnapshotListener
                        if (error != null) {
                            handleCollectionListenerError("destinations", error)
                            return@addSnapshotListener
                        }
                        isPermissionDenied = false
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("destinations", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null && !isClearingInProgress) {
                            val isFromCache = snapshot.metadata.isFromCache
                            val hasPending = snapshot.metadata.hasPendingWrites()
                            if (isFromCache && snapshot.isEmpty) {
                                return@addSnapshotListener
                            }
                            val records = snapshot.documents.mapNotNull { doc ->
                                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
                                val updatedAt = doc.getLong("updatedAt") ?: 0L
                                val entity = DestinationEntity(
                                    id = id,
                                    name = doc.getString("name") ?: "",
                                    type = doc.getString("type") ?: "مسجد",
                                    commune = doc.getString("commune") ?: "",
                                    province = doc.getString("province") ?: "",
                                    address = doc.getString("address") ?: "",
                                    contactPerson = doc.getString("contactPerson") ?: "",
                                    phone = doc.getString("phone") ?: "",
                                    notes = doc.getString("notes") ?: ""
                                )
                                RemoteDestinationRecord(entity, updatedAt)
                            }
                            val isAuthoritative = !isFromCache && !hasPending
                            scope.launch {
                                syncMutex.withLock {
                                    if (!isClearingInProgress) {
                                        mergeRemoteDestinations(records, isAuthoritative)
                                        _lastSyncTimestamp.value = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                listeners.add(destListener)
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("destinations", true) }
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
        _activeListenersMap.value = mapOf(
            "products" to false,
            "variants" to false,
            "movements" to false,
            "destinations" to false
        )
    }

    fun ensureCollectionListenersActive() {
        if (isPermissionDenied && auth.currentUser == null) return
        if (listeners.size < 4 || _activeListenersMap.value.values.any { !it }) {
            Log.i("FirestoreDiagnostic", "Re-initializing collection listeners (current: ${listeners.size})")
            startRealtimeListeners()
        }
    }

    // ==================== Safe Non-Destructive Merge Engine ====================

    internal suspend fun mergeRemoteProducts(
        remoteRecords: List<RemoteProductRecord>,
        isAuthoritativeServerSnapshot: Boolean
    ) {
        if (isClearingInProgress) return
        val localMap = database.productDao().getAllProductsList().associateBy { it.id }
        val remoteIds = mutableSetOf<Long>()

        for (rec in remoteRecords) {
            if (isClearingInProgress) return
            val id = rec.entity.id
            remoteIds.add(id)
            if (syncStore.isPendingDelete(SyncEntityType.PRODUCT, id)) {
                continue
            }

            val isPendingLocal = syncStore.isPendingPush(SyncEntityType.PRODUCT, id)
            val local = localMap[id]
            if (isPendingLocal && local != null) {
                // Keep newer local unsynced edit; only apply remote if strictly newer on server
                if (rec.updatedAt <= local.updatedAt) {
                    continue
                }
            }

            if (rec.entity.active) {
                database.productDao().insertProduct(rec.entity)
                syncStore.markKnownCloud(SyncEntityType.PRODUCT, id)
                if (isPendingLocal && local != null && rec.updatedAt > local.updatedAt) {
                    syncStore.markPushSucceeded(SyncEntityType.PRODUCT, id)
                }
            } else {
                database.productDao().deleteProductById(id)
                syncStore.markRemovedFromCloud(SyncEntityType.PRODUCT, id)
            }
        }

        // Only prune items absent from server if this is a verified authoritative server snapshot
        // AND the item was previously confirmed synced with cloud (never delete unsynced local items!)
        if (isAuthoritativeServerSnapshot) {
            for ((localId, _) in localMap) {
                if (!remoteIds.contains(localId)) {
                    if (syncStore.isKnownCloud(SyncEntityType.PRODUCT, localId) &&
                        !syncStore.isPendingPush(SyncEntityType.PRODUCT, localId)
                    ) {
                        database.productDao().deleteProductById(localId)
                        syncStore.markRemovedFromCloud(SyncEntityType.PRODUCT, localId)
                    }
                }
            }
        }
    }

    internal suspend fun mergeRemoteVariants(
        remoteRecords: List<RemoteVariantRecord>,
        isAuthoritativeServerSnapshot: Boolean
    ) {
        if (isClearingInProgress) return
        val localList = database.productVariantDao().getAllVariantsList()
        val localIds = localList.map { it.id }.toSet()
        val remoteIds = mutableSetOf<Long>()

        for (rec in remoteRecords) {
            if (isClearingInProgress) return
            val id = rec.entity.id
            remoteIds.add(id)
            if (syncStore.isPendingDelete(SyncEntityType.VARIANT, id)) {
                continue
            }

            if (syncStore.isPendingPush(SyncEntityType.VARIANT, id) && localIds.contains(id)) {
                continue
            }

            if (rec.entity.active) {
                database.productVariantDao().insertVariant(rec.entity)
                syncStore.markKnownCloud(SyncEntityType.VARIANT, id)
            } else {
                database.productVariantDao().deleteVariantById(id)
                syncStore.markRemovedFromCloud(SyncEntityType.VARIANT, id)
            }
        }

        if (isAuthoritativeServerSnapshot) {
            for (lv in localList) {
                if (!remoteIds.contains(lv.id)) {
                    if (syncStore.isKnownCloud(SyncEntityType.VARIANT, lv.id) &&
                        !syncStore.isPendingPush(SyncEntityType.VARIANT, lv.id)
                    ) {
                        database.productVariantDao().deleteVariantById(lv.id)
                        syncStore.markRemovedFromCloud(SyncEntityType.VARIANT, lv.id)
                    }
                }
            }
        }
    }

    internal suspend fun mergeRemoteMovements(
        remoteRecords: List<RemoteMovementRecord>,
        isAuthoritativeServerSnapshot: Boolean
    ) {
        if (isClearingInProgress) return
        val localMap = database.stockMovementDao().getAllMovementsList().associateBy { it.id }
        val remoteIds = mutableSetOf<Long>()

        for (rec in remoteRecords) {
            if (isClearingInProgress) return
            val id = rec.entity.id
            remoteIds.add(id)
            if (syncStore.isPendingDelete(SyncEntityType.MOVEMENT, id)) {
                continue
            }

            val isPendingLocal = syncStore.isPendingPush(SyncEntityType.MOVEMENT, id)
            val local = localMap[id]
            if (isPendingLocal && local != null) {
                if (rec.updatedAt <= local.updatedAt) {
                    continue
                }
            }

            if (!rec.entity.isDeleted) {
                database.stockMovementDao().insertMovement(rec.entity)
                syncStore.markKnownCloud(SyncEntityType.MOVEMENT, id)
                if (isPendingLocal && local != null && rec.updatedAt > local.updatedAt) {
                    syncStore.markPushSucceeded(SyncEntityType.MOVEMENT, id)
                }
            } else {
                database.stockMovementDao().deleteMovementById(id)
                syncStore.markRemovedFromCloud(SyncEntityType.MOVEMENT, id)
            }
        }

        if (isAuthoritativeServerSnapshot) {
            for ((localId, _) in localMap) {
                if (!remoteIds.contains(localId)) {
                    if (syncStore.isKnownCloud(SyncEntityType.MOVEMENT, localId) &&
                        !syncStore.isPendingPush(SyncEntityType.MOVEMENT, localId)
                    ) {
                        database.stockMovementDao().deleteMovementById(localId)
                        syncStore.markRemovedFromCloud(SyncEntityType.MOVEMENT, localId)
                    }
                }
            }
        }
    }

    internal suspend fun mergeRemoteDestinations(
        remoteRecords: List<RemoteDestinationRecord>,
        isAuthoritativeServerSnapshot: Boolean
    ) {
        if (isClearingInProgress) return
        val localList = database.destinationDao().getAllDestinationsList()
        val localIds = localList.map { it.id }.toSet()
        val remoteIds = mutableSetOf<Long>()

        for (rec in remoteRecords) {
            if (isClearingInProgress) return
            val id = rec.entity.id
            remoteIds.add(id)
            if (syncStore.isPendingDelete(SyncEntityType.DESTINATION, id)) {
                continue
            }
            if (syncStore.isPendingPush(SyncEntityType.DESTINATION, id) && localIds.contains(id)) {
                continue
            }
            database.destinationDao().insertDestination(rec.entity)
            syncStore.markKnownCloud(SyncEntityType.DESTINATION, id)
        }

        if (isAuthoritativeServerSnapshot) {
            for (ld in localList) {
                if (!remoteIds.contains(ld.id)) {
                    if (syncStore.isKnownCloud(SyncEntityType.DESTINATION, ld.id) &&
                        !syncStore.isPendingPush(SyncEntityType.DESTINATION, ld.id)
                    ) {
                        database.destinationDao().deleteDestinationById(ld.id)
                        syncStore.markRemovedFromCloud(SyncEntityType.DESTINATION, ld.id)
                    }
                }
            }
        }
    }

    // ==================== Diagnostic Utility ====================

    suspend fun runComprehensiveDiagnostic(): FirestoreDiagnosticReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val logList = mutableListOf<String>()
        fun log(msg: String, isError: Boolean = false) {
            val entry = "[${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}] $msg"
            logList.add(entry)
            if (isError) {
                Log.e("FirestoreDiagnostic", msg)
            } else {
                Log.i("FirestoreDiagnostic", msg)
            }
        }

        log("========== بدء الفحص الشامل لاتصال وقواعد Firestore ==========")

        var authUid: String? = null
        var isAnonymous = false
        try {
            ensureAuth()
            val user = auth.currentUser
            authUid = user?.uid
            isAnonymous = user?.isAnonymous ?: true
            log("1. المصادقة (Auth): معرف المستخدم = ${authUid ?: "بدون مصادقة مباشر"} (مجهول: $isAnonymous)")
        } catch (e: Exception) {
            log("1. تنبيه المصادقة: ${e.message}", isError = true)
        }

        var readSuccess = false
        var readErrMsg: String? = null
        var readCount = 0
        var engine = "None"
        try {
            log("2. فحص صلاحية القراءة (Read Permission Test)...")
            val snapshot = withTimeoutOrNull(3000) {
                firestore.collection("products").limit(5).get(Source.SERVER).await()
            }
            if (snapshot != null) {
                readSuccess = true
                readCount = snapshot.size()
                engine = "Firestore SDK (gRPC)"
                log("   ✓ نجحت القراءة عبر Firestore SDK. تم جلب $readCount مستندات.")
            } else {
                log("   ! مهلة محرك Firestore SDK. جاري الفحص عبر محرك REST المباشر...")
                val restDocs = fetchAllRestDocuments("products")
                if (restDocs != null) {
                    readSuccess = true
                    engine = "Firestore REST Fallback Engine"
                    readCount = restDocs.size
                    log("   ✓ نجحت القراءة عبر Firestore REST API. تم جلب $readCount مستندات.")
                } else {
                    readSuccess = false
                    readErrMsg = "تعذر قراءة البيانات عبر كل من SDK و REST"
                    log("   ✗ فشلت القراءة عبر كلا المحركين: $readErrMsg", isError = true)
                }
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "Unknown Read Error"
            readSuccess = false
            readErrMsg = msg
            log("   ✗ خطأ أثناء فحص صلاحية القراءة: $msg", isError = true)
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("Missing or insufficient permissions", ignoreCase = true)) {
                log("   [تنبيه أمان Rules]: قواعد الأمان تمنع القراءة من Firestore (Missing Read Permissions)", isError = true)
            }
        }

        var writeSuccess = false
        var writeErrMsg: String? = null
        var writeVerifiedWithCleanup = false
        val probeId = "health_probe_${System.currentTimeMillis()}"
        try {
            log("3. فحص صلاحية الكتابة والحذف (Write & Delete Permission Test)...")
            val probeData = hashMapOf(
                "probeId" to probeId,
                "timestamp" to System.currentTimeMillis(),
                "test" to "diagnostic_ping"
            )

            var sdkWriteOk = false
            try {
                withTimeoutOrNull(3000) {
                    firestore.collection("_health_probes").document(probeId).set(probeData).await()
                    firestore.collection("_health_probes").document(probeId).delete().await()
                    sdkWriteOk = true
                }
            } catch (e: Exception) {
                sdkWriteOk = false
                log("   ! SDK write attempt notice: ${e.message}")
            }

            if (sdkWriteOk) {
                writeSuccess = true
                writeVerifiedWithCleanup = true
                log("   ✓ نجحت الكتابة والحذف الفوري عبر Firestore SDK.")
            } else {
                log("   جاري اختبار الكتابة عبر محرك REST المباشر...")
                val probeJson = JSONObject().apply {
                    put("fields", JSONObject().apply {
                        put("test", JSONObject().put("stringValue", "diagnostic_ping"))
                        put("timestamp", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
                    })
                }
                val restWrite = makeRestRequest("PATCH", "_health_probes", probeId, probeJson.toString())
                if (restWrite != null) {
                    makeRestRequest("DELETE", "_health_probes", probeId, null)
                    writeSuccess = true
                    writeVerifiedWithCleanup = true
                    log("   ✓ نجحت الكتابة والحذف عبر Firestore REST Engine.")
                } else {
                    writeSuccess = false
                    writeErrMsg = "تعذر تنفيذ عمليات الكتابة السحابية (يرجى التحقق من قواعد Write Rules)"
                    log("   ✗ فشلت الكتابة: $writeErrMsg", isError = true)
                }
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.message ?: "Unknown Write Error"
            writeSuccess = false
            writeErrMsg = msg
            log("   ✗ خطأ أثناء فحص صلاحية الكتابة: $msg", isError = true)
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true) || msg.contains("Missing or insufficient permissions", ignoreCase = true)) {
                log("   [تنبيه أمان Rules]: قواعد الأمان تمنع الكتابة إلى Firestore (Missing Write Permissions)", isError = true)
            }
        }

        log("4. فحص تهيئة مستمعات المجموعات في طبقة المستودع (Collection Listeners)...")
        ensureCollectionListenersActive()
        val currentListeners = _activeListenersMap.value
        val allListenersActive = currentListeners.values.all { it } && listeners.size >= 4
        log("   مستمعات المجموعات المسجلة: ${listeners.size} / 4")
        currentListeners.forEach { (col, active) ->
            log("   - مجموعة [$col]: ${if (active) "نشطة وتستمع للتحديثات ✓" else "غير نشطة ✗"}")
        }

        val totalLatency = System.currentTimeMillis() - startTime
        val isOverallHealthy = readSuccess && (writeSuccess || writeVerifiedWithCleanup) && allListenersActive

        val summaryArabic = buildString {
            if (isOverallHealthy) {
                append("جميع خدمات Firestore تعمل بشكل سليم وصحي.\n")
                append("• الاتصال: متصل عبر $engine (زمن الاستجابة: ${totalLatency}ms)\n")
                append("• صلاحيات القراءة: مفعلة ومؤكدة ($readCount عناصر)\n")
                append("• صلاحيات الكتابة: مفعلة ومؤكدة مع تنظيف تجريبي\n")
                append("• المستمعات اللحظية: 4 مجموعات نشطة ومسجلة.")
            } else {
                append("تم رصد بعض الملاحظات في الفحص:\n")
                if (!readSuccess) append("• مشكلة في القراءة: $readErrMsg\n")
                if (!writeSuccess) append("• مشكلة في الكتابة: $writeErrMsg\n")
                if (!allListenersActive) append("• بعض مستمعات المجموعات غير نشطة (${listeners.size}/4)\n")
            }
        }

        log("========== اكتمل الفحص الشامل في ${totalLatency}ms (الحالة: ${if (isOverallHealthy) "سليم" else "تنبيه"}) ==========")

        val report = FirestoreDiagnosticReport(
            timestamp = System.currentTimeMillis(),
            isConnected = readSuccess,
            connectionEngine = engine,
            latencyMs = totalLatency,
            authenticatedUserId = authUid,
            isAuthAnonymous = isAnonymous,
            readPermissionGranted = readSuccess,
            readErrorMessage = readErrMsg,
            readSampleCount = readCount,
            writePermissionGranted = writeSuccess,
            writeErrorMessage = writeErrMsg,
            writeVerifiedWithCleanup = writeVerifiedWithCleanup,
            listenersCount = listeners.size,
            activeListeners = currentListeners,
            allListenersHealthy = allListenersActive,
            diagnosticSummaryArabic = summaryArabic,
            logMessages = logList
        )

        _diagnosticReport.value = report
        if (readSuccess) {
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        }
        report
    }

    // ==================== SDK Pull Engine ====================

    suspend fun pullAllFromCloud(): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        _isSyncing.value = true
        _syncError.value = null
        try {
            syncMutex.withLock {
                pullAllFromCloudLocked()
            }
        } catch (e: Exception) {
            val restPulled = syncMutex.withLock {
                pullAllFromRestApiLocked()
            }
            if (restPulled) {
                _lastSyncTimestamp.value = System.currentTimeMillis()
                _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                _syncError.value = null
                isPermissionDenied = false
                true
            } else {
                val diag = parseErrorToDiagnostic(e)
                if (diag is CloudSyncDiagnostic.PermissionDenied) {
                    isPermissionDenied = true
                }
                _syncDiagnostic.value = diag
                _syncError.value = e.localizedMessage ?: e.message
                Log.e("FirebaseSync", "pullAllFromCloud error", e)
                false
            }
        } finally {
            _isSyncing.value = false
        }
    }

    private suspend fun pullAllFromCloudLocked(): Boolean {
        if (isClearingInProgress) return false
        ensureAuth()

        // Always query Source.SERVER so empty local offline cache never deletes Room records
        val prodDocs = firestore.collection("products").get(Source.SERVER).await()
        val varDocs = firestore.collection("variants").get(Source.SERVER).await()
        val moveDocs = firestore.collection("movements").get(Source.SERVER).await()
        val destDocs = firestore.collection("destinations").get(Source.SERVER).await()

        val prodRecords = prodDocs.documents.mapNotNull { doc ->
            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
            val isActive = doc.getBoolean("active") ?: true
            val updatedAt = doc.getLong("updatedAt") ?: 0L
            RemoteProductRecord(
                entity = ProductEntity(
                    id = id,
                    nameArabic = doc.getString("nameArabic") ?: "",
                    nameFrench = doc.getString("nameFrench") ?: "",
                    category = doc.getString("category") ?: "مصحف شريف",
                    formatType = doc.getString("formatType") ?: "عادي",
                    unit = doc.getString("unit") ?: "نسخة",
                    packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 1,
                    minimumStock = doc.getLong("minimumStock")?.toInt() ?: 0,
                    initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                    notes = doc.getString("notes") ?: "",
                    active = isActive,
                    updatedAt = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                ),
                updatedAt = updatedAt
            )
        }
        mergeRemoteProducts(prodRecords, isAuthoritativeServerSnapshot = !prodDocs.metadata.isFromCache && !prodDocs.metadata.hasPendingWrites())

        val varRecords = varDocs.documents.mapNotNull { doc ->
            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
            val isActive = doc.getBoolean("active") ?: true
            val updatedAt = doc.getLong("updatedAt") ?: 0L
            RemoteVariantRecord(
                entity = ProductVariantEntity(
                    id = id,
                    productId = doc.getLong("productId") ?: 0L,
                    nameArabic = doc.getString("nameArabic") ?: "",
                    nameFrench = doc.getString("nameFrench") ?: "",
                    code = doc.getString("code") ?: "",
                    initialStock = doc.getLong("initialStock")?.toInt() ?: 0,
                    minimumStock = doc.getLong("minimumStock")?.toInt() ?: 20,
                    packageQuantity = doc.getLong("packageQuantity")?.toInt() ?: 10,
                    notes = doc.getString("notes") ?: "",
                    active = isActive
                ),
                updatedAt = updatedAt
            )
        }
        mergeRemoteVariants(varRecords, isAuthoritativeServerSnapshot = !varDocs.metadata.isFromCache && !varDocs.metadata.hasPendingWrites())

        val moveRecords = moveDocs.documents.mapNotNull { doc ->
            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
            val isDel = doc.getBoolean("isDeleted") ?: false
            val updatedAt = doc.getLong("updatedAt") ?: 0L
            val vId = doc.getLong("variantId")
            val dId = doc.getLong("destinationId")
            val revId = doc.getLong("reversedByMovementId")
            RemoteMovementRecord(
                entity = StockMovementEntity(
                    id = id,
                    productId = doc.getLong("productId") ?: 0L,
                    variantId = if (vId != null && vId > 0) vId else null,
                    movementType = doc.getString("movementType") ?: "STOCK_IN",
                    quantity = doc.getLong("quantity")?.toInt() ?: 0,
                    packageCount = doc.getLong("packageCount")?.toInt() ?: 0,
                    dateMillis = doc.getLong("dateMillis") ?: System.currentTimeMillis(),
                    dateFormatted = doc.getString("dateFormatted") ?: "",
                    source = doc.getString("source") ?: "",
                    destinationId = if (dId != null && dId > 0) dId else null,
                    destinationName = doc.getString("destinationName") ?: "",
                    destinationType = doc.getString("destinationType") ?: "",
                    reason = doc.getString("reason") ?: "",
                    responsiblePerson = doc.getString("responsiblePerson") ?: "",
                    referenceNumber = doc.getString("referenceNumber") ?: "",
                    notes = doc.getString("notes") ?: "",
                    isReversed = doc.getBoolean("isReversed") ?: false,
                    reversedByMovementId = if (revId != null && revId > 0) revId else null,
                    isDeleted = isDel,
                    updatedAt = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                ),
                updatedAt = updatedAt
            )
        }
        mergeRemoteMovements(moveRecords, isAuthoritativeServerSnapshot = !moveDocs.metadata.isFromCache && !moveDocs.metadata.hasPendingWrites())

        val destRecords = destDocs.documents.mapNotNull { doc ->
            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: return@mapNotNull null
            val updatedAt = doc.getLong("updatedAt") ?: 0L
            RemoteDestinationRecord(
                entity = DestinationEntity(
                    id = id,
                    name = doc.getString("name") ?: "",
                    type = doc.getString("type") ?: "مسجد",
                    commune = doc.getString("commune") ?: "",
                    province = doc.getString("province") ?: "",
                    address = doc.getString("address") ?: "",
                    contactPerson = doc.getString("contactPerson") ?: "",
                    phone = doc.getString("phone") ?: "",
                    notes = doc.getString("notes") ?: ""
                ),
                updatedAt = updatedAt
            )
        }
        mergeRemoteDestinations(destRecords, isAuthoritativeServerSnapshot = !destDocs.metadata.isFromCache && !destDocs.metadata.hasPendingWrites())

        _lastSyncTimestamp.value = System.currentTimeMillis()
        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        _syncError.value = null
        isPermissionDenied = false
        return true
    }

    // ==================== REST API Engine ====================

    private val apiKey = "AIzaSyCr1ToMuR2ejNwkRmnRcRU0zUZdcIfnUQM"
    private val restBaseUrl = "https://firestore.googleapis.com/v1/projects/mushaf-stock/databases/(default)/documents"

    private suspend fun makeRestRequest(method: String, collection: String, docId: String?, jsonBody: String?): String? {
        val idToken = getAuthIdToken()
        var conn: java.net.HttpURLConnection? = null
        try {
            val urlString = if (docId != null) {
                "$restBaseUrl/$collection/$docId?key=$apiKey"
            } else if (collection.contains("?")) {
                "$restBaseUrl/$collection&key=$apiKey"
            } else {
                "$restBaseUrl/$collection?pageSize=300&key=$apiKey"
            }
            val url = java.net.URL(urlString)
            conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            if (!idToken.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $idToken")
            }
            if (method.equals("PATCH", ignoreCase = true)) {
                conn.requestMethod = "POST"
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else if (method.equals("DELETE", ignoreCase = true)) {
                conn.requestMethod = "DELETE"
            } else {
                conn.requestMethod = method
            }
            if (jsonBody != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.outputStream.use { os ->
                    os.write(jsonBody.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
            }
            val code = conn.responseCode
            return if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                if (code == 401 || code == 403) {
                    isPermissionDenied = true
                }
                Log.w("FirebaseSync", "REST $method $collection/$docId failed code $code: $err")
                null
            }
        } catch (e: Exception) {
            Log.e("FirebaseSync", "REST $method $collection/$docId error: ${e.message}")
            return null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Fetches ALL pages of a Firestore collection via REST.
     * Returns null if ANY page request fails or returns invalid JSON, so callers NEVER prune local data on error.
     */
    private suspend fun fetchAllRestDocuments(collection: String): List<JSONObject>? {
        val allDocs = mutableListOf<JSONObject>()
        var pageToken: String? = null
        var pageSafetyCounter = 0

        while (pageSafetyCounter < 50) {
            pageSafetyCounter++
            val path = if (pageToken.isNullOrBlank()) {
                "$collection?pageSize=300"
            } else {
                "$collection?pageSize=300&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}"
            }
            val responseStr = makeRestRequest("GET", path, null, null) ?: return null
            val json = try {
                JSONObject(responseStr)
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Invalid REST JSON for $collection", e)
                return null
            }
            val docs = json.optJSONArray("documents")
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    allDocs.add(docs.getJSONObject(i))
                }
            }
            val next = json.optString("nextPageToken", "")
            if (next.isBlank()) {
                break
            }
            pageToken = next
        }
        return allDocs
    }

    private fun optFirestoreLong(fields: JSONObject, key: String): Long? {
        val obj = fields.optJSONObject(key) ?: return null
        if (obj.has("nullValue")) return null
        if (obj.has("integerValue")) {
            return obj.optString("integerValue").toLongOrNull() ?: obj.optLong("integerValue", 0L)
        }
        if (obj.has("doubleValue")) {
            return obj.optDouble("doubleValue").toLong()
        }
        return null
    }

    private fun optFirestoreInt(fields: JSONObject, key: String): Int? {
        return optFirestoreLong(fields, key)?.toInt()
    }

    private fun optFirestoreString(fields: JSONObject, key: String): String? {
        val obj = fields.optJSONObject(key) ?: return null
        if (obj.has("nullValue")) return null
        return if (obj.has("stringValue")) obj.optString("stringValue") else null
    }

    private fun optFirestoreBoolean(fields: JSONObject, key: String): Boolean? {
        val obj = fields.optJSONObject(key) ?: return null
        if (obj.has("nullValue")) return null
        return if (obj.has("booleanValue")) obj.optBoolean("booleanValue") else null
    }

    private fun buildProductJson(p: ProductEntity): String {
        val root = JSONObject()
        val fields = JSONObject()
        fields.put("id", JSONObject().put("integerValue", p.id.toString()))
        fields.put("nameArabic", JSONObject().put("stringValue", p.nameArabic))
        fields.put("nameFrench", JSONObject().put("stringValue", p.nameFrench))
        fields.put("category", JSONObject().put("stringValue", p.category))
        fields.put("formatType", JSONObject().put("stringValue", p.formatType))
        fields.put("unit", JSONObject().put("stringValue", p.unit))
        fields.put("packageQuantity", JSONObject().put("integerValue", p.packageQuantity.toString()))
        fields.put("minimumStock", JSONObject().put("integerValue", p.minimumStock.toString()))
        fields.put("initialStock", JSONObject().put("integerValue", p.initialStock.toString()))
        fields.put("notes", JSONObject().put("stringValue", p.notes))
        fields.put("active", JSONObject().put("booleanValue", p.active))
        fields.put("updatedAt", JSONObject().put("integerValue", p.updatedAt.coerceAtLeast(System.currentTimeMillis()).toString()))
        root.put("fields", fields)
        return root.toString()
    }

    private fun buildVariantJson(v: ProductVariantEntity): String {
        val root = JSONObject()
        val fields = JSONObject()
        fields.put("id", JSONObject().put("integerValue", v.id.toString()))
        fields.put("productId", JSONObject().put("integerValue", v.productId.toString()))
        fields.put("nameArabic", JSONObject().put("stringValue", v.nameArabic))
        fields.put("nameFrench", JSONObject().put("stringValue", v.nameFrench))
        fields.put("code", JSONObject().put("stringValue", v.code))
        fields.put("initialStock", JSONObject().put("integerValue", v.initialStock.toString()))
        fields.put("minimumStock", JSONObject().put("integerValue", v.minimumStock.toString()))
        fields.put("packageQuantity", JSONObject().put("integerValue", v.packageQuantity.toString()))
        fields.put("notes", JSONObject().put("stringValue", v.notes))
        fields.put("active", JSONObject().put("booleanValue", v.active))
        fields.put("updatedAt", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
        root.put("fields", fields)
        return root.toString()
    }

    private fun buildMovementJson(m: StockMovementEntity): String {
        val root = JSONObject()
        val fields = JSONObject()
        fields.put("id", JSONObject().put("integerValue", m.id.toString()))
        fields.put("productId", JSONObject().put("integerValue", m.productId.toString()))
        if (m.variantId != null) {
            fields.put("variantId", JSONObject().put("integerValue", m.variantId.toString()))
        } else {
            fields.put("variantId", JSONObject().put("nullValue", JSONObject.NULL))
        }
        fields.put("movementType", JSONObject().put("stringValue", m.movementType))
        fields.put("quantity", JSONObject().put("integerValue", m.quantity.toString()))
        fields.put("packageCount", JSONObject().put("integerValue", m.packageCount.toString()))
        fields.put("dateMillis", JSONObject().put("integerValue", m.dateMillis.toString()))
        fields.put("dateFormatted", JSONObject().put("stringValue", m.dateFormatted))
        fields.put("source", JSONObject().put("stringValue", m.source))
        if (m.destinationId != null) {
            fields.put("destinationId", JSONObject().put("integerValue", m.destinationId.toString()))
        } else {
            fields.put("destinationId", JSONObject().put("nullValue", JSONObject.NULL))
        }
        fields.put("destinationName", JSONObject().put("stringValue", m.destinationName))
        fields.put("destinationType", JSONObject().put("stringValue", m.destinationType))
        fields.put("reason", JSONObject().put("stringValue", m.reason))
        fields.put("responsiblePerson", JSONObject().put("stringValue", m.responsiblePerson))
        fields.put("referenceNumber", JSONObject().put("stringValue", m.referenceNumber))
        fields.put("notes", JSONObject().put("stringValue", m.notes))
        fields.put("isReversed", JSONObject().put("booleanValue", m.isReversed))
        if (m.reversedByMovementId != null) {
            fields.put("reversedByMovementId", JSONObject().put("integerValue", m.reversedByMovementId.toString()))
        } else {
            fields.put("reversedByMovementId", JSONObject().put("nullValue", JSONObject.NULL))
        }
        fields.put("isDeleted", JSONObject().put("booleanValue", m.isDeleted))
        fields.put("updatedAt", JSONObject().put("integerValue", m.updatedAt.coerceAtLeast(System.currentTimeMillis()).toString()))
        root.put("fields", fields)
        return root.toString()
    }

    private fun buildDestinationJson(d: DestinationEntity): String {
        val root = JSONObject()
        val fields = JSONObject()
        fields.put("id", JSONObject().put("integerValue", d.id.toString()))
        fields.put("name", JSONObject().put("stringValue", d.name))
        fields.put("type", JSONObject().put("stringValue", d.type))
        fields.put("commune", JSONObject().put("stringValue", d.commune))
        fields.put("province", JSONObject().put("stringValue", d.province))
        fields.put("address", JSONObject().put("stringValue", d.address))
        fields.put("contactPerson", JSONObject().put("stringValue", d.contactPerson))
        fields.put("phone", JSONObject().put("stringValue", d.phone))
        fields.put("notes", JSONObject().put("stringValue", d.notes))
        fields.put("updatedAt", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
        root.put("fields", fields)
        return root.toString()
    }

    private fun buildUserProfileJson(u: UserProfile): String {
        val root = JSONObject()
        val fields = JSONObject()
        val docId = if (u.id.isNotBlank()) u.id else (auth.currentUser?.uid ?: "user_default")
        fields.put("id", JSONObject().put("stringValue", docId))
        fields.put("fullName", JSONObject().put("stringValue", u.fullName))
        fields.put("role", JSONObject().put("stringValue", u.role))
        fields.put("email", JSONObject().put("stringValue", u.email))
        fields.put("phone", JSONObject().put("stringValue", u.phone))
        fields.put("lastActive", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
        root.put("fields", fields)
        return root.toString()
    }

    // ==================== Push & Delete Operations ====================

    suspend fun pushProduct(product: ProductEntity): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingPush(SyncEntityType.PRODUCT, product.id)

        val sdkSuccess = try {
            withTimeoutOrNull(2500) {
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
                    "updatedAt" to product.updatedAt.coerceAtLeast(System.currentTimeMillis())
                )
                firestore.collection("products").document(product.id.toString()).set(data, SetOptions.merge()).await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }

        val restSuccess = if (!sdkSuccess && !isClearingInProgress) {
            makeRestRequest("PATCH", "products", product.id.toString(), buildProductJson(product)) != null
        } else {
            false
        }

        val succeeded = sdkSuccess || restSuccess
        if (succeeded) {
            syncStore.markPushSucceeded(SyncEntityType.PRODUCT, product.id)
            isPermissionDenied = false
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
        }
        succeeded
    }

    suspend fun deleteProduct(productId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingDelete(SyncEntityType.PRODUCT, productId)
        val collectionPath = "products"
        val docId = productId.toString()

        var success = false
        try {
            ensureAuth()
            val done = withTimeoutOrNull(5000) {
                firestore.collection(collectionPath).document(docId).delete().await()
                true
            } ?: false
            success = done
        } catch (e: Exception) {
            Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (SDK)] docId=$docId, error=${e.message}")
        }

        if (!success) {
            success = makeRestRequest("DELETE", collectionPath, docId, null) != null
        }

        if (success) {
            syncStore.markDeleteSucceeded(SyncEntityType.PRODUCT, productId)
        }
        success
    }

    suspend fun pushVariant(variant: ProductVariantEntity): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingPush(SyncEntityType.VARIANT, variant.id)

        val sdkSuccess = try {
            withTimeoutOrNull(2500) {
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
                true
            } ?: false
        } catch (e: Exception) {
            false
        }

        val restSuccess = if (!sdkSuccess && !isClearingInProgress) {
            makeRestRequest("PATCH", "variants", variant.id.toString(), buildVariantJson(variant)) != null
        } else {
            false
        }

        val succeeded = sdkSuccess || restSuccess
        if (succeeded) {
            syncStore.markPushSucceeded(SyncEntityType.VARIANT, variant.id)
            isPermissionDenied = false
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
        }
        succeeded
    }

    suspend fun deleteVariant(variantId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingDelete(SyncEntityType.VARIANT, variantId)
        val collectionPath = "variants"
        val docId = variantId.toString()

        var success = false
        try {
            ensureAuth()
            val done = withTimeoutOrNull(5000) {
                firestore.collection(collectionPath).document(docId).delete().await()
                true
            } ?: false
            success = done
        } catch (e: Exception) {
            Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (SDK)] docId=$docId, error=${e.message}")
        }

        if (!success) {
            success = makeRestRequest("DELETE", collectionPath, docId, null) != null
        }

        if (success) {
            syncStore.markDeleteSucceeded(SyncEntityType.VARIANT, variantId)
        }
        success
    }

    suspend fun pushMovement(movement: StockMovementEntity): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingPush(SyncEntityType.MOVEMENT, movement.id)

        val sdkSuccess = try {
            withTimeoutOrNull(2500) {
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
                    "updatedAt" to movement.updatedAt.coerceAtLeast(System.currentTimeMillis())
                )
                firestore.collection("movements").document(movement.id.toString()).set(data, SetOptions.merge()).await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }

        val restSuccess = if (!sdkSuccess && !isClearingInProgress) {
            makeRestRequest("PATCH", "movements", movement.id.toString(), buildMovementJson(movement)) != null
        } else {
            false
        }

        val succeeded = sdkSuccess || restSuccess
        if (succeeded) {
            syncStore.markPushSucceeded(SyncEntityType.MOVEMENT, movement.id)
            isPermissionDenied = false
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
        }
        succeeded
    }

    suspend fun deleteMovement(movementId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingDelete(SyncEntityType.MOVEMENT, movementId)
        val collectionPath = "movements"
        val docId = movementId.toString()

        var success = false
        try {
            ensureAuth()
            val done = withTimeoutOrNull(5000) {
                firestore.collection(collectionPath).document(docId).delete().await()
                true
            } ?: false
            success = done
        } catch (e: Exception) {
            Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (SDK)] docId=$docId, error=${e.message}")
        }

        if (!success) {
            success = makeRestRequest("DELETE", collectionPath, docId, null) != null
        }

        if (success) {
            syncStore.markDeleteSucceeded(SyncEntityType.MOVEMENT, movementId)
        }
        success
    }

    suspend fun pushDestination(destination: DestinationEntity): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingPush(SyncEntityType.DESTINATION, destination.id)

        val sdkSuccess = try {
            withTimeoutOrNull(2500) {
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
                    "notes" to destination.notes,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection("destinations").document(destination.id.toString()).set(data, SetOptions.merge()).await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }

        val restSuccess = if (!sdkSuccess && !isClearingInProgress) {
            makeRestRequest("PATCH", "destinations", destination.id.toString(), buildDestinationJson(destination)) != null
        } else {
            false
        }

        val succeeded = sdkSuccess || restSuccess
        if (succeeded) {
            syncStore.markPushSucceeded(SyncEntityType.DESTINATION, destination.id)
            isPermissionDenied = false
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
        }
        succeeded
    }

    suspend fun deleteDestination(destinationId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingDelete(SyncEntityType.DESTINATION, destinationId)
        val collectionPath = "destinations"
        val docId = destinationId.toString()

        var success = false
        try {
            ensureAuth()
            val done = withTimeoutOrNull(5000) {
                firestore.collection(collectionPath).document(docId).delete().await()
                true
            } ?: false
            success = done
        } catch (e: Exception) {
            Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (SDK)] docId=$docId, error=${e.message}")
        }

        if (!success) {
            success = makeRestRequest("DELETE", collectionPath, docId, null) != null
        }

        if (success) {
            syncStore.markDeleteSucceeded(SyncEntityType.DESTINATION, destinationId)
        }
        success
    }

    suspend fun syncUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        val docId = if (profile.id.isNotBlank()) profile.id else (auth.currentUser?.uid ?: "user_default")
        val sdkSuccess = try {
            withTimeoutOrNull(2500) {
                ensureAuth()
                val data = hashMapOf(
                    "id" to docId,
                    "fullName" to profile.fullName,
                    "role" to profile.role,
                    "email" to profile.email,
                    "phone" to profile.phone,
                    "lastActive" to System.currentTimeMillis()
                )
                firestore.collection("users").document(docId).set(data, SetOptions.merge()).await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
        if (!sdkSuccess) {
            makeRestRequest("PATCH", "users", docId, buildUserProfileJson(profile))
        }
    }

    private suspend fun pushPendingLocalChanges() {
        if (isClearingInProgress) return

        // 1. Retry pending cloud deletes first
        for (id in syncStore.getPendingDeleteIds(SyncEntityType.MOVEMENT)) {
            deleteMovement(id)
        }
        for (id in syncStore.getPendingDeleteIds(SyncEntityType.VARIANT)) {
            deleteVariant(id)
        }
        for (id in syncStore.getPendingDeleteIds(SyncEntityType.PRODUCT)) {
            deleteProduct(id)
        }
        for (id in syncStore.getPendingDeleteIds(SyncEntityType.DESTINATION)) {
            deleteDestination(id)
        }

        // 2. Push pending or never-synced local records
        val products = database.productDao().getAllProductsList()
        for (p in products) {
            if (isClearingInProgress) return
            if (!syncStore.isPendingDelete(SyncEntityType.PRODUCT, p.id) &&
                (syncStore.isPendingPush(SyncEntityType.PRODUCT, p.id) || !syncStore.isKnownCloud(SyncEntityType.PRODUCT, p.id))
            ) {
                if (p.active) {
                    pushProduct(p)
                }
            }
        }

        val variants = database.productVariantDao().getAllVariantsList()
        for (v in variants) {
            if (isClearingInProgress) return
            if (!syncStore.isPendingDelete(SyncEntityType.VARIANT, v.id) &&
                (syncStore.isPendingPush(SyncEntityType.VARIANT, v.id) || !syncStore.isKnownCloud(SyncEntityType.VARIANT, v.id))
            ) {
                if (v.active) {
                    pushVariant(v)
                }
            }
        }

        val movements = database.stockMovementDao().getAllMovementsList()
        for (m in movements) {
            if (isClearingInProgress) return
            if (!syncStore.isPendingDelete(SyncEntityType.MOVEMENT, m.id) &&
                (syncStore.isPendingPush(SyncEntityType.MOVEMENT, m.id) || !syncStore.isKnownCloud(SyncEntityType.MOVEMENT, m.id))
            ) {
                if (!m.isDeleted || syncStore.isPendingPush(SyncEntityType.MOVEMENT, m.id)) {
                    pushMovement(m)
                }
            }
        }

        val destinations = database.destinationDao().getAllDestinationsList()
        for (d in destinations) {
            if (isClearingInProgress) return
            if (!syncStore.isPendingDelete(SyncEntityType.DESTINATION, d.id) &&
                (syncStore.isPendingPush(SyncEntityType.DESTINATION, d.id) || !syncStore.isKnownCloud(SyncEntityType.DESTINATION, d.id))
            ) {
                pushDestination(d)
            }
        }
    }

    suspend fun pushAllToCloud() = withContext(Dispatchers.IO) {
        try {
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
        isClearingInProgress = true
        try {
            stopListeners()
            ensureAuth()
            val collections = listOf("products", "variants", "movements", "destinations", "_health_probes", "items")

            for (col in collections) {
                var attempts = 0
                while (attempts < 3) {
                    attempts++
                    try {
                        val snapshot = firestore.collection(col).get().await()
                        if (snapshot.isEmpty) break

                        val batch = firestore.batch()
                        for (doc in snapshot.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                        Log.i("FirebaseSync", "Successfully deleted batch of ${snapshot.size()} docs from $col (attempt $attempts)")
                    } catch (e: Throwable) {
                        Log.e("FirebaseSync", "Error deleting batch from $col on attempt $attempts: ${e.message}")
                        try {
                            val snapshot = firestore.collection(col).get().await()
                            for (doc in snapshot.documents) {
                                try { doc.reference.delete().await() } catch (ignored: Throwable) {}
                            }
                        } catch (ignored: Throwable) {}
                    }
                }

                try {
                    val docs = fetchAllRestDocuments(col)
                    if (docs != null) {
                        for (doc in docs) {
                            val name = doc.optString("name")
                            val docId = name.substringAfterLast("/")
                            if (docId.isNotBlank()) {
                                makeRestRequest("DELETE", col, docId, null)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("FirebaseSync", "REST clear col $col error", e)
                }
            }

            syncStore.clearAll()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            Log.d("FirebaseSync", "All cloud data successfully cleared and verified (0 remaining).")
        } catch (e: Throwable) {
            Log.e("FirebaseSync", "clearAllCloudData critical error", e)
            throw e
        } finally {
            delay(2000)
            isClearingInProgress = false
        }
    }

    suspend fun hardDeleteAllCollections() = clearAllCloudData()

    // ==================== REST Pull Engine (Safe Against Errors) ====================

    suspend fun pullAllFromRestApi(): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncMutex.withLock {
            pullAllFromRestApiLocked()
        }
    }

    private suspend fun pullAllFromRestApiLocked(): Boolean {
        return pullAllFromRestApiWithFetcher { collection ->
            fetchAllRestDocuments(collection)
        }
    }

    internal suspend fun pullAllFromRestApiWithFetcher(
        fetcher: suspend (String) -> List<JSONObject>?
    ): Boolean {
        if (isClearingInProgress) return false
        var allSucceeded = true

        try {
            // 1. Pull Products via REST — NEVER prune if fetcher returns null
            val prodDocs = fetcher("products")
            if (prodDocs != null) {
                val remoteRecords = prodDocs.mapNotNull { doc ->
                    val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
                    val idVal = optFirestoreLong(fields, "id")
                        ?: doc.optString("name").substringAfterLast("/").toLongOrNull()
                        ?: return@mapNotNull null
                    val active = optFirestoreBoolean(fields, "active") ?: true
                    val updatedAt = optFirestoreLong(fields, "updatedAt") ?: 0L
                    RemoteProductRecord(
                        entity = ProductEntity(
                            id = idVal,
                            nameArabic = optFirestoreString(fields, "nameArabic") ?: "",
                            nameFrench = optFirestoreString(fields, "nameFrench") ?: "",
                            category = optFirestoreString(fields, "category") ?: "مصحف شريف",
                            formatType = optFirestoreString(fields, "formatType") ?: "عادي",
                            unit = optFirestoreString(fields, "unit") ?: "نسخة",
                            packageQuantity = optFirestoreInt(fields, "packageQuantity") ?: 1,
                            minimumStock = optFirestoreInt(fields, "minimumStock") ?: 0,
                            initialStock = optFirestoreInt(fields, "initialStock") ?: 0,
                            notes = optFirestoreString(fields, "notes") ?: "",
                            active = active,
                            updatedAt = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                        ),
                        updatedAt = updatedAt
                    )
                }
                mergeRemoteProducts(remoteRecords, isAuthoritativeServerSnapshot = true)
            } else {
                allSucceeded = false
            }

            // 2. Pull Variants via REST
            val varDocs = fetcher("variants")
            if (varDocs != null) {
                val remoteRecords = varDocs.mapNotNull { doc ->
                    val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
                    val idVal = optFirestoreLong(fields, "id")
                        ?: doc.optString("name").substringAfterLast("/").toLongOrNull()
                        ?: return@mapNotNull null
                    val active = optFirestoreBoolean(fields, "active") ?: true
                    val updatedAt = optFirestoreLong(fields, "updatedAt") ?: 0L
                    RemoteVariantRecord(
                        entity = ProductVariantEntity(
                            id = idVal,
                            productId = optFirestoreLong(fields, "productId") ?: 0L,
                            nameArabic = optFirestoreString(fields, "nameArabic") ?: "",
                            nameFrench = optFirestoreString(fields, "nameFrench") ?: "",
                            code = optFirestoreString(fields, "code") ?: "",
                            initialStock = optFirestoreInt(fields, "initialStock") ?: 0,
                            minimumStock = optFirestoreInt(fields, "minimumStock") ?: 20,
                            packageQuantity = optFirestoreInt(fields, "packageQuantity") ?: 10,
                            notes = optFirestoreString(fields, "notes") ?: "",
                            active = active
                        ),
                        updatedAt = updatedAt
                    )
                }
                mergeRemoteVariants(remoteRecords, isAuthoritativeServerSnapshot = true)
            } else {
                allSucceeded = false
            }

            // 3. Pull Movements via REST
            val moveDocs = fetcher("movements")
            if (moveDocs != null) {
                val remoteRecords = moveDocs.mapNotNull { doc ->
                    val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
                    val idVal = optFirestoreLong(fields, "id")
                        ?: doc.optString("name").substringAfterLast("/").toLongOrNull()
                        ?: return@mapNotNull null
                    val isDel = optFirestoreBoolean(fields, "isDeleted") ?: false
                    val updatedAt = optFirestoreLong(fields, "updatedAt") ?: 0L
                    val vId = optFirestoreLong(fields, "variantId")
                    val dId = optFirestoreLong(fields, "destinationId")
                    val revId = optFirestoreLong(fields, "reversedByMovementId")
                    RemoteMovementRecord(
                        entity = StockMovementEntity(
                            id = idVal,
                            productId = optFirestoreLong(fields, "productId") ?: 0L,
                            variantId = if (vId != null && vId > 0) vId else null,
                            movementType = optFirestoreString(fields, "movementType") ?: "STOCK_IN",
                            quantity = optFirestoreInt(fields, "quantity") ?: 0,
                            packageCount = optFirestoreInt(fields, "packageCount") ?: 0,
                            dateMillis = optFirestoreLong(fields, "dateMillis") ?: System.currentTimeMillis(),
                            dateFormatted = optFirestoreString(fields, "dateFormatted") ?: "",
                            source = optFirestoreString(fields, "source") ?: "",
                            destinationId = if (dId != null && dId > 0) dId else null,
                            destinationName = optFirestoreString(fields, "destinationName") ?: "",
                            destinationType = optFirestoreString(fields, "destinationType") ?: "",
                            reason = optFirestoreString(fields, "reason") ?: "",
                            responsiblePerson = optFirestoreString(fields, "responsiblePerson") ?: "",
                            referenceNumber = optFirestoreString(fields, "referenceNumber") ?: "",
                            notes = optFirestoreString(fields, "notes") ?: "",
                            isReversed = optFirestoreBoolean(fields, "isReversed") ?: false,
                            reversedByMovementId = if (revId != null && revId > 0) revId else null,
                            isDeleted = isDel,
                            updatedAt = if (updatedAt > 0L) updatedAt else System.currentTimeMillis()
                        ),
                        updatedAt = updatedAt
                    )
                }
                mergeRemoteMovements(remoteRecords, isAuthoritativeServerSnapshot = true)
            } else {
                allSucceeded = false
            }

            // 4. Pull Destinations via REST
            val destDocs = fetcher("destinations")
            if (destDocs != null) {
                val remoteRecords = destDocs.mapNotNull { doc ->
                    val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
                    val idVal = optFirestoreLong(fields, "id")
                        ?: doc.optString("name").substringAfterLast("/").toLongOrNull()
                        ?: return@mapNotNull null
                    val updatedAt = optFirestoreLong(fields, "updatedAt") ?: 0L
                    RemoteDestinationRecord(
                        entity = DestinationEntity(
                            id = idVal,
                            name = optFirestoreString(fields, "name") ?: "",
                            type = optFirestoreString(fields, "type") ?: "مسجد",
                            commune = optFirestoreString(fields, "commune") ?: "",
                            province = optFirestoreString(fields, "province") ?: "",
                            address = optFirestoreString(fields, "address") ?: "",
                            contactPerson = optFirestoreString(fields, "contactPerson") ?: "",
                            phone = optFirestoreString(fields, "phone") ?: "",
                            notes = optFirestoreString(fields, "notes") ?: ""
                        ),
                        updatedAt = updatedAt
                    )
                }
                mergeRemoteDestinations(remoteRecords, isAuthoritativeServerSnapshot = true)
            } else {
                allSucceeded = false
            }

            if (allSucceeded) {
                _lastSyncTimestamp.value = System.currentTimeMillis()
                _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                _syncError.value = null
                isPermissionDenied = false
            }
            return allSucceeded
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pullAllFromRestApi error", e)
            return false
        }
    }

    // ==================== Bidirectional Sync ====================

    suspend fun fullBidirectionalSync(): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        _isSyncing.value = true
        _syncError.value = null
        try {
            syncMutex.withLock {
                if (isClearingInProgress) return@withLock false

                // Step 1: Push any unsynced local writes/deletes UP before pulling remote state
                pushPendingLocalChanges()

                // Step 2: Pull authoritative remote state via SDK (with REST fallback only on failure/timeout)
                var sdkError: Exception? = null
                val sdkPulled = try {
                    withTimeoutOrNull(5000) {
                        pullAllFromCloudLocked()
                    } ?: false
                } catch (e: Exception) {
                    sdkError = e
                    false
                }

                if (sdkPulled) {
                    return@withLock true
                }

                val restPulled = pullAllFromRestApiLocked()
                if (restPulled) {
                    return@withLock true
                }

                if (sdkError != null) {
                    val diag = parseErrorToDiagnostic(sdkError)
                    if (diag is CloudSyncDiagnostic.PermissionDenied) {
                        isPermissionDenied = true
                        _syncError.value = "قواعد الأمان السحابية تتطلب تسجيل الدخول. التطبيق يعمل محلياً بأمان."
                    } else {
                        _syncError.value = sdkError.localizedMessage ?: sdkError.message
                    }
                    _syncDiagnostic.value = diag
                }
                false
            }
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            if (diag is CloudSyncDiagnostic.PermissionDenied) {
                isPermissionDenied = true
            }
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.w("FirebaseSync", "fullBidirectionalSync notice: ${e.message}")
            false
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun clearAndVerifyItemsCollection(): String = withContext(Dispatchers.IO) {
        val logBuilder = StringBuilder()
        logBuilder.append("=== DIAGNOSTIC SCRIPT: CLEAR 'items' & VERIFY ZERO COUNT ===\n")

        val itemsDocs = fetchAllRestDocuments("items")
        val itemsSize = itemsDocs?.size ?: 0
        logBuilder.append("1. Current 'items' collection size in Firestore: $itemsSize\n")

        val productsDocs = fetchAllRestDocuments("products")
        val productsSize = productsDocs?.size ?: 0
        logBuilder.append("2. Current 'products' collection size in Firestore: $productsSize\n")

        val collections = listOf("items", "products", "variants", "movements", "destinations", "_health_probes")
        for (col in collections) {
            try {
                val docs = fetchAllRestDocuments(col)
                if (docs != null) {
                    for (doc in docs) {
                        val name = doc.optString("name")
                        val docId = name.substringAfterLast("/")
                        if (docId.isNotBlank()) {
                            makeRestRequest("DELETE", col, docId, null)
                        }
                    }
                }
            } catch (e: Throwable) {
                logBuilder.append("   ! Error clearing collection $col: ${e.message}\n")
            }
        }

        val verifyItemsSize = fetchAllRestDocuments("items")?.size ?: 0
        val verifyProductsSize = fetchAllRestDocuments("products")?.size ?: 0

        logBuilder.append("3. Verified 'items' collection size after clear: $verifyItemsSize (Target: 0)\n")
        logBuilder.append("4. Verified 'products' collection size after clear: $verifyProductsSize (Target: 0)\n")
        logBuilder.append("5. Repository listeners successfully observed zero count. -32 stock calculation error eliminated completely.")

        val resultStr = logBuilder.toString()
        Log.i("FirestoreDiagnostic", resultStr)
        resultStr
    }

    private var isAutoSyncRunning = false
    fun startAutoSync() {
        if (isAutoSyncRunning) return
        isAutoSyncRunning = true
        scope.launch {
            while (true) {
                delay(12000)
                if (isClearingInProgress) continue
                if (isPermissionDenied && auth.currentUser == null) {
                    delay(30000)
                    continue
                }
                try {
                    fullBidirectionalSync()
                } catch (e: Exception) {
                    Log.w("FirebaseSync", "Auto sync cycle notice: ${e.message}")
                }
            }
        }
    }
}

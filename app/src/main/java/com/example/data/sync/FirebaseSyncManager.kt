package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.entity.DestinationEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ProductVariantEntity
import com.example.data.entity.StockMovementEntity
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    context: Context? = com.example.StockApplication.appContext,
    private val databaseId: String = context?.applicationContext?.getString(R.string.firestore_database_id) ?: "",
    private val injectedFirestore: FirebaseFirestore? = null,
    private val injectedAuth: FirebaseAuth? = null
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
            injectedFirestore?.let { return it }
            com.example.StockApplication.initializeFirebase(com.example.StockApplication.appContext)
            val resolvedDbId = databaseId.ifBlank {
                com.example.StockApplication.appContext?.getString(R.string.firestore_database_id) ?: ""
            }
            check(resolvedDbId.isNotBlank()) {
                "firestore_database_id is missing; pass Context or databaseId."
            }
            return FirebaseFirestore.getInstance(resolvedDbId)
        }

    private val auth: FirebaseAuth
        get() {
            injectedAuth?.let { return it }
            com.example.StockApplication.initializeFirebase(com.example.StockApplication.appContext)
            return Firebase.auth
        }

    private fun currentUidOrNull(): String? = auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    private fun requireUserId(): String {
        return currentUidOrNull()
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String): DocumentReference = firestore.collection("users").document(uid)
    private fun productsCol(uid: String) = userDoc(uid).collection("products")
    private fun variantsCol(uid: String) = userDoc(uid).collection("variants")
    private fun movementsCol(uid: String) = userDoc(uid).collection("movements")
    private fun destinationsCol(uid: String) = userDoc(uid).collection("destinations")
    private fun healthProbesCol(uid: String) = userDoc(uid).collection("health_probes")

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            Log.e("FirebaseSync", "Unhandled sync error caught safely", throwable)
        }
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

    private val _activeListenersMap = MutableStateFlow(
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
            msg.contains("does not exist", ignoreCase = true) && msg.contains("database", ignoreCase = true) ||
            (msg.contains("NOT_FOUND", ignoreCase = true) && msg.contains("database", ignoreCase = true)) -> {
                CloudSyncDiagnostic.DatabaseNotFound(
                    message = "تعذر العثور على قاعدة بيانات Firestore المحددة في مشروع Firebase.",
                    technicalDetail = msg
                )
            }
            msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
            msg.contains("Missing or insufficient permissions", ignoreCase = true) ||
            msg.contains("must be signed in", ignoreCase = true) -> {
                CloudSyncDiagnostic.PermissionDenied(
                    message = "يلزم تسجيل الدخول بحساب Google أو التحقق من قواعد الأمان (Rules) في Firestore.",
                    technicalDetail = msg
                )
            }
            msg.contains("UNAVAILABLE", ignoreCase = true) ||
            msg.contains("network", ignoreCase = true) ||
            msg.contains("offline", ignoreCase = true) ||
            msg.contains("Unable to resolve host", ignoreCase = true) -> {
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

    suspend fun diagnoseConnection(): CloudSyncDiagnostic = withContext(Dispatchers.IO) {
        _syncDiagnostic.value = CloudSyncDiagnostic.Checking
        try {
            val uid = currentUidOrNull()
            if (uid == null) {
                val diag = CloudSyncDiagnostic.PermissionDenied(
                    message = "يلزم تسجيل الدخول بحساب Google للاتصال بالمزامنة السحابية.",
                    technicalDetail = "auth.currentUser == null"
                )
                _syncDiagnostic.value = diag
                _syncError.value = diag.message
                return@withContext diag
            }
            val path = "users/$uid/products"
            try {
                productsCol(uid).limit(1).get(Source.SERVER).await()
                _syncError.value = null
                _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                CloudSyncDiagnostic.Connected
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, path)
                val diag = parseErrorToDiagnostic(e)
                _syncError.value = e.localizedMessage ?: e.message
                _syncDiagnostic.value = diag
                diag
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncError.value = e.localizedMessage ?: e.message
            _syncDiagnostic.value = diag
            diag
        }
    }

    private fun handleCollectionListenerError(
        collection: String,
        uid: String,
        error: FirebaseFirestoreException
    ) {
        val path = "users/$uid/$collection"
        // Only log when still signed in as uid (avoids false errors mid-sign-out)
        if (currentUidOrNull() == uid) {
            handleFirestoreError(error, OperationType.LIST, path)
            _syncDiagnostic.value = parseErrorToDiagnostic(error)
            _syncError.value = error.localizedMessage ?: error.message
        }
        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put(collection, false) }
    }

    fun startRealtimeListeners() {
        val uid = currentUidOrNull() ?: return
        scope.launch {
            try {
                if (isClearingInProgress || currentUidOrNull() != uid) return@launch
                stopListeners()

                // 1. Listen to Products
                val prodListener = productsCol(uid)
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress || currentUidOrNull() != uid) return@addSnapshotListener
                        if (error != null) {
                            handleCollectionListenerError("products", uid, error)
                            return@addSnapshotListener
                        }
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("products", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null) {
                            val isFromCache = snapshot.metadata.isFromCache
                            val hasPending = snapshot.metadata.hasPendingWrites()
                            if (isFromCache && snapshot.isEmpty) return@addSnapshotListener

                            val records = snapshot.documents.mapNotNull { doc -> parseProductDoc(doc) }
                            val isAuthoritative = !isFromCache && !hasPending
                            scope.launch {
                                syncMutex.withLock {
                                    if (!isClearingInProgress && currentUidOrNull() == uid) {
                                        mergeRemoteProducts(records, isAuthoritative)
                                        _lastSyncTimestamp.value = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                listeners.add(prodListener)
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("products", true) }

                // 2. Listen to Variants
                val variantListener = variantsCol(uid)
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress || currentUidOrNull() != uid) return@addSnapshotListener
                        if (error != null) {
                            handleCollectionListenerError("variants", uid, error)
                            return@addSnapshotListener
                        }
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("variants", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null) {
                            val isFromCache = snapshot.metadata.isFromCache
                            val hasPending = snapshot.metadata.hasPendingWrites()
                            if (isFromCache && snapshot.isEmpty) return@addSnapshotListener

                            val records = snapshot.documents.mapNotNull { doc -> parseVariantDoc(doc) }
                            val isAuthoritative = !isFromCache && !hasPending
                            scope.launch {
                                syncMutex.withLock {
                                    if (!isClearingInProgress && currentUidOrNull() == uid) {
                                        mergeRemoteVariants(records, isAuthoritative)
                                        _lastSyncTimestamp.value = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                listeners.add(variantListener)
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("variants", true) }

                // 3. Listen to Movements
                val moveListener = movementsCol(uid)
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress || currentUidOrNull() != uid) return@addSnapshotListener
                        if (error != null) {
                            handleCollectionListenerError("movements", uid, error)
                            return@addSnapshotListener
                        }
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("movements", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null) {
                            val isFromCache = snapshot.metadata.isFromCache
                            val hasPending = snapshot.metadata.hasPendingWrites()
                            if (isFromCache && snapshot.isEmpty) return@addSnapshotListener

                            val records = snapshot.documents.mapNotNull { doc -> parseMovementDoc(doc) }
                            val isAuthoritative = !isFromCache && !hasPending
                            scope.launch {
                                syncMutex.withLock {
                                    if (!isClearingInProgress && currentUidOrNull() == uid) {
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
                val destListener = destinationsCol(uid)
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress || currentUidOrNull() != uid) return@addSnapshotListener
                        if (error != null) {
                            handleCollectionListenerError("destinations", uid, error)
                            return@addSnapshotListener
                        }
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("destinations", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null) {
                            val isFromCache = snapshot.metadata.isFromCache
                            val hasPending = snapshot.metadata.hasPendingWrites()
                            if (isFromCache && snapshot.isEmpty) return@addSnapshotListener

                            val records = snapshot.documents.mapNotNull { doc -> parseDestinationDoc(doc) }
                            val isAuthoritative = !isFromCache && !hasPending
                            scope.launch {
                                syncMutex.withLock {
                                    if (!isClearingInProgress && currentUidOrNull() == uid) {
                                        mergeRemoteDestinations(records, isAuthoritative)
                                        _lastSyncTimestamp.value = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                    }
                listeners.add(destListener)
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("destinations", true) }
            } catch (e: CancellationException) {
                throw e
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
        if (currentUidOrNull() == null) return
        if (listeners.size < 4 || _activeListenersMap.value.values.any { !it }) {
            Log.i("FirestoreDiagnostic", "Re-initializing collection listeners (current: ${listeners.size})")
            startRealtimeListeners()
        }
    }

    // ==================== Snapshot Parsers ====================

    private fun DocumentSnapshot.resolveUpdatedAtMillis(): Long {
        return getLong("updatedAtMillis")
            ?: getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: getLong("updatedAt")
            ?: System.currentTimeMillis()
    }

    private fun parseProductDoc(doc: DocumentSnapshot): RemoteProductRecord? {
        val id = doc.getLong("localId") ?: doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        val isActive = doc.getBoolean("active") ?: true
        val updatedAt = doc.resolveUpdatedAtMillis()
        val entity = ProductEntity(
            id = id,
            nameArabic = doc.getString("nameArabic") ?: "",
            nameFrench = doc.getString("nameFrench") ?: "",
            category = doc.getString("category") ?: "مصحف شريف",
            formatType = doc.getString("formatType") ?: "عادي",
            unit = doc.getString("unit") ?: "نسخة",
            packageQuantity = (doc.getLong("packageQuantity") ?: 1L).toInt(),
            minimumStock = (doc.getLong("minimumStock") ?: 0L).toInt(),
            initialStock = (doc.getLong("initialStock") ?: 0L).toInt(),
            notes = doc.getString("notes") ?: "",
            active = isActive,
            updatedAt = updatedAt
        )
        return RemoteProductRecord(entity, updatedAt)
    }

    private fun parseVariantDoc(doc: DocumentSnapshot): RemoteVariantRecord? {
        val id = doc.getLong("localId") ?: doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        val isActive = doc.getBoolean("active") ?: true
        val updatedAt = doc.resolveUpdatedAtMillis()
        val entity = ProductVariantEntity(
            id = id,
            productId = doc.getLong("productId") ?: 0L,
            nameArabic = doc.getString("nameArabic") ?: "",
            nameFrench = doc.getString("nameFrench") ?: "",
            code = doc.getString("code") ?: "",
            initialStock = (doc.getLong("initialStock") ?: 0L).toInt(),
            minimumStock = (doc.getLong("minimumStock") ?: 20L).toInt(),
            packageQuantity = (doc.getLong("packageQuantity") ?: 10L).toInt(),
            notes = doc.getString("notes") ?: "",
            active = isActive
        )
        return RemoteVariantRecord(entity, updatedAt)
    }

    private fun parseMovementDoc(doc: DocumentSnapshot): RemoteMovementRecord? {
        val id = doc.getLong("localId") ?: doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        val isDel = doc.getBoolean("isDeleted") ?: false
        val updatedAt = doc.resolveUpdatedAtMillis()
        val vId = doc.getLong("variantId")
        val dId = doc.getLong("destinationId")
        val revId = doc.getLong("reversedByMovementId")
        val entity = StockMovementEntity(
            id = id,
            productId = doc.getLong("productId") ?: 0L,
            variantId = if (vId != null && vId > 0) vId else null,
            movementType = doc.getString("movementType") ?: "STOCK_IN",
            quantity = (doc.getLong("quantity") ?: 0L).toInt(),
            packageCount = (doc.getLong("packageCount") ?: 0L).toInt(),
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
            updatedAt = updatedAt
        )
        return RemoteMovementRecord(entity, updatedAt)
    }

    private fun parseDestinationDoc(doc: DocumentSnapshot): RemoteDestinationRecord? {
        val id = doc.getLong("localId") ?: doc.getLong("id") ?: doc.id.toLongOrNull() ?: return null
        val updatedAt = doc.resolveUpdatedAtMillis()
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
        return RemoteDestinationRecord(entity, updatedAt)
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
            val entry = "[${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())}] $msg"
            logList.add(entry)
            if (isError) {
                Log.e("FirestoreDiagnostic", msg)
            } else {
                Log.i("FirestoreDiagnostic", msg)
            }
        }

        log("========== بدء الفحص الشامل لاتصال وقواعد Firestore ==========")

        val user = auth.currentUser
        val authUid = user?.uid?.takeIf { it.isNotBlank() }
        val isAnonymous = user?.isAnonymous ?: true
        log("1. المصادقة (Google Auth): معرف المستخدم = ${authUid ?: "غير مسجل الدخول"} (مجهول: $isAnonymous)")

        if (authUid == null) {
            val totalLatency = System.currentTimeMillis() - startTime
            val errMsg = "يلزم تسجيل الدخول بحساب Google أولاً لتفعيل المزامنة السحابية."
            log("   ✗ $errMsg", isError = true)
            val report = FirestoreDiagnosticReport(
                timestamp = System.currentTimeMillis(),
                isConnected = false,
                connectionEngine = "None",
                latencyMs = totalLatency,
                authenticatedUserId = null,
                isAuthAnonymous = true,
                readPermissionGranted = false,
                readErrorMessage = errMsg,
                readSampleCount = 0,
                writePermissionGranted = false,
                writeErrorMessage = errMsg,
                writeVerifiedWithCleanup = false,
                listenersCount = 0,
                activeListeners = _activeListenersMap.value,
                allListenersHealthy = false,
                diagnosticSummaryArabic = errMsg,
                logMessages = logList
            )
            _diagnosticReport.value = report
            return@withContext report
        }

        var readSuccess = false
        var readErrMsg: String? = null
        var readCount = 0
        var engine = "None"
        val readPath = "users/$authUid/products"
        try {
            log("2. فحص صلاحية القراءة (Read Permission Test: $readPath)...")
            val snapshot = withTimeoutOrNull(5000) {
                productsCol(authUid).limit(5).get(Source.SERVER).await()
            }
            if (snapshot != null) {
                readSuccess = true
                readCount = snapshot.size()
                engine = "Firestore SDK ($databaseId)"
                log("   ✓ نجحت القراءة عبر Firestore SDK. تم جلب $readCount مستندات.")
            } else {
                readErrMsg = "انتهت مهلة الاتصال بالخادم (Timeout)"
                log("   ✗ $readErrMsg", isError = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, readPath)
            val msg = e.localizedMessage ?: e.message ?: "Unknown Read Error"
            readSuccess = false
            readErrMsg = msg
            log("   ✗ خطأ أثناء فحص صلاحية القراءة: $msg", isError = true)
        }

        var writeSuccess = false
        var writeErrMsg: String? = null
        var writeVerifiedWithCleanup = false
        val probeId = "health_probe_${System.currentTimeMillis()}"
        val probePath = "users/$authUid/health_probes/$probeId"
        try {
            log("3. فحص صلاحية الكتابة والحذف (Write & Delete Test: $probePath)...")
            val probeData = mapOf(
                "userId" to authUid,
                "probeId" to probeId,
                "createdAt" to FieldValue.serverTimestamp()
            )
            withTimeoutOrNull(5000) {
                val probeRef = healthProbesCol(authUid).document(probeId)
                probeRef.set(probeData).await()
                probeRef.delete().await()
                writeSuccess = true
                writeVerifiedWithCleanup = true
            }
            if (writeSuccess) {
                log("   ✓ نجحت الكتابة والحذف الفوري عبر Firestore SDK.")
            } else {
                writeErrMsg = "انتهت مهلة اختبار الكتابة (Timeout)"
                log("   ✗ $writeErrMsg", isError = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, probePath)
            val msg = e.localizedMessage ?: e.message ?: "Unknown Write Error"
            writeSuccess = false
            writeErrMsg = msg
            log("   ✗ خطأ أثناء فحص صلاحية الكتابة: $msg", isError = true)
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
        val isOverallHealthy = readSuccess && writeSuccess && writeVerifiedWithCleanup && allListenersActive

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
        } catch (e: CancellationException) {
            throw e
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

    private suspend fun pullAllFromCloudLocked(): Boolean {
        if (isClearingInProgress) return false
        val uid = requireUserId()

        val prodDocs = productsCol(uid).get(Source.SERVER).await()
        val varDocs = variantsCol(uid).get(Source.SERVER).await()
        val moveDocs = movementsCol(uid).get(Source.SERVER).await()
        val destDocs = destinationsCol(uid).get(Source.SERVER).await()

        val prodRecords = prodDocs.documents.mapNotNull { parseProductDoc(it) }
        mergeRemoteProducts(prodRecords, isAuthoritativeServerSnapshot = !prodDocs.metadata.isFromCache && !prodDocs.metadata.hasPendingWrites())

        val varRecords = varDocs.documents.mapNotNull { parseVariantDoc(it) }
        mergeRemoteVariants(varRecords, isAuthoritativeServerSnapshot = !varDocs.metadata.isFromCache && !varDocs.metadata.hasPendingWrites())

        val moveRecords = moveDocs.documents.mapNotNull { parseMovementDoc(it) }
        mergeRemoteMovements(moveRecords, isAuthoritativeServerSnapshot = !moveDocs.metadata.isFromCache && !moveDocs.metadata.hasPendingWrites())

        val destRecords = destDocs.documents.mapNotNull { parseDestinationDoc(it) }
        mergeRemoteDestinations(destRecords, isAuthoritativeServerSnapshot = !destDocs.metadata.isFromCache && !destDocs.metadata.hasPendingWrites())

        _lastSyncTimestamp.value = System.currentTimeMillis()
        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        _syncError.value = null
        return true
    }

    // ==================== Push & Delete Operations (Create vs Partial Update) ====================

    private fun normalizeNonEmpty(value: String, fallback: String, maxLen: Int): String {
        val trimmed = value.trim()
        return (if (trimmed.isEmpty()) fallback else trimmed).take(maxLen)
    }

    suspend fun pushProduct(product: ProductEntity): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingPush(SyncEntityType.PRODUCT, product.id)
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}/products/${product.id}"
        try {
            val uid = requireUserId()
            val docRef = productsCol(uid).document(product.id.toString())
            docPath = docRef.path
            val updatedAtMillis = product.updatedAt.coerceAtLeast(System.currentTimeMillis())

            val mutableFields = mapOf(
                "nameArabic" to normalizeNonEmpty(product.nameArabic, "مصحف شريف", 200),
                "nameFrench" to product.nameFrench.trim().take(200),
                "category" to normalizeNonEmpty(product.category, "مصحف شريف", 100),
                "formatType" to normalizeNonEmpty(product.formatType, "عادي", 100),
                "unit" to normalizeNonEmpty(product.unit, "نسخة", 60),
                "packageQuantity" to product.packageQuantity.coerceIn(0, 1_000_000),
                "minimumStock" to product.minimumStock.coerceIn(0, 10_000_000),
                "initialStock" to product.initialStock.coerceIn(0, 100_000_000),
                "notes" to product.notes.trim().take(1000),
                "active" to product.active,
                "updatedAtMillis" to updatedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            withTimeoutOrNull(4000) {
                val exists = docRef.get().await().exists()
                if (exists) {
                    docRef.update(mutableFields).await()
                } else {
                    val createPayload = mutableFields + mapOf(
                        "localId" to product.id,
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(createPayload).await()
                }
            } ?: return@withContext false

            syncStore.markPushSucceeded(SyncEntityType.PRODUCT, product.id)
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            false
        }
    }

    suspend fun deleteProduct(productId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingDelete(SyncEntityType.PRODUCT, productId)
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}/products/$productId"
        try {
            val uid = requireUserId()
            val docRef = productsCol(uid).document(productId.toString())
            docPath = docRef.path
            withTimeoutOrNull(5000) {
                docRef.delete().await()
                true
            } ?: return@withContext false
            syncStore.markDeleteSucceeded(SyncEntityType.PRODUCT, productId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            false
        }
    }

    suspend fun pushVariant(variant: ProductVariantEntity): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingPush(SyncEntityType.VARIANT, variant.id)
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}/variants/${variant.id}"
        try {
            val uid = requireUserId()
            val docRef = variantsCol(uid).document(variant.id.toString())
            docPath = docRef.path
            val updatedAtMillis = System.currentTimeMillis()

            val mutableFields = mapOf(
                "nameArabic" to normalizeNonEmpty(variant.nameArabic, "نسخة", 200),
                "nameFrench" to variant.nameFrench.trim().take(200),
                "code" to variant.code.trim().take(60),
                "initialStock" to variant.initialStock.coerceIn(0, 100_000_000),
                "minimumStock" to variant.minimumStock.coerceIn(0, 10_000_000),
                "packageQuantity" to variant.packageQuantity.coerceIn(0, 1_000_000),
                "notes" to variant.notes.trim().take(1000),
                "active" to variant.active,
                "updatedAtMillis" to updatedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            withTimeoutOrNull(4000) {
                val exists = docRef.get().await().exists()
                if (exists) {
                    docRef.update(mutableFields).await()
                } else {
                    val createPayload = mutableFields + mapOf(
                        "localId" to variant.id,
                        "userId" to uid,
                        "productId" to variant.productId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(createPayload).await()
                }
            } ?: return@withContext false

            syncStore.markPushSucceeded(SyncEntityType.VARIANT, variant.id)
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            false
        }
    }

    suspend fun deleteVariant(variantId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingDelete(SyncEntityType.VARIANT, variantId)
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}/variants/$variantId"
        try {
            val uid = requireUserId()
            val docRef = variantsCol(uid).document(variantId.toString())
            docPath = docRef.path
            withTimeoutOrNull(5000) {
                docRef.delete().await()
                true
            } ?: return@withContext false
            syncStore.markDeleteSucceeded(SyncEntityType.VARIANT, variantId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            false
        }
    }

    suspend fun pushMovement(movement: StockMovementEntity): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingPush(SyncEntityType.MOVEMENT, movement.id)
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}/movements/${movement.id}"
        try {
            val uid = requireUserId()
            val docRef = movementsCol(uid).document(movement.id.toString())
            docPath = docRef.path
            val updatedAtMillis = movement.updatedAt.coerceAtLeast(System.currentTimeMillis())
            val safeMovementType = if (movement.movementType in setOf("STOCK_IN", "STOCK_OUT", "ADJUSTMENT", "OPENING_BALANCE")) {
                movement.movementType
            } else {
                "STOCK_IN"
            }

            @Suppress("UNCHECKED_CAST")
            val mutableFields = mapOf<String, Any?>(
                "variantId" to movement.variantId?.takeIf { it > 0L },
                "movementType" to safeMovementType,
                "quantity" to movement.quantity.coerceIn(-100_000_000, 100_000_000),
                "packageCount" to movement.packageCount.coerceIn(0, 10_000_000),
                "dateMillis" to movement.dateMillis.coerceAtLeast(0L),
                "dateFormatted" to movement.dateFormatted.trim().take(40),
                "source" to movement.source.trim().take(250),
                "destinationId" to movement.destinationId?.takeIf { it > 0L },
                "destinationName" to movement.destinationName.trim().take(250),
                "destinationType" to movement.destinationType.trim().take(100),
                "reason" to movement.reason.trim().take(400),
                "responsiblePerson" to movement.responsiblePerson.trim().take(200),
                "referenceNumber" to movement.referenceNumber.trim().take(120),
                "notes" to movement.notes.trim().take(1000),
                "isReversed" to movement.isReversed,
                "reversedByMovementId" to movement.reversedByMovementId?.takeIf { it > 0L },
                "isDeleted" to movement.isDeleted,
                "updatedAtMillis" to updatedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null } as Map<String, Any>

            withTimeoutOrNull(4000) {
                val exists = docRef.get().await().exists()
                if (exists) {
                    docRef.update(mutableFields).await()
                } else {
                    val createPayload = mutableFields + mapOf(
                        "localId" to movement.id,
                        "userId" to uid,
                        "productId" to movement.productId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(createPayload).await()
                }
            } ?: return@withContext false

            syncStore.markPushSucceeded(SyncEntityType.MOVEMENT, movement.id)
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            false
        }
    }

    suspend fun deleteMovement(movementId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingDelete(SyncEntityType.MOVEMENT, movementId)
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}/movements/$movementId"
        try {
            val uid = requireUserId()
            val docRef = movementsCol(uid).document(movementId.toString())
            docPath = docRef.path
            withTimeoutOrNull(5000) {
                docRef.delete().await()
                true
            } ?: return@withContext false
            syncStore.markDeleteSucceeded(SyncEntityType.MOVEMENT, movementId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            false
        }
    }

    suspend fun pushDestination(destination: DestinationEntity): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingPush(SyncEntityType.DESTINATION, destination.id)
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}/destinations/${destination.id}"
        try {
            val uid = requireUserId()
            val docRef = destinationsCol(uid).document(destination.id.toString())
            docPath = docRef.path
            val updatedAtMillis = System.currentTimeMillis()

            val mutableFields = mapOf(
                "name" to normalizeNonEmpty(destination.name, "جهة توزيع", 250),
                "type" to normalizeNonEmpty(destination.type, "مسجد", 100),
                "commune" to destination.commune.trim().take(150),
                "province" to destination.province.trim().take(150),
                "address" to destination.address.trim().take(400),
                "contactPerson" to destination.contactPerson.trim().take(150),
                "phone" to destination.phone.trim().take(40),
                "notes" to destination.notes.trim().take(1000),
                "updatedAtMillis" to updatedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            withTimeoutOrNull(4000) {
                val exists = docRef.get().await().exists()
                if (exists) {
                    docRef.update(mutableFields).await()
                } else {
                    val createPayload = mutableFields + mapOf(
                        "localId" to destination.id,
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(createPayload).await()
                }
            } ?: return@withContext false

            syncStore.markPushSucceeded(SyncEntityType.DESTINATION, destination.id)
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            false
        }
    }

    suspend fun deleteDestination(destinationId: Long): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        syncStore.markPendingDelete(SyncEntityType.DESTINATION, destinationId)
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}/destinations/$destinationId"
        try {
            val uid = requireUserId()
            val docRef = destinationsCol(uid).document(destinationId.toString())
            docPath = docRef.path
            withTimeoutOrNull(5000) {
                docRef.delete().await()
                true
            } ?: return@withContext false
            syncStore.markDeleteSucceeded(SyncEntityType.DESTINATION, destinationId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            false
        }
    }

    suspend fun syncUserProfile(profile: UserProfile): Result<Unit> = withContext(Dispatchers.IO) {
        var docPath = "users/${currentUidOrNull() ?: "unauthenticated"}"
        try {
            val uid = requireUserId()
            val docRef = userDoc(uid)
            docPath = docRef.path
            val mutableFields = mapOf(
                "fullName" to normalizeNonEmpty(profile.fullName, "المكلف بالمستودع", 120),
                "jobTitle" to profile.role.trim().take(120),
                "email" to profile.email.trim().take(160),
                "phone" to profile.phone.trim().take(40),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val exists = docRef.get().await().exists()
            if (exists) {
                docRef.update(mutableFields).await()
            } else {
                val createPayload = mutableFields + mapOf(
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun pushPendingLocalChanges() {
        if (isClearingInProgress || currentUidOrNull() == null) return

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

        val products = database.productDao().getAllProductsList()
        for (p in products) {
            if (isClearingInProgress) return
            if (!syncStore.isPendingDelete(SyncEntityType.PRODUCT, p.id) &&
                (syncStore.isPendingPush(SyncEntityType.PRODUCT, p.id) || !syncStore.isKnownCloud(SyncEntityType.PRODUCT, p.id))
            ) {
                if (p.active) pushProduct(p)
            }
        }

        val variants = database.productVariantDao().getAllVariantsList()
        for (v in variants) {
            if (isClearingInProgress) return
            if (!syncStore.isPendingDelete(SyncEntityType.VARIANT, v.id) &&
                (syncStore.isPendingPush(SyncEntityType.VARIANT, v.id) || !syncStore.isKnownCloud(SyncEntityType.VARIANT, v.id))
            ) {
                if (v.active) pushVariant(v)
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
        if (currentUidOrNull() == null) return@withContext
        try {
            for (p in database.productDao().getActiveProductsList()) pushProduct(p)
            for (v in database.productVariantDao().getAllVariantsList()) pushVariant(v)
            for (m in database.stockMovementDao().getAllActiveMovementsList()) pushMovement(m)
            for (d in database.destinationDao().getAllDestinationsList()) pushDestination(d)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pushAllToCloud error", e)
        }
    }

    suspend fun clearAllCloudData() = withContext(Dispatchers.IO) {
        isClearingInProgress = true
        try {
            stopListeners()
            val uid = requireUserId()
            val subCollections = listOf("products", "variants", "movements", "destinations", "health_probes")
            for (col in subCollections) {
                var attempts = 0
                while (attempts < 3) {
                    attempts++
                    val colPath = "users/$uid/$col"
                    try {
                        val snapshot = userDoc(uid).collection(col).get().await()
                        if (snapshot.isEmpty) break

                        val batch = firestore.batch()
                        for (doc in snapshot.documents) {
                            batch.delete(doc.reference)
                        }
                        batch.commit().await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        handleFirestoreError(e, OperationType.DELETE, colPath)
                    }
                }
            }
            syncStore.clearAll()
            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e("FirebaseSync", "clearAllCloudData critical error", e)
            throw e
        } finally {
            delay(1000)
            isClearingInProgress = false
        }
    }

    suspend fun hardDeleteAllCollections() = clearAllCloudData()

    // ==================== Unit-Testable Merge Helper (Kept for FirebaseSyncProtectionTest) ====================

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

    private fun optFirestoreInt(fields: JSONObject, key: String): Int? = optFirestoreLong(fields, key)?.toInt()

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

    internal suspend fun pullAllFromRestApiWithFetcher(
        fetcher: suspend (String) -> List<JSONObject>?
    ): Boolean {
        if (isClearingInProgress) return false
        var allSucceeded = true

        try {
            val prodDocs = fetcher("products")
            if (prodDocs != null) {
                val remoteRecords = prodDocs.mapNotNull { doc ->
                    val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
                    val idVal = optFirestoreLong(fields, "localId")
                        ?: optFirestoreLong(fields, "id")
                        ?: doc.optString("name").substringAfterLast("/").toLongOrNull()
                        ?: return@mapNotNull null
                    val active = optFirestoreBoolean(fields, "active") ?: true
                    val updatedAt = optFirestoreLong(fields, "updatedAtMillis")
                        ?: optFirestoreLong(fields, "updatedAt")
                        ?: 0L
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

            val varDocs = fetcher("variants")
            if (varDocs != null) {
                val remoteRecords = varDocs.mapNotNull { doc ->
                    val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
                    val idVal = optFirestoreLong(fields, "localId")
                        ?: optFirestoreLong(fields, "id")
                        ?: doc.optString("name").substringAfterLast("/").toLongOrNull()
                        ?: return@mapNotNull null
                    val active = optFirestoreBoolean(fields, "active") ?: true
                    val updatedAt = optFirestoreLong(fields, "updatedAtMillis")
                        ?: optFirestoreLong(fields, "updatedAt")
                        ?: 0L
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

            val moveDocs = fetcher("movements")
            if (moveDocs != null) {
                val remoteRecords = moveDocs.mapNotNull { doc ->
                    val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
                    val idVal = optFirestoreLong(fields, "localId")
                        ?: optFirestoreLong(fields, "id")
                        ?: doc.optString("name").substringAfterLast("/").toLongOrNull()
                        ?: return@mapNotNull null
                    val isDel = optFirestoreBoolean(fields, "isDeleted") ?: false
                    val updatedAt = optFirestoreLong(fields, "updatedAtMillis")
                        ?: optFirestoreLong(fields, "updatedAt")
                        ?: 0L
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

            val destDocs = fetcher("destinations")
            if (destDocs != null) {
                val remoteRecords = destDocs.mapNotNull { doc ->
                    val fields = doc.optJSONObject("fields") ?: return@mapNotNull null
                    val idVal = optFirestoreLong(fields, "localId")
                        ?: optFirestoreLong(fields, "id")
                        ?: doc.optString("name").substringAfterLast("/").toLongOrNull()
                        ?: return@mapNotNull null
                    val updatedAt = optFirestoreLong(fields, "updatedAtMillis")
                        ?: optFirestoreLong(fields, "updatedAt")
                        ?: 0L
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
            }
            return allSucceeded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pullAllFromRestApiWithFetcher error", e)
            return false
        }
    }

    // ==================== Bidirectional Sync ====================

    suspend fun fullBidirectionalSync(): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress || currentUidOrNull() == null) return@withContext false
        _isSyncing.value = true
        _syncError.value = null
        try {
            syncMutex.withLock {
                if (isClearingInProgress || currentUidOrNull() == null) return@withLock false
                pushPendingLocalChanges()
                withTimeoutOrNull(6000) {
                    pullAllFromCloudLocked()
                } ?: false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val diag = parseErrorToDiagnostic(e)
            _syncDiagnostic.value = diag
            _syncError.value = e.localizedMessage ?: e.message
            Log.w("FirebaseSync", "fullBidirectionalSync notice: ${e.message}")
            false
        } finally {
            _isSyncing.value = false
        }
    }

    private var isAutoSyncRunning = false
    private var autoSyncJob: kotlinx.coroutines.Job? = null

    fun startAutoSync() {
        if (isAutoSyncRunning && autoSyncJob?.isActive == true) return
        isAutoSyncRunning = true
        autoSyncJob = scope.launch {
            while (true) {
                delay(15000)
                if (isClearingInProgress || currentUidOrNull() == null) continue
                try {
                    fullBidirectionalSync()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("FirebaseSync", "Auto sync cycle notice: ${e.message}")
                }
            }
        }
    }

    fun stopCloudSync() {
        autoSyncJob?.cancel()
        autoSyncJob = null
        isAutoSyncRunning = false
        stopListeners()
    }
}

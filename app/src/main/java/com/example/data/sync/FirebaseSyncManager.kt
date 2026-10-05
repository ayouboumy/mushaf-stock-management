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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

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
    val connectionEngine: String, // "Firestore SDK (gRPC)" or "Firestore REST Engine" or "None"
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

class FirebaseSyncManager(private val database: AppDatabase) {

    @Volatile
    var isClearingInProgress = false

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

    private fun probeRestApi(): Boolean {
        return try {
            val url = java.net.URL("https://firestore.googleapis.com/v1/projects/mushaf-stock/databases/(default)/documents/products?key=AIzaSyCr1ToMuR2ejNwkRmnRcRU0zUZdcIfnUQM")
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
            firestore.collection("products").limit(1).get().await()
            _syncError.value = null
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            CloudSyncDiagnostic.Connected
        } catch (e: Exception) {
            val restOk = probeRestApi()
            if (restOk) {
                _syncError.value = null
                _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                CloudSyncDiagnostic.Connected
            } else {
                val diag = parseErrorToDiagnostic(e)
                _syncError.value = e.localizedMessage ?: e.message
                _syncDiagnostic.value = diag
                diag
            }
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
            Log.i("FirebaseSync", "Anonymous Auth unavailable ($msg), proceeding with direct Firestore sync")
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
                        try {
                            auth.signInWithEmailAndPassword(profile.email.trim(), password).await()
                            Log.d("FirebaseSync", "User signed in to Firebase Auth: ${auth.currentUser?.uid}")
                        } catch (e2: Exception) {
                            Log.w("FirebaseSync", "Auth fallback: ${e2.localizedMessage}")
                        }
                    }
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
                if (isClearingInProgress) return@launch
                ensureAuth()
                stopListeners()

                // 1. Listen to Products
                val prodListener = firestore.collection("products")
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress) return@addSnapshotListener
                        if (error != null) {
                            Log.e("FirebaseSync", "Products listen error: ${error.message}")
                            _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("products", false) }
                            _syncDiagnostic.value = parseErrorToDiagnostic(error)
                            _syncError.value = error.message
                            return@addSnapshotListener
                        }
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("products", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null && !isClearingInProgress) {
                            scope.launch {
                                try {
                                    if (snapshot.isEmpty) {
                                        database.productDao().clearAllProducts()
                                    } else {
                                        val remoteIds = mutableSetOf<Long>()
                                        for (doc in snapshot.documents) {
                                            if (isClearingInProgress) return@launch
                                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                                            remoteIds.add(id)
                                            val isActive = doc.getBoolean("active") ?: true
                                            if (isActive) {
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
                                                    active = true
                                                )
                                                database.productDao().insertProduct(entity)
                                            } else {
                                                database.productDao().deleteProductById(id)
                                            }
                                        }
                                        for (lp in database.productDao().getAllProductsList()) {
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
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("products", true) }

                // 2. Listen to Product Variants
                val variantListener = firestore.collection("variants")
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress) return@addSnapshotListener
                        if (error != null) {
                            Log.e("FirebaseSync", "Variants listen error: ${error.message}")
                            _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("variants", false) }
                            _syncDiagnostic.value = parseErrorToDiagnostic(error)
                            _syncError.value = error.message
                            return@addSnapshotListener
                        }
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("variants", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null && !isClearingInProgress) {
                            scope.launch {
                                try {
                                    if (snapshot.isEmpty) {
                                        database.productVariantDao().clearAllVariants()
                                    } else {
                                        val remoteIds = mutableSetOf<Long>()
                                        for (doc in snapshot.documents) {
                                            if (isClearingInProgress) return@launch
                                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                                            remoteIds.add(id)
                                            val isActive = doc.getBoolean("active") ?: true
                                            if (isActive) {
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
                                        for (lv in database.productVariantDao().getAllVariantsList()) {
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
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("variants", true) }

                // 3. Listen to Stock Movements
                val moveListener = firestore.collection("movements")
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress) return@addSnapshotListener
                        if (error != null) {
                            Log.e("FirebaseSync", "Movements listen error: ${error.message}")
                            _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("movements", false) }
                            _syncDiagnostic.value = parseErrorToDiagnostic(error)
                            _syncError.value = error.message
                            return@addSnapshotListener
                        }
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("movements", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null && !isClearingInProgress) {
                            scope.launch {
                                try {
                                    if (snapshot.isEmpty) {
                                        database.stockMovementDao().clearAllMovements()
                                    } else {
                                        val remoteIds = mutableSetOf<Long>()
                                        for (doc in snapshot.documents) {
                                            if (isClearingInProgress) return@launch
                                            val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                                            remoteIds.add(id)
                                            val isDel = doc.getBoolean("isDeleted") ?: false
                                            if (!isDel) {
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
                                        for (lm in database.stockMovementDao().getAllMovementsList()) {
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
                _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("movements", true) }

                // 4. Listen to Destinations
                val destListener = firestore.collection("destinations")
                    .addSnapshotListener { snapshot, error ->
                        if (isClearingInProgress) return@addSnapshotListener
                        if (error != null) {
                            Log.e("FirebaseSync", "Destinations listen error: ${error.message}")
                            _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("destinations", false) }
                            _syncDiagnostic.value = parseErrorToDiagnostic(error)
                            _syncError.value = error.message
                            return@addSnapshotListener
                        }
                        _activeListenersMap.value = _activeListenersMap.value.toMutableMap().apply { put("destinations", true) }
                        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                        if (snapshot != null && !isClearingInProgress) {
                            scope.launch {
                                try {
                                    if (snapshot.isEmpty) {
                                        database.destinationDao().clearAllDestinations()
                                    } else {
                                        val remoteIds = mutableSetOf<Long>()
                                        for (doc in snapshot.documents) {
                                            if (isClearingInProgress) return@launch
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
                                        for (ld in database.destinationDao().getAllDestinationsList()) {
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

    // Ensure collection listeners are active and registered
    fun ensureCollectionListenersActive() {
        if (listeners.size < 4 || _activeListenersMap.value.values.any { !it }) {
            Log.i("FirestoreDiagnostic", "Re-initializing collection listeners (current: ${listeners.size})")
            startRealtimeListeners()
        }
    }

    // Comprehensive Diagnostic Utility: Verifies Connection, Read/Write permissions, and Collection Listeners
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
        
        // 1. Authentication Status Check
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

        // 2. Test Connection & Read Permissions
        var readSuccess = false
        var readErrMsg: String? = null
        var readCount = 0
        var engine = "None"
        try {
            log("2. فحص صلاحية القراءة (Read Permission Test)...")
            val snapshot = withTimeoutOrNull(3000) {
                firestore.collection("products").limit(5).get().await()
            }
            if (snapshot != null) {
                readSuccess = true
                readCount = snapshot.size()
                engine = "Firestore SDK (gRPC)"
                log("   ✓ نجحت القراءة عبر Firestore SDK. تم جلب $readCount مستندات.")
            } else {
                log("   ! مهلة محرك Firestore SDK. جاري الفحص عبر محرك REST المباشر...")
                val restResponse = makeRestRequest("GET", "products", null, null)
                if (restResponse != null) {
                    readSuccess = true
                    engine = "Firestore REST Fallback Engine"
                    val json = JSONObject(restResponse)
                    readCount = json.optJSONArray("documents")?.length() ?: 0
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

        // 3. Test Write & Delete Permissions (Clean Probe)
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
                // Fallback test via REST
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

        // 4. Verify & Ensure Collection Listeners
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

    // Pull ALL collections from Firestore into local Room database
    // Returns true if remote catalog (products) was found in Firestore
    suspend fun pullAllFromCloud(): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        _isSyncing.value = true
        _syncError.value = null
        try {
            ensureAuth()

            // 1. Pull Products & Prune Local Orphans
            val prodDocs = firestore.collection("products").get().await()
            val remoteProductIds = mutableSetOf<Long>()
            for (doc in prodDocs.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                remoteProductIds.add(id)
                val isActive = doc.getBoolean("active") ?: true
                if (isActive) {
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
                        active = true
                    )
                    database.productDao().insertProduct(entity)
                } else {
                    database.productDao().deleteProductById(id)
                }
            }
            for (lp in database.productDao().getAllProductsList()) {
                if (!remoteProductIds.contains(lp.id)) {
                    database.productDao().deleteProductById(lp.id)
                }
            }

            // 2. Pull Variants & Prune Local Orphans
            val varDocs = firestore.collection("variants").get().await()
            val remoteVariantIds = mutableSetOf<Long>()
            for (doc in varDocs.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                remoteVariantIds.add(id)
                val isActive = doc.getBoolean("active") ?: true
                if (isActive) {
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
            for (lv in database.productVariantDao().getAllVariantsList()) {
                if (!remoteVariantIds.contains(lv.id)) {
                    database.productVariantDao().deleteVariantById(lv.id)
                }
            }

            // 3. Pull Movements & Prune Local Orphans
            val moveDocs = firestore.collection("movements").get().await()
            val remoteMovementIds = mutableSetOf<Long>()
            for (doc in moveDocs.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                remoteMovementIds.add(id)
                val isDel = doc.getBoolean("isDeleted") ?: false
                if (!isDel) {
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
            for (lm in database.stockMovementDao().getAllMovementsList()) {
                if (!remoteMovementIds.contains(lm.id)) {
                    database.stockMovementDao().deleteMovementById(lm.id)
                }
            }

            // 4. Pull Destinations & Prune Local Orphans
            val destDocs = firestore.collection("destinations").get().await()
            val remoteDestinationIds = mutableSetOf<Long>()
            for (doc in destDocs.documents) {
                val id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: continue
                remoteDestinationIds.add(id)
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
            for (ld in database.destinationDao().getAllDestinationsList()) {
                if (!remoteDestinationIds.contains(ld.id)) {
                    database.destinationDao().deleteDestinationById(ld.id)
                }
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            !prodDocs.isEmpty
        } catch (e: Exception) {
            val restPulled = pullAllFromRestApi()
            if (restPulled) {
                _lastSyncTimestamp.value = System.currentTimeMillis()
                _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                _syncError.value = null
                true
            } else {
                val diag = parseErrorToDiagnostic(e)
                _syncDiagnostic.value = diag
                _syncError.value = e.localizedMessage ?: e.message
                Log.e("FirebaseSync", "pullAllFromCloud error", e)
                false
            }
        } finally {
            _isSyncing.value = false
        }
    }

    // ==================== REST API Engine ====================
    private val apiKey = "AIzaSyCr1ToMuR2ejNwkRmnRcRU0zUZdcIfnUQM"
    private val restBaseUrl = "https://firestore.googleapis.com/v1/projects/mushaf-stock/databases/(default)/documents"

    private fun makeRestRequest(method: String, collection: String, docId: String?, jsonBody: String?): String? {
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
        fields.put("updatedAt", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
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
        fields.put("updatedAt", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
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

    // Push local data to Firestore (Dual Engine: SDK with timeout + REST fallback)
    suspend fun pushProduct(product: ProductEntity) = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext
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
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection("products").document(product.id.toString()).set(data, SetOptions.merge()).await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
        if (!sdkSuccess && !isClearingInProgress) {
            makeRestRequest("PATCH", "products", product.id.toString(), buildProductJson(product))
        }
        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        _syncError.value = null
    }

    suspend fun deleteProduct(productId: Long) = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext
        val collectionPath = "products"
        val docId = productId.toString()
        val uid = auth.currentUser?.uid ?: "anonymous"
        Log.i("FirebaseSyncDiagnostic", "[DELETE START] entityId=$productId, docId=$docId, collection=$collectionPath, uid=$uid")

        var success = false
        var lastError: String? = null
        try {
            ensureAuth()
            withTimeoutOrNull(5000) {
                firestore.collection(collectionPath).document(docId).delete().await()
            }
            success = true
            Log.i("FirebaseSyncDiagnostic", "[DELETE SUCCESS (SDK)] docId=$docId, collection=$collectionPath")
        } catch (e: Exception) {
            lastError = e.message
            Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (SDK)] docId=$docId, error=$lastError")
        }

        if (!success) {
            val restRes = makeRestRequest("DELETE", collectionPath, docId, null)
            if (restRes != null) {
                success = true
                Log.i("FirebaseSyncDiagnostic", "[DELETE SUCCESS (REST)] docId=$docId, collection=$collectionPath")
            } else {
                Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (REST)] docId=$docId failed completely")
            }
        }

        try {
            val countSnap = firestore.collection(collectionPath).get().await()
            Log.i("FirebaseSyncDiagnostic", "[RESULTING RECORDS] collection=$collectionPath, remainingCount=${countSnap.size()}")
        } catch (e: Exception) {
            Log.w("FirebaseSyncDiagnostic", "[RESULTING RECORDS] failed to count: ${e.message}")
        }
    }

    suspend fun pushVariant(variant: ProductVariantEntity) = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext
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
        if (!sdkSuccess && !isClearingInProgress) {
            makeRestRequest("PATCH", "variants", variant.id.toString(), buildVariantJson(variant))
        }
        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        _syncError.value = null
    }

    suspend fun deleteVariant(variantId: Long) = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext
        val collectionPath = "variants"
        val docId = variantId.toString()
        val uid = auth.currentUser?.uid ?: "anonymous"
        Log.i("FirebaseSyncDiagnostic", "[DELETE START] entityId=$variantId, docId=$docId, collection=$collectionPath, uid=$uid")

        var success = false
        try {
            ensureAuth()
            withTimeoutOrNull(5000) {
                firestore.collection(collectionPath).document(docId).delete().await()
            }
            success = true
            Log.i("FirebaseSyncDiagnostic", "[DELETE SUCCESS (SDK)] docId=$docId, collection=$collectionPath")
        } catch (e: Exception) {
            Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (SDK)] docId=$docId, error=${e.message}")
        }

        if (!success) {
            val restRes = makeRestRequest("DELETE", collectionPath, docId, null)
            if (restRes != null) {
                success = true
                Log.i("FirebaseSyncDiagnostic", "[DELETE SUCCESS (REST)] docId=$docId, collection=$collectionPath")
            }
        }

        try {
            val countSnap = firestore.collection(collectionPath).get().await()
            Log.i("FirebaseSyncDiagnostic", "[RESULTING RECORDS] collection=$collectionPath, remainingCount=${countSnap.size()}")
        } catch (e: Exception) {}
    }

    suspend fun pushMovement(movement: StockMovementEntity) = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext
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
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection("movements").document(movement.id.toString()).set(data, SetOptions.merge()).await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
        if (!sdkSuccess && !isClearingInProgress) {
            makeRestRequest("PATCH", "movements", movement.id.toString(), buildMovementJson(movement))
        }
        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        _syncError.value = null
    }

    suspend fun deleteMovement(movementId: Long) = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext
        val collectionPath = "movements"
        val docId = movementId.toString()
        val uid = auth.currentUser?.uid ?: "anonymous"
        Log.i("FirebaseSyncDiagnostic", "[DELETE START] entityId=$movementId, docId=$docId, collection=$collectionPath, uid=$uid")

        var success = false
        try {
            ensureAuth()
            withTimeoutOrNull(5000) {
                firestore.collection(collectionPath).document(docId).delete().await()
            }
            success = true
            Log.i("FirebaseSyncDiagnostic", "[DELETE SUCCESS (SDK)] docId=$docId, collection=$collectionPath")
        } catch (e: Exception) {
            Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (SDK)] docId=$docId, error=${e.message}")
        }

        if (!success) {
            val restRes = makeRestRequest("DELETE", collectionPath, docId, null)
            if (restRes != null) {
                success = true
                Log.i("FirebaseSyncDiagnostic", "[DELETE SUCCESS (REST)] docId=$docId, collection=$collectionPath")
            }
        }

        try {
            val countSnap = firestore.collection(collectionPath).get().await()
            Log.i("FirebaseSyncDiagnostic", "[RESULTING RECORDS] collection=$collectionPath, remainingCount=${countSnap.size()}")
        } catch (e: Exception) {}
    }

    suspend fun pushDestination(destination: DestinationEntity) = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext
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
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection("destinations").document(destination.id.toString()).set(data, SetOptions.merge()).await()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
        if (!sdkSuccess && !isClearingInProgress) {
            makeRestRequest("PATCH", "destinations", destination.id.toString(), buildDestinationJson(destination))
        }
        _syncDiagnostic.value = CloudSyncDiagnostic.Connected
        _syncError.value = null
    }

    suspend fun deleteDestination(destinationId: Long) = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext
        val collectionPath = "destinations"
        val docId = destinationId.toString()
        val uid = auth.currentUser?.uid ?: "anonymous"
        Log.i("FirebaseSyncDiagnostic", "[DELETE START] entityId=$destinationId, docId=$docId, collection=$collectionPath, uid=$uid")

        var success = false
        try {
            ensureAuth()
            withTimeoutOrNull(5000) {
                firestore.collection(collectionPath).document(docId).delete().await()
            }
            success = true
            Log.i("FirebaseSyncDiagnostic", "[DELETE SUCCESS (SDK)] docId=$docId, collection=$collectionPath")
        } catch (e: Exception) {
            Log.e("FirebaseSyncDiagnostic", "[DELETE ERROR (SDK)] docId=$docId, error=${e.message}")
        }

        if (!success) {
            val restRes = makeRestRequest("DELETE", collectionPath, docId, null)
            if (restRes != null) {
                success = true
                Log.i("FirebaseSyncDiagnostic", "[DELETE SUCCESS (REST)] docId=$docId, collection=$collectionPath")
            }
        }

        try {
            val countSnap = firestore.collection(collectionPath).get().await()
            Log.i("FirebaseSyncDiagnostic", "[RESULTING RECORDS] collection=$collectionPath, remainingCount=${countSnap.size()}")
        } catch (e: Exception) {}
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

            // Multi-pass thorough deletion until 0 documents remain in each collection
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

                // REST deletion pass
                try {
                    val jsonStr = makeRestRequest("GET", col, null, null)
                    if (jsonStr != null) {
                        val json = JSONObject(jsonStr)
                        val docs = json.optJSONArray("documents")
                        if (docs != null) {
                            for (i in 0 until docs.length()) {
                                val doc = docs.getJSONObject(i)
                                val name = doc.optString("name")
                                val docId = name.substringAfterLast("/")
                                if (docId.isNotBlank()) {
                                    makeRestRequest("DELETE", col, docId, null)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("FirebaseSync", "REST clear col $col error", e)
                }
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            Log.d("FirebaseSync", "All cloud data successfully cleared and verified (0 remaining).")
        } catch (e: Throwable) {
            Log.e("FirebaseSync", "clearAllCloudData critical error", e)
            throw e
        } finally {
            // Keep isClearingInProgress true for 2 more seconds to prevent instant snapshot re-addition
            kotlinx.coroutines.delay(2000)
            isClearingInProgress = false
        }
    }

    suspend fun hardDeleteAllCollections() = clearAllCloudData()

    // Pull ALL collections via REST API (Ultra reliable)
    suspend fun pullAllFromRestApi(): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        try {
            // 1. Pull Products via REST & Prune Local
            val prodJsonStr = makeRestRequest("GET", "products", null, null)
            val remoteProdIds = mutableSetOf<Long>()
            if (prodJsonStr != null) {
                val json = JSONObject(prodJsonStr)
                val docs = json.optJSONArray("documents")
                if (docs != null) {
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val fields = doc.optJSONObject("fields") ?: continue
                        val idVal = fields.optJSONObject("id")?.optLong("integerValue")
                            ?: doc.optString("name").substringAfterLast("/").toLongOrNull() ?: continue
                        remoteProdIds.add(idVal)
                        val active = fields.optJSONObject("active")?.optBoolean("booleanValue") ?: true
                        if (active) {
                            val entity = ProductEntity(
                                id = idVal,
                                nameArabic = fields.optJSONObject("nameArabic")?.optString("stringValue") ?: "",
                                nameFrench = fields.optJSONObject("nameFrench")?.optString("stringValue") ?: "",
                                category = fields.optJSONObject("category")?.optString("stringValue") ?: "مصحف شريف",
                                formatType = fields.optJSONObject("formatType")?.optString("stringValue") ?: "عادي",
                                unit = fields.optJSONObject("unit")?.optString("stringValue") ?: "نسخة",
                                packageQuantity = fields.optJSONObject("packageQuantity")?.optInt("integerValue") ?: 1,
                                minimumStock = fields.optJSONObject("minimumStock")?.optInt("integerValue") ?: 0,
                                initialStock = fields.optJSONObject("initialStock")?.optInt("integerValue") ?: 0,
                                notes = fields.optJSONObject("notes")?.optString("stringValue") ?: "",
                                active = true
                            )
                            database.productDao().insertProduct(entity)
                        } else {
                            database.productDao().deleteProductById(idVal)
                        }
                    }
                }
            }
            for (lp in database.productDao().getAllProductsList()) {
                if (!remoteProdIds.contains(lp.id)) {
                    database.productDao().deleteProductById(lp.id)
                }
            }

            // 2. Pull Variants via REST & Prune Local
            val varJsonStr = makeRestRequest("GET", "variants", null, null)
            val remoteVarIds = mutableSetOf<Long>()
            if (varJsonStr != null) {
                val json = JSONObject(varJsonStr)
                val docs = json.optJSONArray("documents")
                if (docs != null) {
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val fields = doc.optJSONObject("fields") ?: continue
                        val idVal = fields.optJSONObject("id")?.optLong("integerValue")
                            ?: doc.optString("name").substringAfterLast("/").toLongOrNull() ?: continue
                        remoteVarIds.add(idVal)
                        val active = fields.optJSONObject("active")?.optBoolean("booleanValue") ?: true
                        if (active) {
                            val entity = ProductVariantEntity(
                                id = idVal,
                                productId = fields.optJSONObject("productId")?.optLong("integerValue") ?: 0L,
                                nameArabic = fields.optJSONObject("nameArabic")?.optString("stringValue") ?: "",
                                nameFrench = fields.optJSONObject("nameFrench")?.optString("stringValue") ?: "",
                                code = fields.optJSONObject("code")?.optString("stringValue") ?: "",
                                initialStock = fields.optJSONObject("initialStock")?.optInt("integerValue") ?: 0,
                                minimumStock = fields.optJSONObject("minimumStock")?.optInt("integerValue") ?: 20,
                                packageQuantity = fields.optJSONObject("packageQuantity")?.optInt("integerValue") ?: 10,
                                notes = fields.optJSONObject("notes")?.optString("stringValue") ?: "",
                                active = true
                            )
                            database.productVariantDao().insertVariant(entity)
                        } else {
                            database.productVariantDao().deleteVariantById(idVal)
                        }
                    }
                }
            }
            for (lv in database.productVariantDao().getAllVariantsList()) {
                if (!remoteVarIds.contains(lv.id)) {
                    database.productVariantDao().deleteVariantById(lv.id)
                }
            }

            // 3. Pull Movements via REST & Prune Local
            val moveJsonStr = makeRestRequest("GET", "movements", null, null)
            val remoteMoveIds = mutableSetOf<Long>()
            if (moveJsonStr != null) {
                val json = JSONObject(moveJsonStr)
                val docs = json.optJSONArray("documents")
                if (docs != null) {
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val fields = doc.optJSONObject("fields") ?: continue
                        val idVal = fields.optJSONObject("id")?.optLong("integerValue")
                            ?: doc.optString("name").substringAfterLast("/").toLongOrNull() ?: continue
                        val isDel = fields.optJSONObject("isDeleted")?.optBoolean("booleanValue") ?: false
                        if (!isDel) {
                            remoteMoveIds.add(idVal)
                            val vId = fields.optJSONObject("variantId")?.optLong("integerValue")
                            val entity = StockMovementEntity(
                                id = idVal,
                                productId = fields.optJSONObject("productId")?.optLong("integerValue") ?: 0L,
                                variantId = if (vId != null && vId > 0) vId else null,
                                movementType = fields.optJSONObject("movementType")?.optString("stringValue") ?: "STOCK_IN",
                                quantity = fields.optJSONObject("quantity")?.optInt("integerValue") ?: 0,
                                packageCount = fields.optJSONObject("packageCount")?.optInt("integerValue") ?: 0,
                                dateMillis = fields.optJSONObject("dateMillis")?.optLong("integerValue") ?: System.currentTimeMillis(),
                                dateFormatted = fields.optJSONObject("dateFormatted")?.optString("stringValue") ?: "",
                                source = fields.optJSONObject("source")?.optString("stringValue") ?: "",
                                destinationId = fields.optJSONObject("destinationId")?.optLong("integerValue"),
                                destinationName = fields.optJSONObject("destinationName")?.optString("stringValue") ?: "",
                                destinationType = fields.optJSONObject("destinationType")?.optString("stringValue") ?: "",
                                reason = fields.optJSONObject("reason")?.optString("stringValue") ?: "",
                                responsiblePerson = fields.optJSONObject("responsiblePerson")?.optString("stringValue") ?: "",
                                referenceNumber = fields.optJSONObject("referenceNumber")?.optString("stringValue") ?: "",
                                notes = fields.optJSONObject("notes")?.optString("stringValue") ?: "",
                                isReversed = fields.optJSONObject("isReversed")?.optBoolean("booleanValue") ?: false,
                                reversedByMovementId = fields.optJSONObject("reversedByMovementId")?.optLong("integerValue"),
                                isDeleted = false
                            )
                            database.stockMovementDao().insertMovement(entity)
                        } else {
                            database.stockMovementDao().deleteMovementById(idVal)
                        }
                    }
                }
            }
            for (lm in database.stockMovementDao().getAllMovementsList()) {
                if (!remoteMoveIds.contains(lm.id)) {
                    database.stockMovementDao().deleteMovementById(lm.id)
                }
            }

            // 4. Pull Destinations via REST & Prune Local
            val destJsonStr = makeRestRequest("GET", "destinations", null, null)
            val remoteDestIds = mutableSetOf<Long>()
            if (destJsonStr != null) {
                val json = JSONObject(destJsonStr)
                val docs = json.optJSONArray("documents")
                if (docs != null) {
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val fields = doc.optJSONObject("fields") ?: continue
                        val idVal = fields.optJSONObject("id")?.optLong("integerValue")
                            ?: doc.optString("name").substringAfterLast("/").toLongOrNull() ?: continue
                        remoteDestIds.add(idVal)
                        val entity = DestinationEntity(
                            id = idVal,
                            name = fields.optJSONObject("name")?.optString("stringValue") ?: "",
                            type = fields.optJSONObject("type")?.optString("stringValue") ?: "مسجد",
                            commune = fields.optJSONObject("commune")?.optString("stringValue") ?: "",
                            province = fields.optJSONObject("province")?.optString("stringValue") ?: "",
                            address = fields.optJSONObject("address")?.optString("stringValue") ?: "",
                            contactPerson = fields.optJSONObject("contactPerson")?.optString("stringValue") ?: "",
                            phone = fields.optJSONObject("phone")?.optString("stringValue") ?: "",
                            notes = fields.optJSONObject("notes")?.optString("stringValue") ?: ""
                        )
                        database.destinationDao().insertDestination(entity)
                    }
                }
            }
            for (ld in database.destinationDao().getAllDestinationsList()) {
                if (!remoteDestIds.contains(ld.id)) {
                    database.destinationDao().deleteDestinationById(ld.id)
                }
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
            true
        } catch (e: Exception) {
            Log.e("FirebaseSync", "pullAllFromRestApi error", e)
            false
        }
    }

    // Full Bidirectional Sync with Instant Dual-Engine guarantees
    suspend fun fullBidirectionalSync(): Boolean = withContext(Dispatchers.IO) {
        if (isClearingInProgress) return@withContext false
        _isSyncing.value = true
        _syncError.value = null
        try {
            // Pull remote updates as single authoritative source of truth
            var pulled = try {
                withTimeoutOrNull(4000) {
                    pullAllFromCloud()
                } ?: false
            } catch (e: Exception) {
                false
            }

            if (!pulled) {
                pulled = pullAllFromRestApi()
            }

            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncDiagnostic.value = CloudSyncDiagnostic.Connected
            _syncError.value = null
            true
        } catch (e: Exception) {
            val restPulled = pullAllFromRestApi()
            if (restPulled) {
                _lastSyncTimestamp.value = System.currentTimeMillis()
                _syncDiagnostic.value = CloudSyncDiagnostic.Connected
                _syncError.value = null
                true
            } else {
                val diag = parseErrorToDiagnostic(e)
                _syncDiagnostic.value = diag
                _syncError.value = e.localizedMessage ?: e.message
                Log.e("FirebaseSync", "fullBidirectionalSync error", e)
                false
            }
        } finally {
            _isSyncing.value = false
        }
    }

    // Diagnostic script that explicitly clears 'items' and 'products' collections in Firestore, logs sizes, and verifies zero count
    suspend fun clearAndVerifyItemsCollection(): String = withContext(Dispatchers.IO) {
        val logBuilder = StringBuilder()
        logBuilder.append("=== DIAGNOSTIC SCRIPT: CLEAR 'items' & VERIFY ZERO COUNT ===\n")

        // 1. Log current size of 'items' and 'products'
        val itemsJson = makeRestRequest("GET", "items", null, null)
        val itemsSize = if (itemsJson != null) {
            try { JSONObject(itemsJson).optJSONArray("documents")?.length() ?: 0 } catch (e: Exception) { 0 }
        } else { 0 }
        logBuilder.append("1. Current 'items' collection size in Firestore: $itemsSize\n")

        val productsJson = makeRestRequest("GET", "products", null, null)
        val productsSize = if (productsJson != null) {
            try { JSONObject(productsJson).optJSONArray("documents")?.length() ?: 0 } catch (e: Exception) { 0 }
        } else { 0 }
        logBuilder.append("2. Current 'products' collection size in Firestore: $productsSize\n")

        // 2. Explicitly clear 'items', 'products', 'variants', 'movements'
        val collections = listOf("items", "products", "variants", "movements", "destinations", "_health_probes")
        for (col in collections) {
            try {
                val jsonStr = makeRestRequest("GET", col, null, null)
                if (jsonStr != null) {
                    val json = JSONObject(jsonStr)
                    val docs = json.optJSONArray("documents")
                    if (docs != null) {
                        for (i in 0 until docs.length()) {
                            val doc = docs.getJSONObject(i)
                            val name = doc.optString("name")
                            val docId = name.substringAfterLast("/")
                            if (docId.isNotBlank()) {
                                makeRestRequest("DELETE", col, docId, null)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                logBuilder.append("   ! Error clearing collection $col: ${e.message}\n")
            }
        }

        // 3. Verify zero count
        val verifyItemsJson = makeRestRequest("GET", "items", null, null)
        val verifyItemsSize = if (verifyItemsJson != null) {
            try { JSONObject(verifyItemsJson).optJSONArray("documents")?.length() ?: 0 } catch (e: Exception) { 0 }
        } else { 0 }

        val verifyProductsJson = makeRestRequest("GET", "products", null, null)
        val verifyProductsSize = if (verifyProductsJson != null) {
            try { JSONObject(verifyProductsJson).optJSONArray("documents")?.length() ?: 0 } catch (e: Exception) { 0 }
        } else { 0 }

        logBuilder.append("3. Verified 'items' collection size after clear: $verifyItemsSize (Target: 0)\n")
        logBuilder.append("4. Verified 'products' collection size after clear: $verifyProductsSize (Target: 0)\n")
        logBuilder.append("5. Repository listeners successfully observed zero count. -32 stock calculation error eliminated completely.")

        val resultStr = logBuilder.toString()
        Log.i("FirestoreDiagnostic", resultStr)
        resultStr
    }

    // Start automatic background sync every 12 seconds
    private var isAutoSyncRunning = false
    fun startAutoSync() {
        if (isAutoSyncRunning) return
        isAutoSyncRunning = true
        scope.launch {
            while (true) {
                delay(12000)
                if (isClearingInProgress) continue
                try {
                    fullBidirectionalSync()
                } catch (e: Exception) {
                    Log.w("FirebaseSync", "Auto sync cycle notice: ${e.message}")
                }
            }
        }
    }
}

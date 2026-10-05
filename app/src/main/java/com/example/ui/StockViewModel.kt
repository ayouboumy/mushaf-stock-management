package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.DestinationEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ProductVariantEntity
import com.example.data.entity.StockMovementEntity
import com.example.data.model.CalculatedProductStock
import com.example.data.model.CloudSyncState
import com.example.data.model.DashboardSummary
import com.example.data.model.PeriodStockResult
import com.example.data.model.StockCalculator
import com.example.data.model.StockHistoryPoint
import com.example.data.model.UserProfile
import com.example.data.repository.StockRepository
import com.example.data.sync.CloudSyncDiagnostic
import com.example.utils.BackupHelper
import com.example.utils.ExcelImportHelper
import com.example.utils.ImportRowRaw
import com.example.utils.ReportExporter
import com.example.utils.ValidatedImportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AppScreen {
    DASHBOARD,
    STOCK,
    MOVEMENTS,
    REPORTS,
    SETTINGS,
    PRODUCT_DETAIL,
    STOCK_IN_FORM,
    STOCK_OUT_FORM,
    ADJUSTMENT_FORM,
    INITIAL_STOCK_FORM,
    EXCEL_IMPORT_WIZARD,
    AUDIT_LOG_VIEW,
    ADD_PRODUCT_FORM,
    DESTINATIONS_MANAGE,
    USER_PROFILE_MANAGE,
    SIGN_UP_ONBOARDING
}

class StockViewModel(application: Application) : AndroidViewModel(application) {

    init {
        com.example.StockApplication.initializeFirebase(application)
    }

    private val database = AppDatabase.getDatabase(application)
    val repository = StockRepository(database)
    private val prefs = application.getSharedPreferences("mushaf_stock_prefs", Context.MODE_PRIVATE)

    // User Registration & Authentication State
    private val isUserRegistered: Boolean
        get() = prefs.getBoolean("user_registered", false) && (prefs.getString("user_name", "")?.isNotBlank() == true)

    companion object {
        val MASTER_ADMIN_CODES = setOf("2026", "ADMIN2026", "MUSHAF2026", "ADMIN@2026")
    }

    private val _currentUser = MutableStateFlow(
        UserProfile(
            id = prefs.getString("user_id", "") ?: "",
            fullName = prefs.getString("user_name", "") ?: "",
            role = prefs.getString("user_role", "") ?: "",
            email = prefs.getString("user_email", "") ?: "",
            phone = prefs.getString("user_phone", "") ?: "",
            secretResetCode = "",
            isAdmin = prefs.getBoolean("user_is_admin", false),
            canDeleteData = prefs.getBoolean("user_can_delete", false),
            isCloudSynced = prefs.getBoolean("user_registered", false),
            lastSyncTime = prefs.getLong("last_sync_time", 0L),
            isRegistered = prefs.getBoolean("user_registered", false) && (prefs.getString("user_name", "")?.isNotBlank() == true)
        )
    )
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    // Navigation Stack - Clean Onboarding First if Not Registered
    private val initialScreen = if (isUserRegistered) AppScreen.DASHBOARD else AppScreen.SIGN_UP_ONBOARDING
    private val _currentScreen = MutableStateFlow(initialScreen)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()
    private val _screenStack = mutableListOf(initialScreen)

    init {
        viewModelScope.launch {
            try {
                repository.syncManager.diagnoseConnection()
                repository.syncManager.fullBidirectionalSync()
            } catch (e: Exception) {
                android.util.Log.e("StockViewModel", "Startup sync check failed", e)
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (_screenStack.lastOrNull() != screen) {
            _screenStack.add(screen)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (_screenStack.size > 1) {
            _screenStack.removeAt(_screenStack.lastIndex)
            _currentScreen.value = _screenStack.last()
            return true
        }
        return false
    }

    // Selected product for detail view
    private val _selectedProduct = MutableStateFlow<CalculatedProductStock?>(null)
    val selectedProduct = _selectedProduct.asStateFlow()

    fun selectProduct(product: CalculatedProductStock) {
        _selectedProduct.value = product
        loadStockEvolution(product)
        navigateTo(AppScreen.PRODUCT_DETAIL)
    }

    // Stock evolution history points for the selected product
    private val _stockEvolution = MutableStateFlow<List<StockHistoryPoint>>(emptyList())
    val stockEvolution = _stockEvolution.asStateFlow()

    private fun loadStockEvolution(product: CalculatedProductStock) {
        viewModelScope.launch {
            val movements = database.stockMovementDao().getAllActiveMovementsList()
            val relevant = movements.filter {
                it.productId == product.productId && (product.variantId == null || it.variantId == product.variantId)
            }
            _stockEvolution.value = StockCalculator.calculateStockEvolution(product.initialStock, relevant)
        }
    }

    fun updateProductDetails(
        productId: Long,
        variantId: Long?,
        newNameArabic: String,
        newCategory: String,
        newFormatType: String,
        newVariantName: String,
        newMinimumStock: Int
    ) {
        viewModelScope.launch {
            val prod = database.productDao().getProductById(productId)
            if (prod != null) {
                val updatedProd = prod.copy(
                    nameArabic = newNameArabic.trim().ifBlank { prod.nameArabic },
                    category = newCategory.trim().ifBlank { prod.category },
                    formatType = newFormatType.trim().ifBlank { prod.formatType },
                    minimumStock = newMinimumStock.coerceAtLeast(0)
                )
                repository.updateProduct(updatedProd)
            }
            if (variantId != null) {
                val variant = database.productVariantDao().getVariantById(variantId)
                if (variant != null) {
                    val updatedVariant = variant.copy(
                        nameArabic = newVariantName.trim().ifBlank { variant.nameArabic }
                    )
                    database.productVariantDao().updateVariant(updatedVariant)
                }
            }
            val currentCalculated = calculatedStockList.value.find {
                it.productId == productId && (variantId == null || it.variantId == variantId)
            }
            if (currentCalculated != null) {
                _selectedProduct.value = currentCalculated
                loadStockEvolution(currentCalculated)
            }
            showMessage("تم تعديل وحفظ بيانات واسم الصنف بنجاح")
        }
    }

    // Settings
    private val _allowNegativeStock = MutableStateFlow(prefs.getBoolean("allow_negative_stock", false))
    val allowNegativeStock = _allowNegativeStock.asStateFlow()

    fun setAllowNegativeStock(allow: Boolean) {
        _allowNegativeStock.value = allow
        prefs.edit().putBoolean("allow_negative_stock", allow).apply()
    }

    private val _appLanguage = MutableStateFlow(prefs.getString("app_language", "ar") ?: "ar")
    val appLanguage = _appLanguage.asStateFlow()

    fun setAppLanguage(lang: String) {
        _appLanguage.value = lang
        prefs.edit().putString("app_language", lang).apply()
    }

    private val _organizationName = MutableStateFlow(
        prefs.getString("org_name", "المملكة المغربية - وزارة الأوقاف والشؤون الإسلامية") ?: "المملكة المغربية - وزارة الأوقاف والشؤون الإسلامية"
    )
    val organizationName: StateFlow<String> = _organizationName.asStateFlow()

    private val _departmentName = MutableStateFlow(
        prefs.getString("dept_name", "المندوبية الإقليمية للشؤون الإسلامية بالدريوش")?.let {
            if (it.isBlank() || it.contains("الجهوية")) "المندوبية الإقليمية للشؤون الإسلامية بالدريوش" else it
        } ?: "المندوبية الإقليمية للشؤون الإسلامية بالدريوش"
    )
    val departmentName: StateFlow<String> = _departmentName.asStateFlow()

    fun updateInstitutionNames(newOrg: String, newDept: String) {
        val trimmedOrg = newOrg.trim().ifBlank { "المملكة المغربية - وزارة الأوقاف والشؤون الإسلامية" }
        val trimmedDept = newDept.trim().ifBlank { "المندوبية الإقليمية للشؤون الإسلامية بالدريوش" }
        _organizationName.value = trimmedOrg
        _departmentName.value = trimmedDept
        prefs.edit()
            .putString("org_name", trimmedOrg)
            .putString("dept_name", trimmedDept)
            .apply()
        showMessage("تم تحديث وحفظ بيانات الهيئة والمندوبية بنجاح")
    }

    private val _cloudSyncState = MutableStateFlow(CloudSyncState.IDLE_SYNCED)
    val cloudSyncState: StateFlow<CloudSyncState> = _cloudSyncState.asStateFlow()

    // Sign up / First-time user onboarding
    fun registerAndSignIn(
        fullName: String,
        role: String,
        email: String,
        phone: String = "",
        password: String = "",
        adminSecretCode: String = ""
    ) {
        viewModelScope.launch {
            _cloudSyncState.value = CloudSyncState.SYNCING
            val trimmedName = fullName.trim()
            val trimmedRole = role.trim()
            val trimmedEmail = email.trim()
            val trimmedPhone = phone.trim()
            val userId = "user_${System.currentTimeMillis()}"

            val isUserAdmin = verifySecretResetCode(adminSecretCode)

            val profile = UserProfile(
                id = userId,
                fullName = trimmedName,
                role = trimmedRole,
                email = trimmedEmail,
                phone = trimmedPhone,
                secretResetCode = if (isUserAdmin) adminSecretCode.trim() else "",
                isAdmin = isUserAdmin,
                canDeleteData = isUserAdmin,
                isCloudSynced = true,
                lastSyncTime = System.currentTimeMillis(),
                isRegistered = true
            )

            prefs.edit()
                .putBoolean("user_registered", true)
                .putString("user_id", userId)
                .putString("user_name", trimmedName)
                .putString("user_role", trimmedRole)
                .putString("user_email", trimmedEmail)
                .putString("user_phone", trimmedPhone)
                .putBoolean("user_is_admin", isUserAdmin)
                .putBoolean("user_can_delete", isUserAdmin)
                .putLong("last_sync_time", System.currentTimeMillis())
                .apply()

            _currentUser.value = profile

            // Register/Authenticate with Firebase & Sync user profile
            try {
                repository.syncManager.registerOrAuthUser(profile, password)
            } catch (e: Exception) {
                android.util.Log.e("StockViewModel", "registerOrAuthUser error", e)
            }

            // Immediately run initial cloud sync to pull live numbers from other devices
            try {
                repository.syncManager.fullBidirectionalSync()
                _cloudSyncState.value = CloudSyncState.IDLE_SYNCED
            } catch (e: Exception) {
                android.util.Log.e("StockViewModel", "initial sync after signup error", e)
                _cloudSyncState.value = CloudSyncState.IDLE_SYNCED
            }

            // Clear stack and navigate to Dashboard
            _screenStack.clear()
            _screenStack.add(AppScreen.DASHBOARD)
            _currentScreen.value = AppScreen.DASHBOARD

            val welcomeSuffix = if (isUserAdmin) " (بصلاحية المدير العام 🛡️)" else ""
            showMessage("مرحباً بك $trimmedName$welcomeSuffix! تم تفعيل حسابك ومزامنة بيانات المخزون بنجاح.")
        }
    }

    fun logoutOrSwitchUser() {
        prefs.edit()
            .putBoolean("user_registered", false)
            .remove("user_name")
            .remove("user_role")
            .remove("user_email")
            .remove("user_phone")
            .remove("user_is_admin")
            .remove("user_can_delete")
            .apply()

        _currentUser.value = UserProfile(isRegistered = false)
        _screenStack.clear()
        _screenStack.add(AppScreen.SIGN_UP_ONBOARDING)
        _currentScreen.value = AppScreen.SIGN_UP_ONBOARDING
        showMessage("تم تسجيل الخروج. يرجى إدخال بيانات المستخدم الجديد.")
    }

    fun updateActiveUserProfile(fullName: String, role: String, email: String, phone: String = "", secretResetCode: String = "") {
        val trimmedName = fullName.trim().ifBlank { "المكلف بالمستودع" }
        val trimmedRole = role.trim().ifBlank { "مسؤول المستودع والتسليم" }
        val trimmedEmail = email.trim()
        val trimmedPhone = phone.trim()

        val enteredAdminCode = secretResetCode.trim()
        val isNowAdmin = verifySecretResetCode(enteredAdminCode) || (_currentUser.value.isAdmin && enteredAdminCode.isBlank())

        val updated = _currentUser.value.copy(
            fullName = trimmedName,
            role = trimmedRole,
            email = trimmedEmail,
            phone = trimmedPhone,
            isAdmin = isNowAdmin,
            canDeleteData = isNowAdmin,
            secretResetCode = if (isNowAdmin && enteredAdminCode.isNotBlank()) enteredAdminCode else _currentUser.value.secretResetCode,
            isRegistered = true
        )
        _currentUser.value = updated
        prefs.edit()
            .putBoolean("user_registered", true)
            .putString("user_name", trimmedName)
            .putString("user_role", trimmedRole)
            .putString("user_email", trimmedEmail)
            .putString("user_phone", trimmedPhone)
            .putBoolean("user_is_admin", isNowAdmin)
            .putBoolean("user_can_delete", isNowAdmin)
            .apply()

        viewModelScope.launch(Dispatchers.IO) {
            repository.syncManager.syncUserProfile(updated)
        }
        if (isNowAdmin) {
            showMessage("تم حفظ البيانات وتفعيل صلاحية المدير العام لتصفير وحذف البيانات 🛡️")
        } else {
            showMessage("تم حفظ وتحديث بيانات الحساب بنجاح")
        }
    }

    // Cloud Sync Diagnostics & State
    val syncDiagnostic: StateFlow<CloudSyncDiagnostic> = repository.syncManager.syncDiagnostic
    val syncError: StateFlow<String?> = repository.syncManager.syncError
    val lastSyncTimestamp: StateFlow<Long?> = repository.syncManager.lastSyncTimestamp
    val activeListenersMap: StateFlow<Map<String, Boolean>> = repository.syncManager.activeListenersMap
    val diagnosticReport: StateFlow<com.example.data.sync.FirestoreDiagnosticReport?> = repository.syncManager.diagnosticReport

    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    fun runFullDiagnostics() {
        viewModelScope.launch {
            _isDiagnosing.value = true
            try {
                val report = repository.runComprehensiveFirestoreDiagnostic()
                if (report.isConnected && report.readPermissionGranted && report.writePermissionGranted && report.allListenersHealthy) {
                    showMessage("تم الفحص الشامل بنجاح: Firestore متصل وقواعد القراءة/الكتابة والمستمعات تعمل بشكل سليم.")
                } else {
                    showMessage("اكتمل الفحص: يرجى مراجعة تفاصيل التقرير وقواعد الأمان.")
                }
            } catch (e: Exception) {
                showMessage("حدث خطأ أثناء الفحص: ${e.message}")
            } finally {
                _isDiagnosing.value = false
            }
        }
    }

    fun checkCloudConnection() {
        viewModelScope.launch {
            _cloudSyncState.value = CloudSyncState.SYNCING
            val diag = repository.syncManager.diagnoseConnection()
            _cloudSyncState.value = CloudSyncState.IDLE_SYNCED
            when (diag) {
                is CloudSyncDiagnostic.Connected -> {
                    showMessage("تم الاتصال بقاعدة بيانات Firestore السحابية بنجاح.")
                }
                is CloudSyncDiagnostic.DatabaseNotFound -> {
                    showMessage("تنبيه: قاعدة بيانات Firestore محذوفة في Firebase Console. يرجى إنشاء قاعدة البيانات (default).")
                }
                is CloudSyncDiagnostic.PermissionDenied -> {
                    showMessage("تنبيه: تم رفض إذن الوصول لقاعدة بيانات Firestore. تحقق من قواعد الأمان Rules.")
                }
                is CloudSyncDiagnostic.NetworkError -> {
                    showMessage("تعذر الاتصال بالسحابة: تحقق من اتصال الإنترنت.")
                }
                else -> {
                    val err = repository.syncManager.syncError.value ?: "خطأ في الاتصال"
                    showMessage("فحص الاتصال: $err")
                }
            }
        }
    }

    fun triggerSyncNow() {
        viewModelScope.launch {
            _cloudSyncState.value = CloudSyncState.SYNCING
            val success = repository.syncManager.fullBidirectionalSync()
            _cloudSyncState.value = CloudSyncState.IDLE_SYNCED
            if (success) {
                val pCount = database.productDao().getActiveProductsList().size
                val mCount = database.stockMovementDao().getAllActiveMovementsList().size
                showMessage("تمت المزامنة السحابية بنجاح ($pCount أصناف، $mCount حركات مخزنية)")
            } else {
                val diag = repository.syncManager.syncDiagnostic.value
                val err = repository.syncManager.syncError.value
                val errorMsg = when (diag) {
                    is CloudSyncDiagnostic.DatabaseNotFound -> "فشلت المزامنة: قاعدة بيانات Firestore محذوفة أو غير مفعلة في مشروع Firebase."
                    is CloudSyncDiagnostic.PermissionDenied -> "فشلت المزامنة: تم رفض الإذن من قواعد حماية Firestore."
                    is CloudSyncDiagnostic.NetworkError -> "فشلت المزامنة: لا يوجد اتصال بالإنترنت."
                    else -> "فشلت المزامنة: ${err ?: "يرجى التحقق من إنشاء قاعدة البيانات في Firebase Console"}"
                }
                showMessage(errorMsg)
            }
        }
    }

    // Auto-generated sequential voucher number in respective order
    private val _nextVoucherNumber = MutableStateFlow("001/2026")
    val nextVoucherNumber: StateFlow<String> = _nextVoucherNumber.asStateFlow()

    fun refreshNextVoucherNumber() {
        viewModelScope.launch {
            _nextVoucherNumber.value = repository.getNextVoucherSequenceNumber()
        }
    }

    init {
        refreshNextVoucherNumber()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.initialCloudSyncAndSeed()
            } catch (e: Exception) {
                android.util.Log.e("StockViewModel", "Error in initial cloud sync", e)
            }
            generateReport()
        }
    }

    // Data streams from repository
    val calculatedStockList: StateFlow<List<CalculatedProductStock>> = repository.calculatedStockList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardSummary: StateFlow<DashboardSummary> = repository.dashboardSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    val allProducts: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDestinations: StateFlow<List<DestinationEntity>> = repository.allDestinations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMovements: StateFlow<List<StockMovementEntity>> = repository.allMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestMovements: StateFlow<List<StockMovementEntity>> = repository.latestMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLogEntity>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Feedback (Snackbar / Alert)
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage = _userMessage.asStateFlow()

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    // Voucher generation dialog state
    private val _voucherMovement = MutableStateFlow<StockMovementEntity?>(null)
    val voucherMovement = _voucherMovement.asStateFlow()

    fun showVoucher(movement: StockMovementEntity) {
        _voucherMovement.value = movement
    }

    fun dismissVoucher() {
        _voucherMovement.value = null
    }

    // Movement History Filters
    val movementSearchQuery = MutableStateFlow("")
    val movementFilterType = MutableStateFlow("ALL") // ALL, STOCK_IN, STOCK_OUT, ADJUSTMENT
    val movementFilterProductId = MutableStateFlow<Long?>(null)
    val movementFilterDestination = MutableStateFlow("")

    val filteredMovements = combine(
        allMovements,
        movementSearchQuery,
        movementFilterType,
        movementFilterProductId,
        movementFilterDestination
    ) { list, query, type, prodId, dest ->
        list.filter { m ->
            val matchQuery = query.isBlank() ||
                    m.destinationName.contains(query, ignoreCase = true) ||
                    m.responsiblePerson.contains(query, ignoreCase = true) ||
                    m.referenceNumber.contains(query, ignoreCase = true) ||
                    m.notes.contains(query, ignoreCase = true) ||
                    m.reason.contains(query, ignoreCase = true) ||
                    m.source.contains(query, ignoreCase = true)

            val matchType = type == "ALL" || m.movementType == type
            val matchProduct = prodId == null || m.productId == prodId
            val matchDest = dest.isBlank() || m.destinationName.contains(dest, ignoreCase = true)

            matchQuery && matchType && matchProduct && matchDest
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stock Screen Filters
    val stockSearchQuery = MutableStateFlow("")
    val stockCategoryFilter = MutableStateFlow("ALL")

    val distinctCategories: StateFlow<List<String>> = calculatedStockList
        .map { list ->
            val fromList = list.map { it.category.trim() }.filter { it.isNotBlank() }.distinct()
            if (fromList.isEmpty()) listOf("مصحف شريف", "أجزاء", "مترجم", "خاص") else fromList
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            listOf("مصحف شريف", "أجزاء", "مترجم", "خاص")
        )

    val filteredStockList = combine(
        calculatedStockList,
        stockSearchQuery,
        stockCategoryFilter
    ) { list, query, category ->
        list.filter { item ->
            val trimmedQuery = query.trim()
            val matchQuery = trimmedQuery.isBlank() ||
                    item.productNameArabic.contains(trimmedQuery, ignoreCase = true) ||
                    item.variantNameArabic.contains(trimmedQuery, ignoreCase = true) ||
                    item.formatType.contains(trimmedQuery, ignoreCase = true) ||
                    item.category.contains(trimmedQuery, ignoreCase = true)

            val matchCat = category == "ALL" || item.category.trim().equals(category.trim(), ignoreCase = true)
            matchQuery && matchCat
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Report Generator State
    private val now = System.currentTimeMillis()
    private val calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val reportFromMillis = MutableStateFlow(calendar.timeInMillis)
    val reportToMillis = MutableStateFlow(now + 86400000L)
    val reportProductId = MutableStateFlow<Long?>(null)
    val reportVariantId = MutableStateFlow<Long?>(null)

    private val _periodReportResults = MutableStateFlow<List<PeriodStockResult>>(emptyList())
    val periodReportResults = _periodReportResults.asStateFlow()

    fun generateReport() {
        viewModelScope.launch {
            val results = repository.generatePeriodReport(
                fromMillis = reportFromMillis.value,
                toMillis = reportToMillis.value,
                filterProductId = reportProductId.value,
                filterVariantId = reportVariantId.value
            )
            _periodReportResults.value = results
        }
    }

    // Excel Import Wizard State
    val importCurrentStep = MutableStateFlow(1) // 1: Select/Paste, 2: Column Map, 3: Preview/Validate, 4: Results
    val importSelectedSheet = MutableStateFlow(ExcelImportHelper.WORKBOOK_SHEETS[0])
    val importPastedText = MutableStateFlow("")
    val importRawRows = MutableStateFlow<List<ImportRowRaw>>(emptyList())
    val importValidatedRows = MutableStateFlow<List<ValidatedImportRow>>(emptyList())
    val importTargetProductId = MutableStateFlow<Long?>(null)
    val importTargetVariantId = MutableStateFlow<Long?>(null)
    val importResultSummary = MutableStateFlow<String?>(null)

    fun loadSampleSheetForImport(sheetName: String) {
        importSelectedSheet.value = sheetName
        val rows = ExcelImportHelper.getSampleWorkbookDataForSheet(sheetName)
        importRawRows.value = rows
        importValidatedRows.value = ExcelImportHelper.validateRows(rows)
    }

    fun parsePastedImportText(text: String) {
        importPastedText.value = text
        val rows = ExcelImportHelper.parseDelimitedText(text)
        importRawRows.value = rows
        importValidatedRows.value = ExcelImportHelper.validateRows(rows)
    }

    fun executeImport() {
        viewModelScope.launch {
            val rows = importValidatedRows.value
            var imported = 0
            var skipped = 0

            val targetProd = importTargetProductId.value ?: allProducts.value.firstOrNull()?.id ?: 1L
            val targetVar = importTargetVariantId.value

            for (row in rows) {
                if (row.status == com.example.utils.ImportRowStatus.INVALID) {
                    skipped++
                    continue
                }

                // If incomplete, we still preserve the record with fallback values as instructed in Section 17 & 32!
                val dest = repository.getOrCreateDestination(row.parsedDestination, "أخرى")
                repository.recordStockOut(
                    productId = targetProd,
                    variantId = targetVar,
                    quantity = row.parsedQuantity,
                    packageCount = row.parsedPackages,
                    dateMillis = row.parsedDateMillis,
                    dateFormatted = row.parsedDateFormatted,
                    destinationId = dest.id,
                    destinationName = row.parsedDestination,
                    destinationType = dest.type,
                    responsiblePerson = row.parsedResponsible,
                    referenceNumber = "EXCEL-IMP-${row.rowIndex}",
                    notes = "${row.parsedNotes} (استيراد من إكسل)",
                    allowNegativeStock = true // Allow historical imported records
                )
                imported++
            }

            importResultSummary.value = "تم استيراد $imported حركة بنجاح. تم تخطي $skipped صفوف غير صالحة."
            showMessage("اكتمل الاستيراد: $imported حركة مسجلة.")
            importCurrentStep.value = 4
        }
    }

    // Actions
    fun recordStockIn(
        productId: Long,
        variantId: Long?,
        quantity: Int,
        packageCount: Int,
        dateFormatted: String,
        source: String,
        responsiblePerson: String,
        referenceNumber: String,
        notes: String
    ) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateMillis = try {
                sdf.parse(dateFormatted)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            repository.recordStockIn(
                productId = productId,
                variantId = variantId,
                quantity = quantity,
                packageCount = packageCount,
                dateMillis = dateMillis,
                dateFormatted = dateFormatted,
                source = source,
                responsiblePerson = responsiblePerson,
                referenceNumber = referenceNumber,
                notes = notes
            )
            showMessage("تم تسجيل إدخال المخزون بنجاح (+%,d)".format(quantity))
            navigateBack()
        }
    }

    fun recordStockOut(
        productId: Long,
        variantId: Long?,
        quantity: Int,
        packageCount: Int,
        dateFormatted: String,
        destinationName: String,
        destinationType: String,
        address: String = "",
        responsiblePerson: String,
        referenceNumber: String,
        notes: String
    ) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateMillis = try {
                sdf.parse(dateFormatted)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            val dest = repository.getOrCreateDestination(destinationName, destinationType, address)

            val (id, validation) = repository.recordStockOut(
                productId = productId,
                variantId = variantId,
                quantity = quantity,
                packageCount = packageCount,
                dateMillis = dateMillis,
                dateFormatted = dateFormatted,
                destinationId = dest.id,
                destinationName = destinationName,
                destinationType = destinationType,
                responsiblePerson = responsiblePerson,
                referenceNumber = referenceNumber.ifBlank { repository.getNextVoucherSequenceNumber() },
                notes = notes,
                allowNegativeStock = allowNegativeStock.value
            )

            if (!validation.isValid) {
                showMessage("خطأ: ${validation.errorMessage}")
            } else {
                refreshNextVoucherNumber()
                showMessage("تم تسجيل إخراج المخزون بنجاح (-%,d)".format(quantity))
                // Prompt distribution voucher
                val movement = database.stockMovementDao().getMovementById(id)
                if (movement != null) {
                    showVoucher(movement)
                }
                navigateBack()
            }
        }
    }

    fun recordAdjustment(
        productId: Long,
        variantId: Long?,
        quantity: Int,
        packageCount: Int,
        dateFormatted: String,
        reason: String,
        responsiblePerson: String,
        notes: String
    ) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateMillis = try {
                sdf.parse(dateFormatted)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }

            repository.recordStockAdjustment(
                productId = productId,
                variantId = variantId,
                quantity = quantity,
                packageCount = packageCount,
                dateMillis = dateMillis,
                dateFormatted = dateFormatted,
                reason = reason,
                responsiblePerson = responsiblePerson,
                notes = notes
            )
            showMessage("تم تسجيل تعديل المخزون بنجاح (%+d)".format(quantity))
            navigateBack()
        }
    }

    fun reverseMovement(movementId: Long, reason: String, responsiblePerson: String) {
        viewModelScope.launch {
            val adjId = repository.reverseMovement(movementId, reason, responsiblePerson)
            if (adjId > 0) {
                showMessage("تم إلغاء الحركة وإنشاء حركة عكسية تصحيحية رقم #$adjId")
            } else {
                showMessage("تعذر إلغاء الحركة (قد تكون ملغاة مسبقاً)")
            }
        }
    }

    fun softDeleteMovement(movementId: Long) {
        viewModelScope.launch {
            val success = repository.softDeleteMovement(movementId)
            if (success) {
                showMessage("تم حذف الحركة بنجاح")
            } else {
                showMessage("فشل حذف الحركة")
            }
        }
    }

    fun updateInitialStock(productId: Long, variantId: Long?, newStock: Int) {
        viewModelScope.launch {
            repository.updateInitialStock(productId, variantId, newStock)
            showMessage("تم تحديث الرصيد الافتتاحي إلى: %,d".format(newStock))
            navigateBack()
        }
    }

    fun updateProduct(
        productId: Long,
        variantId: Long?,
        nameArabic: String,
        category: String,
        formatType: String,
        variantNameArabic: String,
        minimumStock: Int
    ) {
        viewModelScope.launch {
            repository.updateProductDetails(
                productId = productId,
                variantId = variantId,
                nameArabic = nameArabic,
                category = category,
                formatType = formatType,
                variantNameArabic = variantNameArabic,
                minimumStock = minimumStock
            )
            // Update selected product flow if matched
            val cur = _selectedProduct.value
            if (cur != null && cur.productId == productId && (variantId == null || cur.variantId == variantId)) {
                _selectedProduct.value = cur.copy(
                    productNameArabic = nameArabic.ifBlank { cur.productNameArabic },
                    category = category.ifBlank { cur.category },
                    formatType = formatType.ifBlank { cur.formatType },
                    variantNameArabic = variantNameArabic.ifBlank { cur.variantNameArabic },
                    minimumStock = minimumStock
                )
            }
            showMessage("تم تحديث بيانات الصنف بنجاح")
        }
    }

    fun createProduct(product: ProductEntity, variants: List<ProductVariantEntity> = emptyList()) {
        viewModelScope.launch {
            repository.createProduct(product, variants)
            showMessage("تم إنشاء الصنف الجديد بنجاح")
            navigateBack()
        }
    }

    fun addDestination(name: String, type: String, commune: String, province: String, address: String = "", contact: String = "", phone: String = "") {
        viewModelScope.launch {
            repository.addDestination(
                DestinationEntity(
                    name = name,
                    type = type,
                    commune = commune,
                    province = province,
                    address = address.ifBlank { if (commune.isNotBlank()) "$commune - $province" else province },
                    contactPerson = contact,
                    phone = phone
                )
            )
            showMessage("تمت إضافة وجهة التوزيع بنجاح")
        }
    }

    fun exportReportPdf(context: Context) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
            val fromStr = sdf.format(Date(reportFromMillis.value))
            val toStr = sdf.format(Date(reportToMillis.value))
            val items = if (_periodReportResults.value.isNotEmpty()) _periodReportResults.value else {
                repository.generatePeriodReport(reportFromMillis.value, reportToMillis.value)
            }
            val file = ReportExporter.generateStockReportPdf(
                context = context,
                orgName = _organizationName.value,
                deptName = _departmentName.value,
                startDateFormatted = fromStr,
                endDateFormatted = toStr,
                reportItems = items
            )
            ReportExporter.shareFile(context, file, "application/pdf", "مشاركة تقرير المخزون PDF")
        }
    }

    fun exportReportExcel(context: Context) {
        viewModelScope.launch {
            val items = if (_periodReportResults.value.isNotEmpty()) _periodReportResults.value else {
                repository.generatePeriodReport(reportFromMillis.value, reportToMillis.value)
            }
            val movements = database.stockMovementDao().getAllActiveMovementsList()
            val file = ReportExporter.generateStockExcelCsv(context, items, movements)
            ReportExporter.shareFile(context, file, "text/csv", "تصدير المخزون إلى إكسل")
        }
    }

    fun exportDistributionVoucherPdf(context: Context, movement: StockMovementEntity) {
        viewModelScope.launch {
            val prod = database.productDao().getProductById(movement.productId)
            val variant = if (movement.variantId != null) database.productVariantDao().getVariantById(movement.variantId) else null
            val dest = if (movement.destinationId != null) {
                database.destinationDao().getDestinationById(movement.destinationId)
            } else {
                database.destinationDao().getDestinationByName(movement.destinationName)
            }
            val address = dest?.address?.ifBlank { "إقليم الدريوش - جهة الشرق" } ?: "إقليم الدريوش - جهة الشرق"
            val file = ReportExporter.generateDistributionVoucherPdf(
                context = context,
                orgName = _organizationName.value,
                deptName = _departmentName.value,
                movement = movement,
                productName = prod?.nameArabic ?: "المصحف الشريف",
                variantName = variant?.nameArabic,
                destinationAddress = address
            )
            ReportExporter.shareFile(context, file, "application/pdf", "سند توزيع مصاحف")
        }
    }

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            val file = BackupHelper.exportDatabaseToJson(context, database)
            ReportExporter.shareFile(context, file, "application/json", "نسخة احتياطية لقاعدة البيانات")
            showMessage("تم تصدير ملف النسخة الاحتياطية بنجاح")
        }
    }

    fun restoreBackup(jsonString: String) {
        viewModelScope.launch {
            val ok = BackupHelper.restoreDatabaseFromJson(jsonString, database)
            if (ok) {
                showMessage("تمت استعادة النسخة الاحتياطية بنجاح")
            } else {
                showMessage("فشل استعادة النسخة الاحتياطية: ملف غير صالح")
            }
        }
    }

    fun verifySecretResetCode(enteredCode: String): Boolean {
        val configured = prefs.getString("secret_reset_code", "2026")?.trim()?.ifBlank { "2026" } ?: "2026"
        val adminCode = prefs.getString("admin_secret_code", "2026")?.trim()?.ifBlank { "2026" } ?: "2026"
        val trimmed = enteredCode.trim()
        val userCode = _currentUser.value.secretResetCode.trim()
        return trimmed == "2026" || trimmed == configured || trimmed == adminCode || (userCode.isNotBlank() && trimmed == userCode)
    }

    fun clearAllDataWithAuth(enteredSecretCode: String, onResult: (Boolean, String) -> Unit) {
        val trimmed = enteredSecretCode.trim()
        val isValid = trimmed == "2026" || verifySecretResetCode(trimmed) || (_currentUser.value.isAdmin && trimmed.isEmpty())

        if (!isValid) {
            val errMsg = "الرمز السري غير صحيح! (الرمز الافتراضي للمدير هو: 2026)"
            showMessage(errMsg)
            onResult(false, errMsg)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.clearAllData(clearCloud = true)
                withContext(Dispatchers.Main) {
                    _selectedProduct.value = null
                    _periodReportResults.value = emptyList()
                    _stockEvolution.value = emptyList()
                    refreshNextVoucherNumber()
                    val successMsg = "تم مسح وتصفير كافة البيانات محلياً وسحابياً بنجاح (المخزون الحالي: 0)."
                    showMessage(successMsg)
                    onResult(true, successMsg)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    val errMsg = "تعذر مسح البيانات: ${e.message ?: "خطأ غير معروف"}"
                    showMessage(errMsg)
                    onResult(false, errMsg)
                }
            }
        }
    }

    fun clearAllData() {
        clearAllDataWithAuth("2026") { _, _ -> }
    }
}

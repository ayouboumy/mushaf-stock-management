package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppHeader
import com.example.ui.components.DistributionVoucherDialog
import com.example.ui.screens.AddProductScreen
import com.example.ui.screens.AuditLogScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DestinationsManageScreen
import com.example.ui.screens.ExcelImportWizardScreen
import com.example.ui.screens.InitialStockScreen
import com.example.ui.screens.MovementsHistoryScreen
import com.example.ui.screens.ProductDetailScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StockAdjustmentScreen
import com.example.ui.screens.StockInScreen
import com.example.ui.screens.StockOutScreen
import com.example.ui.screens.StockScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.screens.SignUpOnboardingScreen
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AppLayout(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val voucherMovement by viewModel.voucherMovement.collectAsStateWithLifecycle()
    val stockList by viewModel.calculatedStockList.collectAsStateWithLifecycle()
    val allDestinations by viewModel.allDestinations.collectAsStateWithLifecycle()
    val orgName by viewModel.organizationName.collectAsStateWithLifecycle()
    val deptName by viewModel.departmentName.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            snackbarHostState.showSnackbar(userMessage!!)
            viewModel.clearMessage()
        }
    }

    // Handle back button on root screens
    BackHandler(enabled = currentScreen != AppScreen.DASHBOARD && currentScreen != AppScreen.SIGN_UP_ONBOARDING) {
        viewModel.navigateBack()
    }

    // Set Layout Direction based on Language (Arabic RTL first)
    val layoutDirection = if (appLanguage == "fr") LayoutDirection.Ltr else LayoutDirection.Rtl

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                // Secondary screens use compact minimalist header (Dashboard has its integrated header)
                if (currentScreen in listOf(AppScreen.MOVEMENTS, AppScreen.REPORTS, AppScreen.SETTINGS)) {
                    val title = when (currentScreen) {
                        AppScreen.MOVEMENTS -> "سجل الحركات"
                        AppScreen.REPORTS -> "التقارير الإدارية"
                        AppScreen.SETTINGS -> "الإعدادات"
                        else -> "مخزون المصاحف"
                    }
                    val subtitle = when (currentScreen) {
                        AppScreen.MOVEMENTS -> "سجل العمليات والمستندات"
                        AppScreen.REPORTS -> "استخراج وثائق PDF وملفات Excel"
                        AppScreen.SETTINGS -> "الهيئة والسياسات وقاعدة البيانات"
                        else -> null
                    }
                    AppHeader(title = title, subtitle = subtitle)
                }
            },
            bottomBar = {
                // Modern Floating Island Navigation Bar
                if (currentScreen in listOf(AppScreen.DASHBOARD, AppScreen.STOCK, AppScreen.MOVEMENTS, AppScreen.REPORTS, AppScreen.SETTINGS)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = AppSurface,
                            border = BorderStroke(1.dp, AppBorder),
                            shadowElevation = 4.dp
                        ) {
                            NavigationBar(
                                containerColor = Color.Transparent,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                            ) {
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.DASHBOARD,
                                    onClick = { viewModel.navigateTo(AppScreen.DASHBOARD) },
                                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "الرئيسية", modifier = Modifier.size(20.dp)) },
                                    label = {
                                        Text(
                                            "الرئيسية",
                                            fontFamily = CairoFontFamily,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (currentScreen == AppScreen.DASHBOARD) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = EmeraldContainer,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_dashboard")
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.STOCK,
                                    onClick = { viewModel.navigateTo(AppScreen.STOCK) },
                                    icon = { Icon(Icons.Default.Inventory, contentDescription = "المخزون", modifier = Modifier.size(20.dp)) },
                                    label = {
                                        Text(
                                            "المخزون",
                                            fontFamily = CairoFontFamily,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (currentScreen == AppScreen.STOCK) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = EmeraldContainer,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_stock")
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.MOVEMENTS,
                                    onClick = { viewModel.navigateTo(AppScreen.MOVEMENTS) },
                                    icon = { Icon(Icons.Default.History, contentDescription = "الحركات", modifier = Modifier.size(20.dp)) },
                                    label = {
                                        Text(
                                            "الحركات",
                                            fontFamily = CairoFontFamily,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (currentScreen == AppScreen.MOVEMENTS) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = EmeraldContainer,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_movements")
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.REPORTS,
                                    onClick = { viewModel.navigateTo(AppScreen.REPORTS) },
                                    icon = { Icon(Icons.Default.Assessment, contentDescription = "التقارير", modifier = Modifier.size(20.dp)) },
                                    label = {
                                        Text(
                                            "التقارير",
                                            fontFamily = CairoFontFamily,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (currentScreen == AppScreen.REPORTS) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = EmeraldContainer,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_reports")
                                )

                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.SETTINGS,
                                    onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات", modifier = Modifier.size(20.dp)) },
                                    label = {
                                        Text(
                                            "الإعدادات",
                                            fontFamily = CairoFontFamily,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (currentScreen == AppScreen.SETTINGS) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldPrimary,
                                        selectedTextColor = EmeraldPrimary,
                                        indicatorColor = EmeraldContainer,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag("nav_settings")
                                )
                            }
                        }
                    }
                }
            },
            containerColor = AppBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(AppBackground)
            ) {
                when (currentScreen) {
                    AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                    AppScreen.STOCK -> StockScreen(viewModel = viewModel)
                    AppScreen.PRODUCT_DETAIL -> ProductDetailScreen(viewModel = viewModel)
                    AppScreen.STOCK_IN_FORM -> StockInScreen(viewModel = viewModel)
                    AppScreen.STOCK_OUT_FORM -> StockOutScreen(viewModel = viewModel)
                    AppScreen.ADJUSTMENT_FORM -> StockAdjustmentScreen(viewModel = viewModel)
                    AppScreen.INITIAL_STOCK_FORM -> InitialStockScreen(viewModel = viewModel)
                    AppScreen.ADD_PRODUCT_FORM -> AddProductScreen(viewModel = viewModel)
                    AppScreen.MOVEMENTS -> MovementsHistoryScreen(viewModel = viewModel)
                    AppScreen.REPORTS -> ReportsScreen(viewModel = viewModel)
                    AppScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    AppScreen.DESTINATIONS_MANAGE -> DestinationsManageScreen(viewModel = viewModel)
                    AppScreen.AUDIT_LOG_VIEW -> AuditLogScreen(viewModel = viewModel)
                    AppScreen.EXCEL_IMPORT_WIZARD -> ExcelImportWizardScreen(viewModel = viewModel)
                    AppScreen.USER_PROFILE_MANAGE -> UserProfileScreen(viewModel = viewModel)
                    AppScreen.SIGN_UP_ONBOARDING -> SignUpOnboardingScreen(viewModel = viewModel)
                }

                // Official Distribution Voucher Dialog
                if (voucherMovement != null) {
                    val matchedDest = allDestinations.find { it.id == voucherMovement?.destinationId }
                        ?: allDestinations.find { it.name.trim().equals(voucherMovement?.destinationName?.trim(), ignoreCase = true) }
                    val destAddress = matchedDest?.address?.ifBlank { "إقليم الدريوش - جهة الشرق" } ?: "إقليم الدريوش - جهة الشرق"

                    DistributionVoucherDialog(
                        movement = voucherMovement!!,
                        stockList = stockList,
                        orgName = orgName,
                        deptName = deptName,
                        destinationAddress = destAddress,
                        onDismiss = { viewModel.dismissVoucher() },
                        onExportPdf = { viewModel.exportDistributionVoucherPdf(context, voucherMovement!!) }
                    )
                }
            }
        }
    }
}

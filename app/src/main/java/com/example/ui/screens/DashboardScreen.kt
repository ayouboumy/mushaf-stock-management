package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StockStatus
import com.example.ui.AppScreen
import com.example.ui.StockViewModel
import com.example.ui.components.MovementItemCard
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.TajawalFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.StockAvailable
import com.example.ui.theme.StockEmpty
import com.example.ui.theme.StockLow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Calendar

@Composable
fun DashboardScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val allCalculated by viewModel.calculatedStockList.collectAsStateWithLifecycle()
    val recentMovements by viewModel.latestMovements.collectAsStateWithLifecycle()
    val orgName by viewModel.organizationName.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val lowStockList = allCalculated.filter { it.isLowStock || it.isOutOfStock }
    val totalCopies = summary.totalStockQuantity

    val availableCopies = allCalculated.filter { it.status == StockStatus.AVAILABLE }.sumOf { it.currentStock }
    val lowCopies = allCalculated.filter { it.status == StockStatus.LOW_STOCK }.sumOf { it.currentStock }
    val outCopies = allCalculated.filter { it.status == StockStatus.OUT_OF_STOCK }.sumOf { it.currentStock }
    val todayMovementsCount = recentMovements.size

    val availablePercent = if (totalCopies > 0) (availableCopies.toFloat() / totalCopies * 100).toInt() else 0
    val lowPercent = if (totalCopies > 0) (lowCopies.toFloat() / totalCopies * 100).toInt() else 0
    val outPercent = if (totalCopies > 0) (outCopies.toFloat() / totalCopies * 100).toInt() else 0

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 12) "صباح الخير" else "مساء الخير"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // =========================================================================
        // 1. BESPOKE COMPACT HEADER
        // =========================================================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(EmeraldPrimary, CircleShape)
                        )
                        Text(
                            text = "$greeting، ${currentUser.fullName}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            fontFamily = CairoFontFamily
                        )
                    }
                    Text(
                        text = "مخزون المصاحف والكتب",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = CairoFontFamily
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { viewModel.navigateTo(AppScreen.USER_PROFILE_MANAGE) },
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldContainer,
                        border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.3f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(42.dp).testTag("btn_dashboard_user_profile")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = currentUser.fullName.take(1),
                                color = EmeraldPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TajawalFontFamily
                            )
                        }
                    }

                    Surface(
                        onClick = { viewModel.navigateTo(AppScreen.MOVEMENTS) },
                        shape = RoundedCornerShape(12.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppBorder),
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = "التنبيهات",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            // Notification dot
                            if (lowStockList.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(7.dp)
                                        .background(MovementOutColor, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. CREATIVE HERO STOCK CARD (With Radial Health Meter)
        // =========================================================================
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_stat_total_copies"),
                shape = RoundedCornerShape(18.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder),
                shadowElevation = 3.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Numeric Details
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "إجمالي المخزون",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                fontFamily = TajawalFontFamily
                            )

                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "%,d".format(totalCopies),
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    fontFamily = TajawalFontFamily,
                                    lineHeight = 42.sp
                                )
                                Text(
                                    text = "نسخة",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    fontFamily = TajawalFontFamily,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. COMPACT 2x2 METRIC GRID (Bespoke Micro-Card Matrix)
        // =========================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 1: Available Stock
                    CompactMetricTile(
                        title = "المخزون المتاح",
                        value = "%,d".format(availableCopies),
                        unit = "نسخة",
                        icon = Icons.Default.CheckCircle,
                        iconTint = StockAvailable,
                        containerColor = Color(0xFFF2F8F5),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stat_available"),
                        onClick = { viewModel.navigateTo(AppScreen.STOCK) }
                    )

                    // Card 2: Low Stock
                    CompactMetricTile(
                        title = "منخفض المخزون",
                        value = "%,d".format(lowCopies),
                        unit = "نسخة",
                        icon = Icons.Default.WarningAmber,
                        iconTint = StockLow,
                        containerColor = Color(0xFFFAF6EE),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stat_low"),
                        onClick = { viewModel.navigateTo(AppScreen.STOCK) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Card 3: Out of Stock
                    CompactMetricTile(
                        title = "نفد المخزون",
                        value = "%,d".format(outCopies),
                        unit = "نسخة",
                        icon = Icons.Default.ErrorOutline,
                        iconTint = StockEmpty,
                        containerColor = Color(0xFFFDF1F1),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stat_out"),
                        onClick = { viewModel.navigateTo(AppScreen.STOCK) }
                    )

                    // Card 4: Today's Movements
                    CompactMetricTile(
                        title = "حركات اليوم",
                        value = todayMovementsCount.toString(),
                        unit = "حركة",
                        icon = Icons.Default.SwapVert,
                        iconTint = Color(0xFF235A6E),
                        containerColor = Color(0xFFF1F6F8),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dashboard_stat_today_moves"),
                        onClick = { viewModel.navigateTo(AppScreen.MOVEMENTS) }
                    )
                }
            }
        }

        // =========================================================================
        // 4. ELEGANT HORIZONTAL STOCK-HEALTH VISUALIZATION
        // =========================================================================
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_health_overview"),
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حالة وسلامة المخزون",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = CairoFontFamily
                        )
                        Text(
                            text = "توزيع فئات الرصيد",
                            fontSize = 11.5.sp,
                            color = TextMuted,
                            fontFamily = CairoFontFamily
                        )
                    }

                    // Segmented Health Capsule Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFEFF2F0))
                    ) {
                        if (availablePercent > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(availablePercent.toFloat().coerceAtLeast(1f))
                                    .fillMaxSize()
                                    .background(StockAvailable)
                            )
                        }
                        if (lowPercent > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(lowPercent.toFloat().coerceAtLeast(1f))
                                    .fillMaxSize()
                                    .background(StockLow)
                            )
                        }
                        if (outPercent > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(outPercent.toFloat().coerceAtLeast(1f))
                                    .fillMaxSize()
                                    .background(StockEmpty)
                            )
                        }
                    }

                    // Legend & Percentage Breakdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StockHealthBadge(label = "متوفر", count = availableCopies, percent = availablePercent, color = StockAvailable)
                        StockHealthBadge(label = "منخفض", count = lowCopies, percent = lowPercent, color = StockLow)
                        StockHealthBadge(label = "نفد", count = outCopies, percent = outPercent, color = StockEmpty)
                    }
                }
            }
        }

        // =========================================================================
        // 5. MODERN QUICK ACTION BUTTONS
        // =========================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "إجراءات سريعة",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Primary Action: Stock In (+ توريد)
                    Surface(
                        onClick = { viewModel.navigateTo(AppScreen.STOCK_IN_FORM) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("fab_stock_in"),
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldPrimary,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "إدخال مخزون",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = CairoFontFamily
                            )
                        }
                    }

                    // Secondary Action: Stock Out (− صرف)
                    Surface(
                        onClick = { viewModel.navigateTo(AppScreen.STOCK_OUT_FORM) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("fab_stock_out"),
                        shape = RoundedCornerShape(12.dp),
                        color = AppSurface,
                        border = BorderStroke(1.2.dp, AppBorder),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = null,
                                tint = MovementOutColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "إخراج من المخزون",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = CairoFontFamily
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 6. LOW STOCK ATTENTION SECTION (Compact Inset)
        // =========================================================================
        if (lowStockList.isNotEmpty()) {
            item {
                val itemToWatch = lowStockList.first()
                Column(
                    modifier = Modifier.testTag("dashboard_low_stock_banner"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "يحتاج إلى انتباه",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StockLow,
                        fontFamily = CairoFontFamily
                    )

                    Surface(
                        onClick = {
                            viewModel.selectProduct(itemToWatch)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, Color(0xFFF0DEBC)),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itemToWatch.displayName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontFamily = CairoFontFamily,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "الحد الأدنى: ${itemToWatch.minimumStock} نسخة",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        fontFamily = CairoFontFamily
                                    )
                                }

                                Text(
                                    text = "${itemToWatch.currentStock} نسخة",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (itemToWatch.currentStock <= 0) StockEmpty else StockLow,
                                    fontFamily = CairoFontFamily
                                )
                            }

                            // Progress Bar
                            val progress = if (itemToWatch.minimumStock > 0) {
                                (itemToWatch.currentStock.toFloat() / itemToWatch.minimumStock).coerceIn(0f, 1f)
                            } else 0f

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFF3ECE1))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxSize()
                                        .background(if (itemToWatch.currentStock <= 0) StockEmpty else StockLow)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "عرض الصنف",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    fontFamily = CairoFontFamily
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 7. RECENT MOVEMENTS ACTIVITY FEED
        // =========================================================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "آخر الحركات",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily
                )

                Text(
                    text = "عرض السجل بالكامل ←",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    fontFamily = CairoFontFamily,
                    modifier = Modifier
                        .clickable { viewModel.navigateTo(AppScreen.MOVEMENTS) }
                        .testTag("dashboard_btn_all_movements")
                )
            }
        }

        if (recentMovements.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppSurface,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد حركات مسجلة حتى الآن",
                            fontSize = 12.5.sp,
                            color = TextMuted,
                            fontFamily = CairoFontFamily
                        )
                    }
                }
            }
        } else {
            items(recentMovements.take(4)) { movement ->
                MovementItemCard(
                    movement = movement,
                    onVoucherClick = { viewModel.showVoucher(movement) }
                )
            }
        }
    }
}

// =========================================================================
// SUB-COMPONENTS: COMPACT METRIC TILE
// =========================================================================

@Composable
fun CompactMetricTile(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(14.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    fontFamily = CairoFontFamily
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(containerColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily
                )
                Text(
                    text = unit,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextMuted,
                    fontFamily = CairoFontFamily,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun StockHealthBadge(
    label: String,
    count: Int,
    percent: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label: %,d (%d%%)".format(count, percent),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            fontFamily = CairoFontFamily
        )
    }
}

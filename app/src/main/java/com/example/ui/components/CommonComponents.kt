package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.StockMovementEntity
import com.example.data.model.StockHistoryPoint
import com.example.data.model.StockStatus
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMidnight
import com.example.ui.theme.EmeraldNavy
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.GoldSoft
import com.example.ui.theme.GoldWarm
import com.example.ui.theme.MovementAdjColor
import com.example.ui.theme.MovementAdjContainer
import com.example.ui.theme.MovementInColor
import com.example.ui.theme.MovementInContainer
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.MovementOutContainer
import com.example.ui.theme.StockAvailable
import com.example.ui.theme.StockEmpty
import com.example.ui.theme.StockLow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOnDark
import com.example.ui.theme.TextOnPrimary
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Modern Geometric Detail
 */
@Composable
fun IslamicGeometricWatermark(
    modifier: Modifier = Modifier,
    tint: Color = EmeraldPrimary,
    alpha: Float = 0.05f
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.45f
        val stroke = Stroke(width = 1.dp.toPx())
        drawCircle(color = tint.copy(alpha = alpha), radius = r, center = Offset(cx, cy), style = stroke)
        drawCircle(color = tint.copy(alpha = alpha * 0.6f), radius = r * 0.6f, center = Offset(cx, cy), style = stroke)
    }
}

/**
 * Modern Minimalist Inventory Header
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    actions: @Composable () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppSurface,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Subtle Signature Vertical Spine Accent (3dp Emerald Line)
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(EmeraldPrimary, RoundedCornerShape(1.5.dp))
                        )
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontFamily = CairoFontFamily,
                                    fontSize = 16.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontFamily = CairoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(36.dp)
                                .testTag("header_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                actions = {
                    actions()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.8.dp)
                    .background(AppBorder)
            )
        }
    }
}

/**
 * Status Dot + Label (Clean, Typography First, No Heavy Badges)
 */
@Composable
fun StockStatusBadge(
    status: StockStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (status) {
        StockStatus.AVAILABLE -> Pair(StockAvailable, "متوفر")
        StockStatus.LOW_STOCK -> Pair(StockLow, "منخفض")
        StockStatus.OUT_OF_STOCK -> Pair(StockEmpty, "نفد")
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = label,
            color = dotColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CairoFontFamily
        )
    }
}

/**
 * Movement Type Semantic Label
 */
@Composable
fun MovementTypeBadge(
    type: String,
    modifier: Modifier = Modifier
) {
    val isIncoming = type.contains("وارد") || type.contains("IN")
    val isOutgoing = type.contains("صرف") || type.contains("توزيع") || type.contains("OUT")

    val (label, fg) = when {
        isIncoming -> Pair("إدخال مخزون", MovementInColor)
        isOutgoing -> Pair("إخراج مخزون", MovementOutColor)
        else -> Pair("تسوية مخزون", MovementAdjColor)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(fg, CircleShape)
        )
        Text(
            text = label,
            color = fg,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CairoFontFamily
        )
    }
}

/**
 * Modern Transaction / Activity Feed Item (Clean row, hairline separator)
 */
@Composable
fun MovementItemCard(
    movement: StockMovementEntity,
    onVoucherClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isIncoming = movement.movementType.contains("وارد") || movement.movementType.contains("IN")
    val isOutgoing = movement.movementType.contains("صرف") || movement.movementType.contains("توزيع") || movement.movementType.contains("OUT")

    val quantityColor = when {
        isIncoming -> MovementInColor
        isOutgoing -> MovementOutColor
        else -> MovementAdjColor
    }

    val quantityPrefix = when {
        isIncoming -> "+"
        isOutgoing -> "−"
        else -> if (movement.quantity > 0) "+" else ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Semantic status dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(quantityColor, CircleShape)
                )

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when {
                                isIncoming -> "إدخال"
                                isOutgoing -> "إخراج"
                                else -> "تسوية"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = CairoFontFamily
                        )

                        val detailPlace = when {
                            isIncoming -> movement.source.ifBlank { "شحنة واردة" }
                            isOutgoing -> movement.destinationName.ifBlank { "توزيع خارجي" }
                            else -> movement.reason.ifBlank { "تسوية جردية" }
                        }
                        Text(
                            text = "• $detailPlace",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = CairoFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = movement.dateFormatted,
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = CairoFontFamily
                    )
                }
            }

            // Signed Quantity & Voucher Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$quantityPrefix%,d".format(movement.quantity),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = quantityColor,
                    fontFamily = CairoFontFamily
                )

                if (isOutgoing && onVoucherClick != null) {
                    IconButton(
                        onClick = onVoucherClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "معاينة السند",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Empty State (Simple icon & text)
 */
@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontFamily = CairoFontFamily
        )
        Text(
            text = description,
            fontSize = 12.sp,
            color = TextSecondary,
            fontFamily = CairoFontFamily,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Text(actionText, fontSize = 12.sp, fontFamily = CairoFontFamily, color = EmeraldPrimary)
            }
        }
    }
}

/**
 * Clean Line Chart for Stock Evolution
 */
@Composable
fun StockEvolutionChart(
    points: List<StockHistoryPoint>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(AppSurface, RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, AppBorder), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("لا توجد بيانات تاريخية كافية للرسم البياني", color = TextMuted, fontSize = 12.sp, fontFamily = CairoFontFamily)
        }
        return
    }

    val maxVal = points.maxOfOrNull { it.stockAfter }?.toFloat()?.coerceAtLeast(10f) ?: 100f
    val minVal = points.minOfOrNull { it.stockAfter }?.toFloat()?.coerceAtLeast(0f) ?: 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(12.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            val w = size.width
            val h = size.height
            val range = (maxVal - minVal).coerceAtLeast(1f)
            val stepX = if (points.size > 1) w / (points.size - 1) else w

            val path = Path()
            points.forEachIndexed { index, pt ->
                val x = index * stepX
                val y = h - ((pt.stockAfter - minVal) / range) * (h * 0.8f) - (h * 0.1f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = EmeraldPrimary,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw points
            points.forEachIndexed { index, pt ->
                val x = index * stepX
                val y = h - ((pt.stockAfter - minVal) / range) * (h * 0.8f) - (h * 0.1f)
                drawCircle(color = AppSurface, radius = 4.dp.toPx(), center = Offset(x, y))
                drawCircle(color = EmeraldPrimary, radius = 2.5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

/**
 * Clean Distribution Voucher Dialog
 */
@Composable
fun DistributionVoucherDialog(
    movement: StockMovementEntity,
    stockList: List<com.example.data.model.CalculatedProductStock>,
    orgName: String,
    deptName: String,
    onDismiss: () -> Unit,
    onExportPdf: () -> Unit
) {
    val product = stockList.find { it.productId == movement.productId }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp)),
            color = AppSurface,
            border = BorderStroke(1.dp, AppBorder),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "سند تسليم وتوزيع مصاحف",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = CairoFontFamily
                        )
                        Text(
                            text = "رقم السند: #${movement.referenceNumber.ifBlank { movement.id.toString() }}",
                            fontSize = 11.5.sp,
                            color = TextSecondary,
                            fontFamily = CairoFontFamily
                        )
                    }

                    MovementTypeBadge(type = movement.movementType)
                }

                Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(AppBorder))

                // Organization & Date
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(orgName, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, fontFamily = CairoFontFamily)
                    Text(deptName, fontSize = 11.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                    Text("التاريخ: ${movement.dateFormatted}", fontSize = 11.sp, color = TextMuted, fontFamily = CairoFontFamily)
                }

                // Delivery Info Block
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = AppBackground,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الجهة المستلمة:", fontSize = 12.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                            Text(movement.destinationName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = CairoFontFamily)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("نوع الجهة:", fontSize = 12.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                            Text(movement.destinationType, fontSize = 12.sp, color = TextPrimary, fontFamily = CairoFontFamily)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الصنف الموزع:", fontSize = 12.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                            Text(product?.displayName ?: "المصحف الشريف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = CairoFontFamily)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الكمية المسلمة:", fontSize = 12.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                            Text("%,d نسخة".format(movement.quantity), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldPrimary, fontFamily = CairoFontFamily)
                        }
                        if (movement.packageCount > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("عدد الطرود:", fontSize = 12.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                                Text("${movement.packageCount} طرد", fontSize = 12.sp, color = TextPrimary, fontFamily = CairoFontFamily)
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppBorder)
                    ) {
                        Text("إغلاق", fontSize = 13.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                    }

                    Button(
                        onClick = onExportPdf,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("استخراج PDF", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

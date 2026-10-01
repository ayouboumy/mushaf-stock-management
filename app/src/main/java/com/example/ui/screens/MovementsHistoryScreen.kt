package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.StockMovementEntity
import com.example.ui.StockViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MovementTypeBadge
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MovementAdjColor
import com.example.ui.theme.MovementInColor
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.StockEmpty
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MovementsHistoryScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val movements by viewModel.filteredMovements.collectAsStateWithLifecycle()
    val searchQuery by viewModel.movementSearchQuery.collectAsStateWithLifecycle()
    val filterType by viewModel.movementFilterType.collectAsStateWithLifecycle()

    var movementToReverse by remember { mutableStateOf<StockMovementEntity?>(null) }
    var movementToDelete by remember { mutableStateOf<StockMovementEntity?>(null) }

    val filterOptions = listOf(
        Pair("ALL", "الكل"),
        Pair("STOCK_IN", "الوارد"),
        Pair("STOCK_OUT", "الموزع"),
        Pair("ADJUSTMENT", "التسويات")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.movementSearchQuery.value = it },
            placeholder = { Text("بحث عن وجهة، مسؤول، مرجع...", fontSize = 13.sp, fontFamily = CairoFontFamily, color = TextMuted) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { viewModel.movementSearchQuery.value = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "مسح", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("movements_search_input"),
            shape = RoundedCornerShape(10.dp),
            textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.SemiBold, color = TextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                unfocusedBorderColor = AppBorder,
                focusedContainerColor = AppSurface,
                unfocusedContainerColor = AppSurface
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter chips (Subtle horizontal tabs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for ((key, label) in filterOptions) {
                val isSelected = filterType == key
                Surface(
                    onClick = { viewModel.movementFilterType.value = key },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) EmeraldContainer else AppSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) EmeraldPrimary.copy(alpha = 0.3f) else AppBorder
                    )
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontFamily = CairoFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) EmeraldPrimary else TextSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Count Summary
        Text(
            text = "${movements.size} حركة مسجلة",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
            fontFamily = CairoFontFamily
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Movements List
        if (movements.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Default.History,
                title = "لا توجد حركات مسجلة",
                description = "ستظهر هنا كافة عمليات الإدخال والتوزيع والتسويات المستودعية.",
                actionText = if (searchQuery.isNotBlank() || filterType != "ALL") "إعادة تعيين الفلتر" else null,
                onActionClick = {
                    viewModel.movementSearchQuery.value = ""
                    viewModel.movementFilterType.value = "ALL"
                }
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(movements, key = { it.id }) { m ->
                        MovementDetailRow(
                            movement = m,
                            onVoucherClick = { viewModel.showVoucher(m) },
                            onReverseClick = { movementToReverse = m },
                            onDeleteClick = { movementToDelete = m }
                        )
                        HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Reverse Confirmation Dialog
    if (movementToReverse != null) {
        val m = movementToReverse!!
        AlertDialog(
            onDismissRequest = { movementToReverse = null },
            shape = RoundedCornerShape(14.dp),
            containerColor = AppSurface,
            title = {
                Text(
                    text = "إلغاء وعكس الحركة رقم #${m.id}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            },
            text = {
                Text(
                    text = "سيتم الحفاظ على الحركة الأصلية في سجل التدقيق وإنشاء حركة تعديل عكسية مقابلة تلقائياً لضمان سلامة المخزون.",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    fontFamily = CairoFontFamily,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reverseMovement(m.id, "إلغاء يدوي من سجل الحركات", "المشرف")
                        movementToReverse = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تأكيد الإلغاء", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { movementToReverse = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تراجع", fontFamily = CairoFontFamily, color = TextSecondary)
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (movementToDelete != null) {
        val m = movementToDelete!!
        AlertDialog(
            onDismissRequest = { movementToDelete = null },
            shape = RoundedCornerShape(14.dp),
            containerColor = AppSurface,
            title = {
                Text(
                    text = "حذف الحركة رقم #${m.id}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = StockEmpty
                    )
                )
            },
            text = {
                Text(
                    text = "حذف هذه الحركة سيؤثر على حساب المخزون الحالي. هل أنت متأكد من الحذف؟",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    fontFamily = CairoFontFamily,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.softDeleteMovement(m.id)
                        movementToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StockEmpty),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حذف الحركة", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { movementToDelete = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFontFamily, color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Clean Movement Row (Transaction Style)
 */
@Composable
fun MovementDetailRow(
    movement: StockMovementEntity,
    onVoucherClick: () -> Unit,
    onReverseClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val isIncoming = movement.movementType.contains("وارد") || movement.movementType.contains("IN")
    val isOutgoing = movement.movementType.contains("صرف") || movement.movementType.contains("توزيع") || movement.movementType.contains("OUT")

    val accentColor = when {
        isIncoming -> MovementInColor
        isOutgoing -> MovementOutColor
        else -> MovementAdjColor
    }

    val quantityPrefix = when {
        isIncoming -> "+"
        isOutgoing -> "−"
        else -> if (movement.quantity > 0) "+" else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (movement.isReversed) Color.LightGray else accentColor, CircleShape)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    if (movement.isReversed) {
                        Text(
                            text = "(ملغاة)",
                            fontSize = 10.5.sp,
                            color = TextMuted,
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "$quantityPrefix%,d".format(movement.quantity),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (movement.isReversed) Color.LightGray else accentColor,
                    fontFamily = CairoFontFamily
                )
            }

            val labelPlace = when (movement.movementType) {
                "STOCK_IN" -> "المصدر: ${movement.source.ifBlank { "شحنة واردة" }}"
                "STOCK_OUT" -> "الوجهة: ${movement.destinationName} (${movement.destinationType})"
                else -> "سبب التعديل: ${movement.reason}"
            }
            Text(
                text = labelPlace,
                fontSize = 12.sp,
                fontFamily = CairoFontFamily,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${movement.dateFormatted} • ${movement.responsiblePerson.ifBlank { "المستودع" }}",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = CairoFontFamily
                )

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "خيارات", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(AppSurface)
                    ) {
                        if (movement.movementType == "STOCK_OUT") {
                            DropdownMenuItem(
                                text = { Text("معاينة السند", fontSize = 12.5.sp, fontFamily = CairoFontFamily, color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    onVoucherClick()
                                }
                            )
                        }
                        if (!movement.isReversed) {
                            DropdownMenuItem(
                                text = { Text("إلغاء الحركة (عكس)", fontSize = 12.5.sp, fontFamily = CairoFontFamily, color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Undo, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    onReverseClick()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("حذف الحركة", fontSize = 12.5.sp, fontFamily = CairoFontFamily, color = StockEmpty) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = StockEmpty, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.StockViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MovementAdjColor
import com.example.ui.theme.MovementInColor
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val stockList by viewModel.calculatedStockList.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var selectedItemIndex by remember { mutableStateOf(0) }
    var expandedDropdown by remember { mutableStateOf(false) }
    val currentSelectedItem = stockList.getOrNull(selectedItemIndex)

    var isPositive by remember { mutableStateOf(false) } // False = Deficit (-), True = Surplus (+)
    var quantityStr by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf("جرد فعلي - فروقات جرد") }
    var responsiblePerson by remember(currentUser.fullName) { mutableStateOf(currentUser.fullName) }
    var notesStr by remember { mutableStateOf("") }

    val rawQty = quantityStr.toIntOrNull() ?: 0
    val signedQty = if (isPositive) rawQty else -rawQty

    val currentStock = currentSelectedItem?.currentStock ?: 0
    val stockAfterAdj = currentStock + signedQty

    val reasons = listOf(
        "جرد فعلي - فروقات جرد",
        "نسخ تالفة أثناء التخزين والنقل",
        "نسخ مفقودة",
        "فائض مكتشف أثناء الجرد",
        "تصحيح إداري لخطأ قيد سابق",
        "أخرى"
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedContainerColor = AppSurface,
        unfocusedContainerColor = AppSurface,
        focusedBorderColor = EmeraldPrimary,
        unfocusedBorderColor = AppBorder,
        focusedLabelColor = EmeraldPrimary,
        unfocusedLabelColor = TextSecondary,
        focusedPlaceholderColor = TextMuted,
        unfocusedPlaceholderColor = TextMuted
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        AppHeader(
            title = "تسجيل تسوية المخزون",
            subtitle = "تسوية الفروقات والتلف والجرد الدوري",
            showBackButton = true,
            onBackClick = { viewModel.navigateBack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Direction Selector: Deficit vs Surplus
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("نوع التسوية *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = { isPositive = false },
                        shape = RoundedCornerShape(8.dp),
                        color = if (!isPositive) MovementOutColor else AppSurface,
                        border = BorderStroke(1.dp, if (!isPositive) MovementOutColor else AppBorder),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "عجز / تلف (−)",
                                fontFamily = CairoFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = if (!isPositive) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isPositive) Color.White else TextSecondary
                            )
                        }
                    }

                    Surface(
                        onClick = { isPositive = true },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPositive) MovementInColor else AppSurface,
                        border = BorderStroke(1.dp, if (isPositive) MovementInColor else AppBorder),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "فائض / إضافة (+)",
                                fontFamily = CairoFontFamily,
                                fontSize = 12.5.sp,
                                fontWeight = if (isPositive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPositive) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            // Product Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("الصنف *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                ExposedDropdownMenuBox(
                    expanded = expandedDropdown,
                    onExpandedChange = { expandedDropdown = !expandedDropdown }
                ) {
                    OutlinedTextField(
                        value = currentSelectedItem?.displayName ?: "اختر الصنف",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("adj_product_dropdown"),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                        colors = textFieldColors
                    )

                    ExposedDropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        stockList.forEachIndexed { index, item ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(item.displayName, fontWeight = FontWeight.Bold, fontFamily = CairoFontFamily, color = TextPrimary, fontSize = 13.sp)
                                        Text("المتوفر: %,d %s".format(item.currentStock, item.unit), fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextMuted)
                                    }
                                },
                                onClick = {
                                    selectedItemIndex = index
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            // Quantity Input
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("كمية التسوية (نسخة) *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("0", fontFamily = CairoFontFamily) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adj_quantity_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 15.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary),
                    colors = textFieldColors,
                    singleLine = true
                )
            }

            // Reason Selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("سبب التسوية *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reasons.take(3).forEach { r ->
                        val isSelected = selectedReason == r
                        Surface(
                            onClick = { selectedReason = r },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) EmeraldPrimary else AppSurface,
                            border = BorderStroke(1.dp, if (isSelected) EmeraldPrimary else AppBorder)
                        ) {
                            Text(
                                text = r,
                                fontSize = 11.5.sp,
                                fontFamily = CairoFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Responsible Person
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("المسؤول عن التسوية", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = responsiblePerson,
                    onValueChange = { responsiblePerson = it },
                    placeholder = { Text("اسم الموظف أو لجنة الجرد", fontFamily = CairoFontFamily) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adj_responsible_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                    colors = textFieldColors,
                    singleLine = true
                )
            }

            // Notes
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("بيان وملاحظات التسوية", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = notesStr,
                    onValueChange = { notesStr = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adj_notes_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                    colors = textFieldColors
                )
            }

            // Clean Live Calculation Result
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("الرصيد الحالي", fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                        Text("%,d نسخة".format(currentStock), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = CairoFontFamily)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("قيمة التسوية", fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                        Text("%+d".format(signedQty), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MovementAdjColor, fontFamily = CairoFontFamily)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("بعد العملية", fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                        Text("%,d نسخة".format(stockAfterAdj), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldPrimary, fontFamily = CairoFontFamily)
                    }
                }
            }

            // Submit Button
            Button(
                onClick = {
                    if (currentSelectedItem != null && rawQty > 0) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val todayStr = sdf.format(Date())
                        viewModel.recordAdjustment(
                            productId = currentSelectedItem.productId,
                            variantId = currentSelectedItem.variantId,
                            quantity = signedQty,
                            packageCount = 0,
                            dateFormatted = todayStr,
                            reason = selectedReason,
                            responsiblePerson = responsiblePerson,
                            notes = notesStr
                        )
                    }
                },
                enabled = currentSelectedItem != null && rawQty > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_submit_adjustment"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تأكيد وحفظ التسوية (%+d)".format(signedQty), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

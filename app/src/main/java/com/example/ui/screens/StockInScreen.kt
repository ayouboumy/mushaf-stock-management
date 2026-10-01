package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CalendarToday
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
import com.example.ui.theme.MovementInColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockInScreen(
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

    var packageCountStr by remember { mutableStateOf("") }
    var quantityStr by remember { mutableStateOf("") }
    var dateStr by remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        mutableStateOf(sdf.format(Date()))
    }
    var sourceStr by remember { mutableStateOf("المندوبية الجهوية - مطبعة فضالة") }
    var responsiblePerson by remember(currentUser.fullName) { mutableStateOf(currentUser.fullName) }
    var referenceNumber by remember { mutableStateOf("") }
    var notesStr by remember { mutableStateOf("") }

    val pkgUnits = currentSelectedItem?.packageQuantity ?: 10
    val pkgs = packageCountStr.toIntOrNull() ?: 0
    val qty = quantityStr.toIntOrNull() ?: 0
    val currentStock = currentSelectedItem?.currentStock ?: 0
    val stockAfterIn = currentStock + qty

    fun onPackageCountChange(newVal: String) {
        packageCountStr = newVal.filter { it.isDigit() }
        val p = newVal.toIntOrNull() ?: 0
        val computedQty = p * pkgUnits
        quantityStr = if (p > 0) computedQty.toString() else ""
    }

    fun onQuantityChange(newVal: String) {
        quantityStr = newVal.filter { it.isDigit() }
        val q = newVal.toIntOrNull() ?: 0
        if (pkgUnits > 0 && q > 0) {
            val calcPkg = (q + pkgUnits - 1) / pkgUnits
            packageCountStr = calcPkg.toString()
        } else if (q == 0) {
            packageCountStr = ""
        }
    }

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
            title = "إدخال مخزون",
            subtitle = "تسجيل الشحنات المستودعية واستلام الطرود",
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
            // Product Selector Dropdown
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "المنتج *",
                    fontSize = 13.sp,
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
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
                            .testTag("stock_in_product_dropdown"),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 14.5.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary),
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
                                        Text("المتوفر: %,d • سعة الطرد: %d".format(item.currentStock, item.packageQuantity), fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextMuted)
                                    }
                                },
                                onClick = {
                                    selectedItemIndex = index
                                    expandedDropdown = false
                                    onPackageCountChange(packageCountStr)
                                }
                            )
                        }
                    }
                }
            }

            // Package & Quantity Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("عدد الطرود", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        value = packageCountStr,
                        onValueChange = { onPackageCountChange(it) },
                        placeholder = { Text("0", fontFamily = CairoFontFamily) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_in_package_input"),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 15.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary),
                        colors = textFieldColors,
                        singleLine = true
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("الكمية (نسخة) *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { onQuantityChange(it) },
                        placeholder = { Text("0", fontFamily = CairoFontFamily) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_in_quantity_input"),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 15.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = EmeraldPrimary),
                        colors = textFieldColors,
                        singleLine = true
                    )
                }
            }

            // Date & Reference
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("التاريخ *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_in_date_input"),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 13.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                        colors = textFieldColors,
                        singleLine = true
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("رقم السند / الوثيقة", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        value = referenceNumber,
                        onValueChange = { referenceNumber = it },
                        placeholder = { Text("BE-2026/042", fontFamily = CairoFontFamily) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_in_ref_input"),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 13.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                        colors = textFieldColors,
                        singleLine = true
                    )
                }
            }

            // Source
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("المصدر / المطبعة *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = sourceStr,
                    onValueChange = { sourceStr = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_in_source_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                    colors = textFieldColors,
                    singleLine = true
                )
            }

            // Responsible Person
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("المستلم / أمين المستودع", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = responsiblePerson,
                    onValueChange = { responsiblePerson = it },
                    placeholder = { Text("اسم الموظف المستلم", fontFamily = CairoFontFamily) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_in_responsible_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                    colors = textFieldColors,
                    singleLine = true
                )
            }

            // Notes
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ملاحظات إضافية", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = notesStr,
                    onValueChange = { notesStr = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_in_notes_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                    colors = textFieldColors
                )
            }

            // Clean Live Stock Calculation Row
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

                    Column(horizontalAlignment = Alignment.End) {
                        Text("بعد العملية", fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                        Text("%,d نسخة".format(stockAfterIn), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldPrimary, fontFamily = CairoFontFamily)
                    }
                }
            }

            // Submit Button
            Button(
                onClick = {
                    if (currentSelectedItem != null && qty > 0) {
                        viewModel.recordStockIn(
                            productId = currentSelectedItem.productId,
                            variantId = currentSelectedItem.variantId,
                            quantity = qty,
                            packageCount = pkgs,
                            dateFormatted = dateStr,
                            source = sourceStr,
                            responsiblePerson = responsiblePerson,
                            referenceNumber = referenceNumber,
                            notes = notesStr
                        )
                    }
                },
                enabled = currentSelectedItem != null && qty > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_submit_stock_in"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تأكيد الإدخال (+%,d نسخة)".format(qty), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

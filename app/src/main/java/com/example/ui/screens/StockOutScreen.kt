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
import androidx.compose.material.icons.filled.ReceiptLong
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.StockEmpty
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockOutScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val stockList by viewModel.calculatedStockList.collectAsStateWithLifecycle()
    val allDestinations by viewModel.allDestinations.collectAsStateWithLifecycle()
    val allowNegativeStock by viewModel.allowNegativeStock.collectAsStateWithLifecycle()
    val nextVoucher by viewModel.nextVoucherNumber.collectAsStateWithLifecycle()
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
    var destinationName by remember { mutableStateOf("") }
    var destinationType by remember { mutableStateOf("مسجد") }
    var destinationAddress by remember { mutableStateOf("") }
    var responsiblePerson by remember(currentUser.fullName) { mutableStateOf(currentUser.fullName) }
    var referenceNumber by remember { mutableStateOf("") }
    var notesStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshNextVoucherNumber()
    }

    LaunchedEffect(nextVoucher) {
        if (referenceNumber.isBlank()) {
            referenceNumber = nextVoucher
        }
    }

    val pkgUnits = currentSelectedItem?.packageQuantity ?: 10
    val pkgs = packageCountStr.toIntOrNull() ?: 0
    val qty = quantityStr.toIntOrNull() ?: 0
    val currentStock = currentSelectedItem?.currentStock ?: 0
    val stockAfterOut = currentStock - qty
    val isExceedingStock = qty > currentStock && !allowNegativeStock

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

    val destTypes = listOf("مسجد", "مؤسسة", "جمعية", "مجلس علمي", "إدارة", "حفل", "إمام", "أخرى")

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
            title = "إخراج من المخزون",
            subtitle = "تسجيل التوزيع للمساجد والمؤسسات مع السند الرسمي",
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
                        value = currentSelectedItem?.displayName ?: "اختر المنتج",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("stock_out_product_dropdown"),
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(item.displayName, fontWeight = FontWeight.Bold, fontFamily = CairoFontFamily, color = TextPrimary, fontSize = 13.sp)
                                        Text(
                                            "المتوفر: %,d".format(item.currentStock),
                                            fontSize = 12.sp,
                                            fontFamily = CairoFontFamily,
                                            color = EmeraldPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
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
                            .testTag("stock_out_package_input"),
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
                        isError = isExceedingStock,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_out_quantity_input"),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 15.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = MovementOutColor),
                        colors = textFieldColors,
                        singleLine = true
                    )
                }
            }

            if (isExceedingStock) {
                Text(
                    text = "تنبيه: الكمية المطلوبة ($qty) أكبر من المخزون المتوفر ($currentStock).",
                    color = StockEmpty,
                    fontSize = 11.5.sp,
                    fontFamily = CairoFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Destination Name & Suggestions
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("المستفيد / مكان التوزيع *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = destinationName,
                    onValueChange = { input ->
                        destinationName = input
                        val match = allDestinations.find { it.name.trim().equals(input.trim(), ignoreCase = true) }
                        if (match != null) {
                            destinationType = match.type
                            if (match.address.isNotBlank() && destinationAddress.isBlank()) {
                                destinationAddress = match.address
                            }
                        }
                    },
                    placeholder = { Text("مثال: مسجد المسيرة، مدرسة الإمام نافع...", fontFamily = CairoFontFamily) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_out_destination_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                    colors = textFieldColors,
                    singleLine = true
                )
            }

            // Destination Type Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                destTypes.take(4).forEach { t ->
                    val isSelected = destinationType == t
                    Surface(
                        onClick = { destinationType = t },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) EmeraldPrimary else AppSurface,
                        border = BorderStroke(1.dp, if (isSelected) EmeraldPrimary else AppBorder)
                    ) {
                        Text(
                            text = t,
                            fontSize = 11.5.sp,
                            fontFamily = CairoFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Destination Address (العنوان)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("العنوان (النفوذ الترابي / مقر الجهة)", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = destinationAddress,
                    onValueChange = { destinationAddress = it },
                    placeholder = { Text("مثال: إقليم الدريوش - جهة الشرق / حي المسيرة...", fontFamily = CairoFontFamily) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_out_address_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                    colors = textFieldColors,
                    singleLine = true
                )
            }

            // Date & Voucher Number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("تاريخ التوزيع *", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_out_date_input"),
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
                    Text("رقم السند", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedTextField(
                        value = referenceNumber,
                        onValueChange = { referenceNumber = it },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_out_ref_input"),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary),
                        colors = textFieldColors,
                        singleLine = true
                    )
                }
            }

            // Responsible Person
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("المستلم / ممثل الجهة", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = responsiblePerson,
                    onValueChange = { responsiblePerson = it },
                    placeholder = { Text("اسم الشخص المستلم", fontFamily = CairoFontFamily) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_out_responsible_input"),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary),
                    colors = textFieldColors,
                    singleLine = true
                )
            }

            // Notes
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ملاحظات وسند التوزيع", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = notesStr,
                    onValueChange = { notesStr = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_out_notes_input"),
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
                        Text(
                            "%,d نسخة".format(stockAfterOut),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isExceedingStock) StockEmpty else TextPrimary,
                            fontFamily = CairoFontFamily
                        )
                    }
                }
            }

            // Submit Button
            Button(
                onClick = {
                    if (currentSelectedItem != null && qty > 0 && destinationName.isNotBlank() && (!isExceedingStock || allowNegativeStock)) {
                        viewModel.recordStockOut(
                            productId = currentSelectedItem.productId,
                            variantId = currentSelectedItem.variantId,
                            quantity = qty,
                            packageCount = pkgs,
                            dateFormatted = dateStr,
                            destinationName = destinationName.trim(),
                            destinationType = destinationType,
                            address = destinationAddress.trim(),
                            responsiblePerson = responsiblePerson.trim(),
                            referenceNumber = referenceNumber.trim(),
                            notes = notesStr.trim()
                        )
                    }
                },
                enabled = currentSelectedItem != null && qty > 0 && destinationName.isNotBlank() && (!isExceedingStock || allowNegativeStock),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_submit_stock_out"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("تأكيد الإخراج (−%,d نسخة)".format(qty), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

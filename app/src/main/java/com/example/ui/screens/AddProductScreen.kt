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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.data.entity.ProductEntity
import com.example.data.entity.ProductVariantEntity
import com.example.ui.StockViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.TajawalFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddProductScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    // 1. النوع (Type / Name)
    var productType by remember { mutableStateOf("") }

    // 2. عدد الطرود (Number of packages)
    var packageCountStr by remember { mutableStateOf("") }

    // 3. عدد المصاحف في كل طرد (Copies per package)
    var copiesPerPackageStr by remember { mutableStateOf("10") }

    // 4. إجمالي عدد المصاحف (Total copies / pieces)
    var directTotalStr by remember { mutableStateOf("") }

    // 5. اللغة (Language)
    var languageStr by remember { mutableStateOf("العربية") }

    // 6. الحد الأدنى (Minimum Stock Alert)
    var minStockStr by remember { mutableStateOf("50") }

    // 7. التاريخ (Date)
    val defaultDate = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date())
    }
    var dateStr by remember { mutableStateOf(defaultDate) }

    // 8. ملاحظات (Notes)
    var notesStr by remember { mutableStateOf("") }

    // Dynamic Bidirectional Stock Calculations
    val pkgs = packageCountStr.toIntOrNull() ?: 0
    val perPkg = (copiesPerPackageStr.toIntOrNull() ?: 10).coerceAtLeast(1)
    val directTotal = directTotalStr.toIntOrNull()

    // The effective total number of pieces / copies
    val totalMushafs = directTotal ?: (pkgs * perPkg)

    fun onPackageCountChanged(newVal: String) {
        val filtered = newVal.filter { it.isDigit() }
        packageCountStr = filtered
        val p = filtered.toIntOrNull() ?: 0
        val computedTotal = p * perPkg
        directTotalStr = if (p > 0) computedTotal.toString() else ""
    }

    fun onCopiesPerPackageChanged(newVal: String) {
        val filtered = newVal.filter { it.isDigit() }
        copiesPerPackageStr = filtered
        val pp = (filtered.toIntOrNull() ?: 10).coerceAtLeast(1)
        if (pkgs > 0) {
            directTotalStr = (pkgs * pp).toString()
        }
    }

    fun onDirectTotalChanged(newVal: String) {
        val filtered = newVal.filter { it.isDigit() }
        directTotalStr = filtered
        val t = filtered.toIntOrNull() ?: 0
        if (t > 0 && perPkg > 0) {
            val calcPkgs = (t + perPkg - 1) / perPkg
            packageCountStr = calcPkgs.toString()
        } else if (t == 0) {
            packageCountStr = ""
        }
    }

    val standardTypes = listOf(
        "المصحف المحمدي",
        "المصحف المجزأ",
        "الهدايا",
        "جزء عم",
        "ربع يس",
        "طبعة برايل",
        "مصحف مترجم"
    )

    val standardLanguages = listOf(
        "العربية",
        "الفرنسية",
        "الإنجليزية",
        "الإسبانية",
        "الأمازيغية"
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
            title = "إضافة صنف وتحديد الأرقام",
            subtitle = "المعادلة: عدد المصاحف = عدد الطرود × المصاحف في كل طرد",
            showBackButton = true,
            onBackClick = { viewModel.navigateBack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // 1. LIVE FORMULA SUMMARY CARD
            // =========================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "معادلة احتساب إجمالي عدد النسخ / المصاحف",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$pkgs طرد",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "×",
                            color = TextMuted,
                            fontSize = 16.sp,
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$perPkg نسخة/طرد",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "=",
                            color = TextMuted,
                            fontSize = 16.sp,
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "%,d نسخة".format(totalMushafs),
                            color = EmeraldPrimary,
                            fontSize = 19.sp,
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldContainer
                    ) {
                        Text(
                            text = "الرصيد الافتتاحي الفعلي: %,d قطعة/نسخة".format(totalMushafs),
                            color = EmeraldPrimary,
                            fontSize = 11.5.sp,
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 2. MAIN FORM INPUTS
            // =========================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. النوع (Type / Name)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "1. النوع (اسم ونوع المصحف) *",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = TajawalFontFamily,
                            color = TextPrimary
                        )

                        OutlinedTextField(
                            value = productType,
                            onValueChange = { productType = it },
                            placeholder = { Text("مثال: المصحف المحمدي، المصحف المجزأ، جزء عم...", fontFamily = TajawalFontFamily, color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_prod_name_ar"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = TextStyle(fontSize = 14.sp, fontFamily = TajawalFontFamily, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                            colors = textFieldColors,
                            singleLine = true
                        )

                        // Quick Type Selector Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            standardTypes.take(4).forEach { t ->
                                val isSelected = productType == t
                                Surface(
                                    onClick = { productType = t },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) EmeraldContainer else Color(0xFFF1F5F3),
                                    border = BorderStroke(1.dp, if (isSelected) EmeraldPrimary else Color(0xFFD4E0D9))
                                ) {
                                    Text(
                                        text = t,
                                        fontSize = 11.sp,
                                        fontFamily = TajawalFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) EmeraldPrimary else TextPrimary,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. عدد الطرود & 3. سعة الطرد
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "2. عدد الطرود *",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = TajawalFontFamily,
                                color = TextPrimary
                            )
                            OutlinedTextField(
                                value = packageCountStr,
                                onValueChange = { onPackageCountChanged(it) },
                                placeholder = { Text("مثال: 50", fontFamily = TajawalFontFamily, color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 15.sp, fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_prod_package_count"),
                                shape = RoundedCornerShape(10.dp),
                                colors = textFieldColors,
                                singleLine = true
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "3. المصاحف/طرد *",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = TajawalFontFamily,
                                color = TextPrimary
                            )
                            OutlinedTextField(
                                value = copiesPerPackageStr,
                                onValueChange = { onCopiesPerPackageChanged(it) },
                                placeholder = { Text("10", fontFamily = TajawalFontFamily, color = TextMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(fontSize = 15.sp, fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_prod_copies_per_package"),
                                shape = RoundedCornerShape(10.dp),
                                colors = textFieldColors,
                                singleLine = true
                            )
                        }
                    }

                    // 4. إجمالي عدد القطع / المصاحف
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "4. إجمالي عدد النسخ (محسوب تلقائياً) *",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = TajawalFontFamily,
                                color = TextPrimary
                            )
                            if (totalMushafs > 0) {
                                Text(
                                    text = "%,d نسخة".format(totalMushafs),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    fontFamily = TajawalFontFamily,
                                    color = EmeraldPrimary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = directTotalStr,
                            onValueChange = { onDirectTotalChanged(it) },
                            placeholder = { Text("%,d".format(totalMushafs.coerceAtLeast(0)), fontFamily = TajawalFontFamily, color = TextMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontSize = 15.sp, fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, color = EmeraldPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_prod_total_pieces"),
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            singleLine = true
                        )
                    }

                    // 5. اللغة (Language)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "5. اللغة *",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = TajawalFontFamily,
                            color = TextPrimary
                        )

                        OutlinedTextField(
                            value = languageStr,
                            onValueChange = { languageStr = it },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(17.dp))
                            },
                            textStyle = TextStyle(fontSize = 14.sp, fontFamily = TajawalFontFamily, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_prod_language"),
                            shape = RoundedCornerShape(10.dp),
                            colors = textFieldColors,
                            singleLine = true
                        )

                        // Language chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            standardLanguages.forEach { lang ->
                                val isSelected = languageStr == lang
                                Surface(
                                    onClick = { languageStr = lang },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) EmeraldPrimary else Color(0xFFF1F5F3),
                                    border = BorderStroke(1.dp, if (isSelected) EmeraldPrimary else Color(0xFFD4E0D9))
                                ) {
                                    Text(
                                        text = lang,
                                        fontSize = 11.sp,
                                        fontFamily = TajawalFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 6. الحد الأدنى & 7. التاريخ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "6. الحد الأدنى للتنبيه *",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = TajawalFontFamily,
                                color = TextPrimary
                            )
                            OutlinedTextField(
                                value = minStockStr,
                                onValueChange = { minStockStr = it.filter { char -> char.isDigit() } },
                                placeholder = { Text("50", fontFamily = TajawalFontFamily) },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = GoldSecondary, modifier = Modifier.size(16.dp))
                                },
                                textStyle = TextStyle(fontSize = 14.5.sp, fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_prod_min_stock"),
                                shape = RoundedCornerShape(10.dp),
                                colors = textFieldColors,
                                singleLine = true
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "7. التاريخ *",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = TajawalFontFamily,
                                color = TextPrimary
                            )
                            OutlinedTextField(
                                value = dateStr,
                                onValueChange = { dateStr = it },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(15.dp))
                                },
                                textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = TajawalFontFamily, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_prod_date"),
                                shape = RoundedCornerShape(10.dp),
                                colors = textFieldColors,
                                singleLine = true
                            )
                        }
                    }

                    // 8. ملاحظات (Notes)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = "8. ملاحظات",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = TajawalFontFamily,
                            color = TextPrimary
                        )

                        OutlinedTextField(
                            value = notesStr,
                            onValueChange = { notesStr = it },
                            placeholder = { Text("أدخل أي بيان، مواصفات الطبعة، أو ملاحظات إضافية...", fontFamily = TajawalFontFamily, color = TextMuted) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Notes, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            },
                            textStyle = TextStyle(fontSize = 13.5.sp, fontFamily = TajawalFontFamily, color = TextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_prod_notes"),
                            shape = RoundedCornerShape(10.dp),
                            minLines = 2,
                            colors = textFieldColors
                        )
                    }
                }
            }

            // Save & Submit Button
            Button(
                onClick = {
                    if (productType.isNotBlank()) {
                        val minStock = minStockStr.toIntOrNull() ?: 50
                        val pkgUnits = perPkg.coerceAtLeast(1)

                        val categoryDerived = when {
                            productType.contains("أجزاء") || productType.contains("جزء") || productType.contains("يس") -> "أجزاء"
                            languageStr != "العربية" || productType.contains("مترجم") -> "مترجم"
                            productType.contains("هدايا") || productType.contains("خاص") || productType.contains("برايل") -> "خاص"
                            else -> "مصحف شريف"
                        }

                        val product = ProductEntity(
                            nameArabic = productType.trim(),
                            category = categoryDerived,
                            language = languageStr.trim(),
                            formatType = productType.trim(),
                            unit = "نسخة",
                            packageQuantity = pkgUnits,
                            minimumStock = minStock,
                            initialStock = totalMushafs, // Precise calculated total number of pieces
                            notes = notesStr.trim()
                        )

                        val variants = if (languageStr != "العربية") {
                            listOf(
                                ProductVariantEntity(
                                    productId = 0,
                                    nameArabic = languageStr.trim(),
                                    nameFrench = languageStr.trim(),
                                    initialStock = totalMushafs,
                                    minimumStock = minStock,
                                    packageQuantity = pkgUnits,
                                    notes = notesStr.trim()
                                )
                            )
                        } else {
                            emptyList()
                        }

                        viewModel.createProduct(product, variants)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_save_new_product"),
                enabled = productType.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "حفظ وإدراج الصنف (%,d نسخة)".format(totalMushafs),
                    fontSize = 14.5.sp,
                    fontFamily = TajawalFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

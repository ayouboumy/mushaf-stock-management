package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.StockViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AmiriFontFamily
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.MovementInColor
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.StockEmpty
import com.example.ui.theme.StockLow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.ExcelImportHelper
import com.example.utils.ImportRowStatus
import com.example.utils.ValidatedImportRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelImportWizardScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (viewModel.importCurrentStep.value > 1 && viewModel.importCurrentStep.value < 4) {
            viewModel.importCurrentStep.value--
        } else {
            viewModel.navigateBack()
        }
    }

    val currentStep by viewModel.importCurrentStep.collectAsStateWithLifecycle()
    val selectedSheet by viewModel.importSelectedSheet.collectAsStateWithLifecycle()
    val validatedRows by viewModel.importValidatedRows.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val summaryResult by viewModel.importResultSummary.collectAsStateWithLifecycle()

    var productDropdownExpanded by remember { mutableStateOf(false) }
    var selectedProductIndex by remember { mutableStateOf(0) }
    val currentSelectedProduct = allProducts.getOrNull(selectedProductIndex)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        AppHeader(
            title = "معالج استيراد بيانات الإكسل",
            subtitle = "مطابقة واستيراد سجلات توزيع المصحف المحمدي",
            showBackButton = true,
            onBackClick = {
                if (currentStep > 1 && currentStep < 4) {
                    viewModel.importCurrentStep.value--
                } else {
                    viewModel.navigateBack()
                }
            }
        )

        // Wizard Stepper Indicators
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            color = AppSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WizardStepIndicator(1, "الورقة", currentStep >= 1, isCurrent = currentStep == 1)
                WizardStepDivider(currentStep >= 2)
                WizardStepIndicator(2, "المطابقة", currentStep >= 2, isCurrent = currentStep == 2)
                WizardStepDivider(currentStep >= 3)
                WizardStepIndicator(3, "المعاينة", currentStep >= 3, isCurrent = currentStep == 3)
                WizardStepDivider(currentStep >= 4)
                WizardStepIndicator(4, "التنفيذ", currentStep >= 4, isCurrent = currentStep == 4)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (currentStep) {
                // STEP 1: Select sheet from workbook
                1 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "الخطوة 1: اختيار الورقة من مصنف 'توزيع المصحف 2026.xlsx'",
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary,
                                        fontSize = 13.5.sp,
                                        fontFamily = CairoFontFamily
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "اكتشف النظام أوراق المصنف الأصلي بوزارة الأوقاف. يرجى اختيار الورقة المراد استيراد بياناتها:",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontFamily = CairoFontFamily
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                for (sheet in ExcelImportHelper.WORKBOOK_SHEETS) {
                                    val isSelected = selectedSheet == sheet
                                    Surface(
                                        onClick = { viewModel.loadSampleSheetForImport(sheet) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) EmeraldPrimary.copy(alpha = 0.08f) else AppBackground,
                                        border = BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) EmeraldPrimary else AppBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = if (isSelected) EmeraldPrimary else TextMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = sheet,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) EmeraldPrimary else TextPrimary,
                                                    fontFamily = CairoFontFamily
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = EmeraldPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = { viewModel.importCurrentStep.value = 2 },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("import_wizard_step1_next"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Text("المتابعة لمطابقة الأعمدة", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                // STEP 2: Map columns & Target Product
                2 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "الخطوة 2: ربط الأعمدة وتحديد صنف المستودع المستهدف",
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    fontSize = 13.5.sp,
                                    fontFamily = CairoFontFamily
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    "الصنف المرتبط في المستودع:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFontFamily,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                ExposedDropdownMenuBox(
                                    expanded = productDropdownExpanded,
                                    onExpandedChange = { productDropdownExpanded = !productDropdownExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = currentSelectedProduct?.nameArabic ?: "اختر الصنف",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmeraldPrimary,
                                            unfocusedBorderColor = AppBorder
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontFamily = CairoFontFamily,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary
                                        )
                                    )
                                    ExposedDropdownMenu(
                                        expanded = productDropdownExpanded,
                                        onDismissRequest = { productDropdownExpanded = false }
                                    ) {
                                        allProducts.forEachIndexed { idx, p ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        p.nameArabic,
                                                        fontFamily = CairoFontFamily,
                                                        fontSize = 12.5.sp
                                                    )
                                                },
                                                onClick = {
                                                    selectedProductIndex = idx
                                                    viewModel.importTargetProductId.value = p.id
                                                    productDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    "مطابقة أعمدة الإكسل الذكية:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CairoFontFamily,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ColumnMappingRow("مكان التوزيع (إكسل)", "← وجهة التوزيع (المستفيد)")
                                    ColumnMappingRow("التاريخ (إكسل)", "← تاريخ الحركة الرسمي")
                                    ColumnMappingRow("الكمية (إكسل)", "← الكمية المسلمة الفعلية")
                                    ColumnMappingRow("الطرود (إكسل)", "← عدد الطرود والكراتين")
                                    ColumnMappingRow("توقيع المكلف بالتوزيع", "← المسؤول الإداري المباشر")
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = { viewModel.importCurrentStep.value = 3 },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("import_wizard_step2_next"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Text("معاينة السجلات والتحقق", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                // STEP 3: Preview with Validation
                3 -> {
                    val validCount = validatedRows.count { it.status == ImportRowStatus.VALID }
                    val incompleteCount = validatedRows.count { it.status == ImportRowStatus.INCOMPLETE }
                    val invalidCount = validatedRows.count { it.status == ImportRowStatus.INVALID }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "الخطوة 3: نتائج التحقق من السجلات (${validatedRows.size} صف)",
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    fontSize = 13.5.sp,
                                    fontFamily = CairoFontFamily
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ValidationStatBadge("جاهز: $validCount", Color(0xFF2E7D32), Modifier.weight(1f))
                                    ValidationStatBadge("ناقص: $incompleteCount", Color(0xFFE65100), Modifier.weight(1f))
                                    ValidationStatBadge("مرفوض: $invalidCount", Color(0xFFC62828), Modifier.weight(1f))
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "وفقاً للنظام: السجلات الناقصة لا تُحذف بل تُدرج مع وسم 'بيانات ناقصة' لضمان الدقة الأرشيفية.",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    fontFamily = CairoFontFamily
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { viewModel.executeImport() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("import_wizard_step3_execute"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تأكيد واستيراد السجلات الصالحة", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Render rows preview
                    items(validatedRows, key = { it.rowIndex }) { row ->
                        ImportRowPreviewCard(row = row)
                    }
                }

                // STEP 4: Results
                4 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = EmeraldPrimary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = EmeraldPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "اكتملت عملية الاستيراد بنجاح!",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontFamily = CairoFontFamily
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = summaryResult ?: "تم تسجيل وتحديث المخزون وسجلات التوزيع بنجاح.",
                                    fontSize = 12.5.sp,
                                    color = TextSecondary,
                                    fontFamily = CairoFontFamily
                                )

                                Spacer(modifier = Modifier.height(22.dp))

                                Button(
                                    onClick = { viewModel.navigateBack() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("العودة إلى لوحة التحكم", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnMappingRow(sourceCol: String, targetField: String) {
    Surface(
        color = AppBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AppBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(sourceCol, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary, fontFamily = CairoFontFamily)
            Text(targetField, fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
        }
    }
}

@Composable
private fun ValidationStatBadge(text: String, color: Color, modifier: Modifier) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            maxLines = 1,
            fontFamily = CairoFontFamily
        )
    }
}

@Composable
private fun ImportRowPreviewCard(row: ValidatedImportRow) {
    val (statusBg, statusBorder, statusText, statusLabel) = when (row.status) {
        ImportRowStatus.VALID -> Quadruple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF2E7D32), "جاهز 🟢")
        ImportRowStatus.INCOMPLETE -> Quadruple(Color(0xFFFFF3E0), StockLow, StockLow, "بيانات ناقصة 🟠")
        ImportRowStatus.INVALID -> Quadruple(Color(0xFFFFEBEE), StockEmpty, StockEmpty, "غير صالح 🔴")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "صف #${row.rowIndex}: ${row.parsedDestination}",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily
                )
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusBorder.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = statusLabel,
                        color = statusText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontFamily = CairoFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "الكمية: %,d (%d طرد)".format(row.parsedQuantity, row.parsedPackages),
                    fontSize = 11.5.sp,
                    color = TextSecondary,
                    fontFamily = CairoFontFamily
                )
                Text(
                    "التاريخ: ${row.parsedDateFormatted}",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    fontFamily = CairoFontFamily
                )
            }

            if (row.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                for (issue in row.issues) {
                    Text(
                        text = "• $issue",
                        fontSize = 10.5.sp,
                        color = statusText,
                        fontFamily = CairoFontFamily
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun WizardStepIndicator(step: Int, label: String, isActive: Boolean, isCurrent: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    if (isCurrent) GoldPrimary else if (isActive) EmeraldPrimary else Color.LightGray.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$step",
                color = if (isCurrent) EmeraldDark else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CairoFontFamily
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isCurrent) GoldSecondary else if (isActive) EmeraldPrimary else TextMuted,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            fontFamily = CairoFontFamily
        )
    }
}

@Composable
private fun WizardStepDivider(isActive: Boolean) {
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(2.dp)
            .background(if (isActive) EmeraldPrimary else Color.LightGray.copy(alpha = 0.4f))
    )
}

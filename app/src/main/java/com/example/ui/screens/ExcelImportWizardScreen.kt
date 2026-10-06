package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.StockViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.MovementInColor
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.StockEmpty
import com.example.ui.theme.StockLow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.utils.ImportRowStatus
import com.example.utils.ValidatedImportRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcelImportWizardScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    BackHandler {
        if (viewModel.importCurrentStep.value > 1 && viewModel.importCurrentStep.value < 4) {
            viewModel.importCurrentStep.value--
        } else {
            viewModel.navigateBack()
        }
    }

    val currentStep by viewModel.importCurrentStep.collectAsStateWithLifecycle()
    val validatedRows by viewModel.importValidatedRows.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val summaryResult by viewModel.importResultSummary.collectAsStateWithLifecycle()
    val pastedText by viewModel.importPastedText.collectAsStateWithLifecycle()

    var productDropdownExpanded by remember { mutableStateOf(false) }
    var selectedProductIndex by remember { mutableStateOf(0) }
    val currentSelectedProduct = allProducts.getOrNull(selectedProductIndex)

    var inputMethodTab by remember { mutableStateOf(0) } // 0: Pick File, 1: Paste Text

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFileFromUri(context, uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        AppHeader(
            title = "معالج استيراد بيانات الإكسل",
            subtitle = "تنزيل النموذج الرسمي واستيراد شحنات وتوزيع المصاحف",
            showBackButton = true,
            onBackClick = {
                if (currentStep > 1 && currentStep < 4) {
                    viewModel.importCurrentStep.value--
                } else {
                    viewModel.navigateBack()
                }
            }
        )

        // Stepper Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
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
                WizardStepIndicator(1, "النموذج والملف", currentStep >= 1, isCurrent = currentStep == 1)
                WizardStepDivider(currentStep >= 2)
                WizardStepIndicator(2, "مطابقة الأصناف", currentStep >= 2, isCurrent = currentStep == 2)
                WizardStepDivider(currentStep >= 3)
                WizardStepIndicator(3, "المعاينة والتحقق", currentStep >= 3, isCurrent = currentStep == 3)
                WizardStepDivider(currentStep >= 4)
                WizardStepIndicator(4, "النتائج", currentStep >= 4, isCurrent = currentStep == 4)
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
                // STEP 1: Download Model & Pick / Paste File
                1 -> {
                    // Card 1: Download Official Excel Template
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            border = BorderStroke(1.2.dp, EmeraldPrimary.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "1. تحميل نموذج الإكسل الرسمي المعتمد",
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary,
                                        fontSize = 13.5.sp,
                                        fontFamily = CairoFontFamily
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "قم بتحميل النموذج الرسمي (.csv) المتوافق مع Excel و Google Sheets، يحتوي على أعمدة منظمة لشحنات التوريد (وارد) وتوزيع المصاحف على المساجد (توزيع). يمكنك تعبئته وحفظه ثم رفعه هنا.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontFamily = CairoFontFamily,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { viewModel.downloadExcelTemplate(context) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("download_excel_model_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تحميل نموذج الإكسل الفارغ (.csv)", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    // Card 2: Upload File or Paste Data
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = TextPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "2. استيراد الملف بعد تعبئته",
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 13.5.sp,
                                        fontFamily = CairoFontFamily
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Tabs: Pick File vs Paste
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        onClick = { inputMethodTab = 0 },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (inputMethodTab == 0) EmeraldContainer else AppBackground,
                                        border = BorderStroke(1.dp, if (inputMethodTab == 0) EmeraldPrimary else AppBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(imageVector = Icons.Default.UploadFile, contentDescription = null, tint = if (inputMethodTab == 0) EmeraldPrimary else TextSecondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("اختيار ملف من الهاتف", fontSize = 12.sp, fontFamily = CairoFontFamily, fontWeight = if (inputMethodTab == 0) FontWeight.Bold else FontWeight.Medium, color = if (inputMethodTab == 0) EmeraldPrimary else TextSecondary)
                                        }
                                    }

                                    Surface(
                                        onClick = { inputMethodTab = 1 },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (inputMethodTab == 1) EmeraldContainer else AppBackground,
                                        border = BorderStroke(1.dp, if (inputMethodTab == 1) EmeraldPrimary else AppBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, tint = if (inputMethodTab == 1) EmeraldPrimary else TextSecondary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("لصق نص الجدول", fontSize = 12.sp, fontFamily = CairoFontFamily, fontWeight = if (inputMethodTab == 1) FontWeight.Bold else FontWeight.Medium, color = if (inputMethodTab == 1) EmeraldPrimary else TextSecondary)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                if (inputMethodTab == 0) {
                                    // File Picker Button
                                    Button(
                                        onClick = { filePickerLauncher.launch("*/*") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("select_file_button"),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, EmeraldPrimary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("اختيار ملف Excel / CSV من الجهاز", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = EmeraldPrimary, fontSize = 13.sp)
                                    }
                                } else {
                                    // Paste Text Field
                                    OutlinedTextField(
                                        value = pastedText,
                                        onValueChange = { viewModel.importPastedText.value = it },
                                        placeholder = { Text("الصق هنا صفوف الجدول المنسوخة من إكسل...", fontSize = 12.sp, fontFamily = CairoFontFamily) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .testTag("paste_import_text_field"),
                                        shape = RoundedCornerShape(10.dp),
                                        textStyle = TextStyle(fontSize = 12.5.sp, fontFamily = CairoFontFamily),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmeraldPrimary,
                                            unfocusedBorderColor = AppBorder,
                                            focusedContainerColor = AppBackground,
                                            unfocusedContainerColor = AppBackground
                                        )
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { viewModel.parsePastedImportText(pastedText) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("تحليل ومعاينة النص الملصوق", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                    }
                                }

                                if (validatedRows.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Surface(
                                        color = EmeraldContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = "تم العثور على ${validatedRows.size} سطر من البيانات جاهزة للمراجعة.",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldPrimary,
                                                fontFamily = CairoFontFamily
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = { viewModel.importCurrentStep.value = 2 },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .testTag("import_wizard_step1_next"),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("المتابعة لمطابقة الأصناف والمعاينة", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // STEP 2: Map columns & Target Product Fallback
                2 -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "الخطوة 2: ربط الأصناف وتحديد الصنف الاحتياطي",
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    fontSize = 13.5.sp,
                                    fontFamily = CairoFontFamily
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "يقوم النظام بقراءة اسم الصنف من كل سطر في الإكسل تلقائياً (مثل المصحف المحمدي، المصحف المجزأ، إلخ)، وإن لم يكن مسجلاً في المستودع سيتم إنشاؤه ومزامنته سحابياً فوراً.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    fontFamily = CairoFontFamily,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    "الصنف الافتراضي في حال كان حقل الصنف فارغاً:",
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
                                        value = currentSelectedProduct?.nameArabic ?: "اختر الصنف الافتراضي",
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = productDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = EmeraldPrimary,
                                            unfocusedBorderColor = AppBorder
                                        ),
                                        textStyle = TextStyle(
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

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = { viewModel.importCurrentStep.value = 3 },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("import_wizard_step2_next"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("معاينة السجلات والتحقق", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
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
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
                            border = BorderStroke(1.dp, CardBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "الخطوة 3: نتائج التحقق من السجلات (${validatedRows.size} سطر)",
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

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "• سيتم تسجيل حركات الوارد وحركات التوزيع وتحديث أرصدة المخزون تلقائياً.\n• السجلات الناقصة يتم تصحيحها بقيم افتراضية لضمان التوثيق الأرشيفي.",
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    fontFamily = CairoFontFamily,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { viewModel.executeImport() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("import_wizard_step3_execute"),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = (validCount + incompleteCount) > 0
                                ) {
                                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تأكيد استيراد السجلات (${validCount + incompleteCount} حركة)", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
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
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = AppSurface),
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
                                    text = summaryResult ?: "تم تسجيل وتحديث المخزون ومزامنته مع السحابة بنجاح.",
                                    fontSize = 12.5.sp,
                                    color = TextSecondary,
                                    fontFamily = CairoFontFamily
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = { viewModel.navigateTo(AppScreen.MOVEMENTS) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("عرض حركات المخزون المسجلة", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { viewModel.navigateTo(AppScreen.STOCK) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, EmeraldPrimary)
                                ) {
                                    Icon(imageVector = Icons.Default.Inventory2, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("الذهاب إلى قائمة الأصناف والمخزون", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
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
    val isIncoming = row.movementType == "STOCK_IN"

    val (statusBg, statusBorder, statusText, statusLabel) = when (row.status) {
        ImportRowStatus.VALID -> Quadruple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF2E7D32), "جاهز 🟢")
        ImportRowStatus.INCOMPLETE -> Quadruple(Color(0xFFFFF3E0), StockLow, StockLow, "ناقص 🟠")
        ImportRowStatus.INVALID -> Quadruple(Color(0xFFFFEBEE), StockEmpty, StockEmpty, "غير صالح 🔴")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Type Badge
                    Surface(
                        color = if (isIncoming) MovementInColor.copy(alpha = 0.12f) else MovementOutColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isIncoming) MovementInColor.copy(alpha = 0.4f) else MovementOutColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isIncoming) "وارد" else "توزيع",
                            color = if (isIncoming) MovementInColor else MovementOutColor,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFontFamily,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "سطر #${row.rowIndex}: ${row.parsedDestination}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = CairoFontFamily
                    )
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, statusBorder.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = statusLabel,
                        color = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
                    text = "الصنف: ${row.productName}${if (row.variantName != null) " (${row.variantName})" else ""}",
                    fontSize = 11.5.sp,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CairoFontFamily
                )
                Text(
                    text = "${if (isIncoming) "+" else "−"}%,d نسخة (%d طرد)".format(row.parsedQuantity, row.parsedPackages),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncoming) MovementInColor else MovementOutColor,
                    fontFamily = CairoFontFamily
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "التاريخ: ${row.parsedDateFormatted}",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = CairoFontFamily
                )
                Text(
                    text = "المسؤول: ${row.parsedResponsible}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = CairoFontFamily
                )
            }

            if (row.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
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
                .size(26.dp)
                .background(
                    if (isCurrent) GoldPrimary else if (isActive) EmeraldPrimary else Color.LightGray.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$step",
                color = if (isCurrent) EmeraldDark else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CairoFontFamily
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 9.5.sp,
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
            .width(20.dp)
            .height(2.dp)
            .background(if (isActive) EmeraldPrimary else Color.LightGray.copy(alpha = 0.4f))
    )
}

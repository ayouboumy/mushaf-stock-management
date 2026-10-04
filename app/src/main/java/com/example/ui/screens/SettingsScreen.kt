package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.sync.CloudSyncDiagnostic
import com.example.ui.AppScreen
import com.example.ui.StockViewModel
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.StockEmpty
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allowNegativeStock by viewModel.allowNegativeStock.collectAsStateWithLifecycle()
    val orgName by viewModel.organizationName.collectAsStateWithLifecycle()
    val deptName by viewModel.departmentName.collectAsStateWithLifecycle()
    val syncDiagnostic by viewModel.syncDiagnostic.collectAsStateWithLifecycle()

    var showEditInstitutionDialog by remember { mutableStateOf(false) }
    var editOrgName by remember { mutableStateOf(orgName) }
    var editDeptName by remember { mutableStateOf(deptName) }

    var showClearDataDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Section: الهيئة والمؤسسة
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "الهيئة والمؤسسة الرسمية",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppSurface,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(orgName, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, fontFamily = CairoFontFamily, color = TextPrimary)
                                Text(deptName, fontSize = 12.sp, fontFamily = CairoFontFamily, color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                            }

                            IconButton(
                                onClick = {
                                    editOrgName = orgName
                                    editDeptName = deptName
                                    showEditInstitutionDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "تعديل", tint = TextSecondary, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }

        // Section: ضوابط وسياسات المخزون
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ضوابط وسياسات المخزون",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppSurface,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("السماح بالمخزون السالب", fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = CairoFontFamily, color = TextPrimary)
                                Text(
                                    text = "يمنع صرف كميات تفوق المتوفر لتفادي أخطاء الجرد.",
                                    fontSize = 11.5.sp,
                                    fontFamily = CairoFontFamily,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = allowNegativeStock,
                                onCheckedChange = { viewModel.setAllowNegativeStock(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = EmeraldPrimary,
                                    checkedTrackColor = EmeraldContainer
                                ),
                                modifier = Modifier.testTag("settings_allow_negative_stock_switch")
                            )
                        }
                    }
                }
            }
        }

        // Section: قاعدة البيانات والأدوات
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "قاعدة البيانات والوثائق",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppSurface,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Column {
                        val cloudSubtitle = when (syncDiagnostic) {
                            is CloudSyncDiagnostic.Connected -> "متصل بسحابة Firestore (default)"
                            is CloudSyncDiagnostic.DatabaseNotFound -> "⚠️ قاعدة بيانات Firestore محذوفة في Firebase"
                            is CloudSyncDiagnostic.PermissionDenied -> "⚠️ إذن الوصول مرفوض (قواعد Rules)"
                            is CloudSyncDiagnostic.NetworkError -> "تعذر الاتصال بالإنترنت"
                            else -> "إدارة هوية المسؤول والمزامنة السحابية"
                        }
                        SettingsRow(
                            icon = Icons.Default.AccountCircle,
                            title = "حساب المستخدم والمزامنة السحابية",
                            subtitle = cloudSubtitle,
                            onClick = { viewModel.navigateTo(AppScreen.USER_PROFILE_MANAGE) },
                            textColor = if (syncDiagnostic is CloudSyncDiagnostic.DatabaseNotFound) StockEmpty else TextPrimary
                        )
                        HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                        SettingsRow(
                            icon = Icons.Default.LocationCity,
                            title = "قاعدة بيانات وجهات التوزيع",
                            subtitle = "المساجد والمؤسسات الشريكة",
                            onClick = { viewModel.navigateTo(AppScreen.DESTINATIONS_MANAGE) }
                        )
                        HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                        SettingsRow(
                            icon = Icons.Default.History,
                            title = "سجل التدقيق والمراقبة",
                            subtitle = "توثيق العمليات والمشرفين",
                            onClick = { viewModel.navigateTo(AppScreen.AUDIT_LOG_VIEW) }
                        )
                        HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                        SettingsRow(
                            icon = Icons.Default.Rule,
                            title = "الأرصدة الافتتاحية",
                            subtitle = "تعديل رصيد الانطلاق للأصناف",
                            onClick = { viewModel.navigateTo(AppScreen.INITIAL_STOCK_FORM) }
                        )
                    }
                }
            }
        }

        // Section: النسخ الاحتياطي
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "النسخ الاحتياطي",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AppSurface,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "حفظ نسخة احتياطية مشفرة بصيغة JSON.",
                            fontSize = 12.sp,
                            fontFamily = CairoFontFamily,
                            color = TextSecondary
                        )

                        Button(
                            onClick = { viewModel.exportBackup(context) },
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("settings_export_backup_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير نسخة احتياطية (JSON)", fontSize = 12.5.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section: إعادة التعيين
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.DeleteForever,
                        title = "مسح كافة البيانات",
                        subtitle = "تصفير قاعدة البيانات بالكامل (محلياً وسحابياً)",
                        onClick = { showClearDataDialog = true },
                        textColor = StockEmpty
                    )
                }
            }
        }
    }

    // Edit Institution Dialog
    if (showEditInstitutionDialog) {
        AlertDialog(
            onDismissRequest = { showEditInstitutionDialog = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = AppSurface,
            title = {
                Text(
                    text = "تعديل بيانات الهيئة",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editOrgName,
                        onValueChange = { editOrgName = it },
                        label = { Text("اسم الوزارة / المؤسسة", fontFamily = CairoFontFamily) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_org_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = editDeptName,
                        onValueChange = { editDeptName = it },
                        label = { Text("المندوبية / القسم", fontFamily = CairoFontFamily) },
                        modifier = Modifier.fillMaxWidth().testTag("edit_dept_input"),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateInstitutionNames(editOrgName, editDeptName)
                        showEditInstitutionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("save_institution_btn")
                ) {
                    Text("حفظ البيانات", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditInstitutionDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFontFamily, color = TextSecondary)
                }
            }
        )
    }

    // Clear Data Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = AppSurface,
            title = {
                Text(
                    text = "مسح كافة البيانات",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = StockEmpty)
                )
            },
            text = {
                Text(
                    text = "سيتم حذف جميع الأصناف، الحركات، والوجهات بشكل نهائي. هل أنت متأكد؟",
                    fontFamily = CairoFontFamily,
                    fontSize = 12.5.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StockEmpty),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تأكيد المسح", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearDataDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تراجع", fontFamily = CairoFontFamily, color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            Column {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor, fontFamily = CairoFontFamily)
                Text(text = subtitle, fontSize = 11.sp, color = TextSecondary, fontFamily = CairoFontFamily)
            }
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
    }
}

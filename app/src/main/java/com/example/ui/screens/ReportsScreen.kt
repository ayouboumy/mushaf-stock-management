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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PeriodStockResult
import com.example.ui.AppScreen
import com.example.ui.StockViewModel
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MovementInColor
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val results by viewModel.periodReportResults.collectAsStateWithLifecycle()
    val fromMillis by viewModel.reportFromMillis.collectAsStateWithLifecycle()
    val toMillis by viewModel.reportToMillis.collectAsStateWithLifecycle()
    val allMovements by viewModel.allMovements.collectAsStateWithLifecycle()

    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var fromDateStr by remember { mutableStateOf(sdf.format(Date(fromMillis))) }
    var toDateStr by remember { mutableStateOf(sdf.format(Date(toMillis))) }

    val reportTemplates = listOf("وضعية المخزون الشاملة", "تقرير التوزيع للمساجد", "تقرير شحنات الوارد")
    var selectedTemplate by remember { mutableStateOf("وضعية المخزون الشاملة") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date Range Selector (Clean Workspace Block)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "الفترة ونوع التقرير",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = CairoFontFamily
                    )

                    // Date Inputs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = fromDateStr,
                            onValueChange = {
                                fromDateStr = it
                                val t = try { sdf.parse(it)?.time } catch (e: Exception) { null }
                                if (t != null) viewModel.reportFromMillis.value = t
                            },
                            label = { Text("من تاريخ", fontSize = 11.5.sp, fontFamily = CairoFontFamily) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f).testTag("report_from_date"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = AppBorder,
                                focusedContainerColor = AppSurface,
                                unfocusedContainerColor = AppSurface
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = toDateStr,
                            onValueChange = {
                                toDateStr = it
                                val t = try { sdf.parse(it)?.time } catch (e: Exception) { null }
                                if (t != null) viewModel.reportToMillis.value = t + 86400000L - 1
                            },
                            label = { Text("إلى تاريخ", fontSize = 11.5.sp, fontFamily = CairoFontFamily) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f).testTag("report_to_date"),
                            shape = RoundedCornerShape(10.dp),
                            textStyle = TextStyle(fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = AppBorder,
                                focusedContainerColor = AppSurface,
                                unfocusedContainerColor = AppSurface
                            ),
                            singleLine = true
                        )
                    }

                    // Template Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (tpl in reportTemplates) {
                            val isSelected = selectedTemplate == tpl
                            Surface(
                                onClick = { selectedTemplate = tpl },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) EmeraldContainer else AppSurface,
                                border = BorderStroke(1.dp, if (isSelected) EmeraldPrimary.copy(alpha = 0.3f) else AppBorder)
                            ) {
                                Text(
                                    text = tpl,
                                    fontSize = 11.5.sp,
                                    fontFamily = CairoFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) EmeraldPrimary else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    // Update & Generate Action
                    Button(
                        onClick = { viewModel.generateReport() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("report_generate_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استخراج وتحديث التقرير", fontSize = 13.5.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Export Actions Bar (PDF & Excel)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = { viewModel.exportReportPdf(context, selectedTemplate) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("report_export_pdf_button"),
                        shape = RoundedCornerShape(10.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, Color(0xFFDC2626))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير PDF", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                    }

                    Surface(
                        onClick = { viewModel.exportReportExcel(context, selectedTemplate) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("report_export_excel_button"),
                        shape = RoundedCornerShape(10.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, EmeraldPrimary)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.TableChart, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تصدير Excel", fontSize = 13.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        }
                    }
                }

                // Quick Import & Template Helper Row
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = AppSurface,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                            Text("استيراد شحنات أو توزيع من إكسل", fontSize = 12.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = { viewModel.downloadExcelTemplate(context) },
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                                    Text("نموذج Excel", fontSize = 11.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                }
                            }
                            Surface(
                                onClick = { viewModel.navigateTo(AppScreen.EXCEL_IMPORT_WIZARD) },
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldPrimary
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Text("استيراد", fontSize = 11.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Summary Totals Strip based on selected template
        item {
            when (selectedTemplate) {
                "تقرير التوزيع للمساجد" -> {
                    val outList = allMovements.filter { it.movementType == "STOCK_OUT" && it.dateMillis in fromMillis..toMillis }
                    val totalOutCopies = outList.sumOf { it.quantity }
                    val totalOutPkgs = outList.sumOf { it.packageCount }
                    val mosqueCount = outList.map { it.destinationName }.distinct().size

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("المساجد والوجهات", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("%,d".format(mosqueCount), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("إجمالي الموزع", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("−%,d".format(totalOutCopies), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = MovementOutColor)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("عدد الطرود", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("%,d".format(totalOutPkgs), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("عدد السندات", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("%,d".format(outList.size), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.ExtraBold, color = EmeraldPrimary)
                            }
                        }
                    }
                }

                "تقرير شحنات الوارد" -> {
                    val inList = allMovements.filter { it.movementType == "STOCK_IN" && it.dateMillis in fromMillis..toMillis }
                    val totalInCopies = inList.sumOf { it.quantity }
                    val totalInPkgs = inList.sumOf { it.packageCount }
                    val sourcesCount = inList.map { it.source }.distinct().size

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الموردون والمطابع", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("%,d".format(sourcesCount), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("إجمالي الوارد", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("+%,d".format(totalInCopies), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = MovementInColor)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("عدد الطرود", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("%,d".format(totalInPkgs), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("عدد الشحنات", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("%,d".format(inList.size), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.ExtraBold, color = EmeraldPrimary)
                            }
                        }
                    }
                }

                else -> {
                    val totalOpening = results.sumOf { it.openingStock }
                    val totalIn = results.sumOf { it.incoming }
                    val totalOut = results.sumOf { it.outgoing }
                    val totalClosing = results.sumOf { it.closingStock }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
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
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الافتتاحي", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("%,d".format(totalOpening), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الوارد", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("+%,d".format(totalIn), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = MovementInColor)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الموزع", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("−%,d".format(totalOut), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = MovementOutColor)
                            }
                            Box(modifier = Modifier.width(1.dp).height(22.dp).background(AppBorder))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("الختامي", fontSize = 11.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                Text("%,d".format(totalClosing), fontSize = 14.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.ExtraBold, color = EmeraldPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Details Results Table based on selected template
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = AppSurface,
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val tableTitle = when (selectedTemplate) {
                        "تقرير التوزيع للمساجد" -> "سجل توزيع المصاحف للمساجد خلال الفترة"
                        "تقرير شحنات الوارد" -> "سجل شحنات التوريد الواردة خلال الفترة"
                        else -> "تفاصيل حركة الأصناف للفترة المحددة"
                    }
                    Text(
                        text = tableTitle,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = CairoFontFamily
                    )

                    when (selectedTemplate) {
                        "تقرير التوزيع للمساجد" -> {
                            val outList = allMovements.filter { it.movementType == "STOCK_OUT" && it.dateMillis in fromMillis..toMillis }
                            if (outList.isEmpty()) {
                                Text(
                                    text = "لا توجد عمليات توزيع مسجلة خلال الفترة المحددة.",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    fontFamily = CairoFontFamily,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                outList.forEachIndexed { index, m ->
                                    if (index > 0) HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = m.destinationName.ifBlank { "مسجد غير محدد" },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                fontFamily = CairoFontFamily
                                            )
                                            Text(
                                                text = "−%,d نسخة".format(m.quantity),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MovementOutColor,
                                                fontFamily = CairoFontFamily
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("التاريخ: ${m.dateFormatted}", fontSize = 11.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                                            Text("الطرود: ${m.packageCount} | المستلم: ${m.responsiblePerson.ifBlank { "الإمام" }}", fontSize = 11.sp, color = TextMuted, fontFamily = CairoFontFamily)
                                        }
                                    }
                                }
                            }
                        }

                        "تقرير شحنات الوارد" -> {
                            val inList = allMovements.filter { it.movementType == "STOCK_IN" && it.dateMillis in fromMillis..toMillis }
                            if (inList.isEmpty()) {
                                Text(
                                    text = "لا توجد شحنات توريد مسجلة خلال الفترة المحددة.",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    fontFamily = CairoFontFamily,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                inList.forEachIndexed { index, m ->
                                    if (index > 0) HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = m.source.ifBlank { "مورد خارجي" },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                fontFamily = CairoFontFamily
                                            )
                                            Text(
                                                text = "+%,d نسخة".format(m.quantity),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MovementInColor,
                                                fontFamily = CairoFontFamily
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("التاريخ: ${m.dateFormatted}", fontSize = 11.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                                            Text("الطرود: ${m.packageCount} | المسؤول: ${m.responsiblePerson.ifBlank { "أمين المستودع" }}", fontSize = 11.sp, color = TextMuted, fontFamily = CairoFontFamily)
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            if (results.isEmpty()) {
                                Text(
                                    text = "لا توجد بيانات للفترة المحددة، يرجى الضغط على زر التحديث.",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    fontFamily = CairoFontFamily,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                results.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        HorizontalDivider(color = AppBorder, thickness = 0.8.dp)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.productName,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                fontFamily = CairoFontFamily
                                            )
                                            Text(
                                                text = "الرصيد: %,d نسخة".format(item.closingStock),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldPrimary,
                                                fontFamily = CairoFontFamily
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("افتتاحي: %,d".format(item.openingStock), fontSize = 11.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                                            Text("وارد: +%,d".format(item.incoming), fontSize = 11.sp, color = MovementInColor, fontFamily = CairoFontFamily)
                                            Text("موزع: −%,d".format(item.outgoing), fontSize = 11.sp, color = MovementOutColor, fontFamily = CairoFontFamily)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

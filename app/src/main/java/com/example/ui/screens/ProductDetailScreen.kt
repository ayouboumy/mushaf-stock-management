package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.AppScreen
import com.example.ui.StockViewModel
import com.example.ui.components.MovementItemCard
import com.example.ui.components.StockEvolutionChart
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MovementAdjColor
import com.example.ui.theme.MovementInColor
import com.example.ui.theme.MovementOutColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ProductDetailScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val product by viewModel.selectedProduct.collectAsStateWithLifecycle()
    val historyPoints by viewModel.stockEvolution.collectAsStateWithLifecycle()
    val allMovements by viewModel.allMovements.collectAsStateWithLifecycle()

    var showEditDialog by remember { mutableStateOf(false) }

    if (product == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AppBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("لم يتم تحديد أي صنف", color = TextMuted, fontFamily = CairoFontFamily)
        }
        return
    }

    val prod = product!!
    val prodMovements = allMovements.filter {
        it.productId == prod.productId && (prod.variantId == null || it.variantId == prod.variantId)
    }

    // Editable state holders
    var editNameArabic by remember(prod) { mutableStateOf(prod.productNameArabic) }
    var editCategory by remember(prod) { mutableStateOf(prod.category) }
    var editFormat by remember(prod) { mutableStateOf(prod.formatType) }
    var editVariantName by remember(prod) { mutableStateOf(prod.variantNameArabic) }
    var editMinStock by remember(prod) { mutableStateOf(prod.minimumStock.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        // Minimalist Top Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = AppSurface,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = TextPrimary
                            )
                        }
                        Text(
                            text = prod.displayName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CairoFontFamily,
                            color = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StockStatusBadge(status = prod.status)
                        IconButton(
                            onClick = {
                                editNameArabic = prod.productNameArabic
                                editCategory = prod.category
                                editFormat = prod.formatType
                                editVariantName = prod.variantNameArabic
                                editMinStock = prod.minimumStock.toString()
                                showEditDialog = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "تعديل الصنف",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(AppBorder))
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Main Stock Hero Number (Typography-First, No Heavy Card)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "المخزون المتوفر",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        fontFamily = CairoFontFamily
                    )

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "%,d".format(prod.currentStock),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontFamily = CairoFontFamily
                        )
                        Text(
                            text = "نسخة",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            fontFamily = CairoFontFamily,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    val packageCount = if (prod.packageQuantity > 0) prod.currentStock / prod.packageQuantity else 0
                    val packageRemainder = if (prod.packageQuantity > 0) prod.currentStock % prod.packageQuantity else 0
                    val pkgInfo = if (prod.packageQuantity > 0) {
                        "يعادل $packageCount طرد (${prod.packageQuantity} نسخة/طرد)" + if (packageRemainder > 0) " + $packageRemainder فردي" else ""
                    } else "حساب مباشر بالنسخ"

                    Text(
                        text = "$pkgInfo • الحد الأدنى للتنبيه: ${prod.minimumStock} نسخة",
                        fontSize = 12.sp,
                        color = TextMuted,
                        fontFamily = CairoFontFamily
                    )
                }
            }

            // Compact Metric Row (NOT 4 Separate Giant Cards)
            item {
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
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الوارد", fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                            Text("+%,d".format(prod.totalIncoming), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MovementInColor, fontFamily = CairoFontFamily)
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(AppBorder))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الموزع", fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                            Text("−%,d".format(prod.totalOutgoing), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MovementOutColor, fontFamily = CairoFontFamily)
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(AppBorder))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("التسويات", fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                            Text("%+d".format(prod.totalAdjustments), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MovementAdjColor, fontFamily = CairoFontFamily)
                        }

                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(AppBorder))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الافتتاحي", fontSize = 11.5.sp, color = TextSecondary, fontFamily = CairoFontFamily)
                            Text("%,d".format(prod.initialStock), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = CairoFontFamily)
                        }
                    }
                }
            }

            // Quick Operations Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { viewModel.navigateTo(AppScreen.STOCK_IN_FORM) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("detail_btn_stock_in"),
                        color = EmeraldPrimary,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("وارد (+)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = CairoFontFamily)
                        }
                    }

                    Surface(
                        onClick = { viewModel.navigateTo(AppScreen.STOCK_OUT_FORM) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("detail_btn_stock_out"),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = null, tint = MovementOutColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("صرف (−)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = CairoFontFamily)
                        }
                    }

                    Surface(
                        onClick = { viewModel.navigateTo(AppScreen.ADJUSTMENT_FORM) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("detail_btn_adjustment"),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = MovementAdjColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تسوية", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = CairoFontFamily)
                        }
                    }
                }
            }

            // Stock Evolution Chart
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "حركة تطور المخزون",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = CairoFontFamily
                    )
                    StockEvolutionChart(points = historyPoints)
                }
            }

            // Movements List
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "سجل العمليات الخاصة بهذا الصنف",
                        fontSize = 14.sp,
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
                            if (prodMovements.isEmpty()) {
                                Text(
                                    text = "لا توجد حركات مسجلة لهذا الصنف",
                                    fontSize = 12.5.sp,
                                    color = TextMuted,
                                    fontFamily = CairoFontFamily,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                prodMovements.take(6).forEachIndexed { index, m ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = AppBorder,
                                            thickness = 0.8.dp,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                    MovementItemCard(
                                        movement = m,
                                        onVoucherClick = { viewModel.showVoucher(m) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Product Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = AppSurface,
            title = {
                Text(
                    text = "تعديل بيانات الصنف",
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
                        value = editNameArabic,
                        onValueChange = { editNameArabic = it },
                        label = { Text("اسم الصنف (بالعربية)", fontFamily = CairoFontFamily) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = { Text("التصنيف", fontFamily = CairoFontFamily) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editFormat,
                        onValueChange = { editFormat = it },
                        label = { Text("الحجم / الرواية / الطبعة", fontFamily = CairoFontFamily) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = editMinStock,
                        onValueChange = { editMinStock = it.filter { c -> c.isDigit() } },
                        label = { Text("الحد الأدنى للتنبيه", fontFamily = CairoFontFamily) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val min = editMinStock.toIntOrNull() ?: prod.minimumStock
                        viewModel.updateProduct(
                            productId = prod.productId,
                            variantId = prod.variantId,
                            nameArabic = editNameArabic.trim(),
                            category = editCategory.trim(),
                            formatType = editFormat.trim(),
                            variantNameArabic = editVariantName.trim(),
                            minimumStock = min
                        )
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("edit_product_save_btn")
                ) {
                    Text("حفظ التغييرات", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إلغاء", fontFamily = CairoFontFamily, color = TextSecondary)
                }
            }
        )
    }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CalculatedProductStock
import com.example.ui.AppScreen
import com.example.ui.StockViewModel
import com.example.ui.components.AppHeader
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun InitialStockScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val stockList by viewModel.calculatedStockList.collectAsStateWithLifecycle()
    var editingItem by remember { mutableStateOf<CalculatedProductStock?>(null) }
    var editPackagesStr by remember { mutableStateOf("") }
    var editCopiesPerPkgStr by remember { mutableStateOf("10") }
    var editDirectTotalStr by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        AppHeader(
            title = "الأرصدة الافتتاحية للأصناف",
            subtitle = "ضبط وتعيين أعداد الطرود والمصاحف الأولية",
            showBackButton = true,
            onBackClick = { viewModel.navigateBack() },
            actions = {
                IconButton(onClick = { viewModel.navigateTo(AppScreen.ADD_PRODUCT_FORM) }) {
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة صنف جديد", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = AppSurface,
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Inventory, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "يتم احتساب الرصيد الافتتاحي بمعادلة: عدد الطرود × عدد المصاحف في كل طرد، أو بإدخال المجموع الإجمالي مباشرة.",
                            fontSize = 12.sp,
                            fontFamily = CairoFontFamily,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            items(stockList, key = { "${it.productId}_${it.variantId ?: 0}" }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    border = BorderStroke(1.dp, AppBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                fontFamily = CairoFontFamily,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "التصنيف: ${item.category} • سعة الطرد: ${item.packageQuantity} وحدات",
                                fontSize = 11.5.sp,
                                fontFamily = CairoFontFamily,
                                color = TextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("الرصيد الافتتاحي", fontSize = 10.sp, fontFamily = CairoFontFamily, color = TextSecondary)
                                    Text(
                                        "%,d".format(item.initialStock),
                                        fontSize = 16.sp,
                                        fontFamily = CairoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    editingItem = item
                                    val pkgUnits = if (item.packageQuantity > 0) item.packageQuantity else 10
                                    editCopiesPerPkgStr = pkgUnits.toString()
                                    editPackagesStr = if (pkgUnits > 0) (item.initialStock / pkgUnits).toString() else "0"
                                    editDirectTotalStr = item.initialStock.toString()
                                },
                                modifier = Modifier.testTag("edit_initial_stock_${item.productId}")
                            ) {
                                Surface(
                                    color = Color(0xFFF1F5F3),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل",
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Initial Stock Dialog
    if (editingItem != null) {
        val item = editingItem!!
        val pkgs = editPackagesStr.toIntOrNull() ?: 0
        val perPkg = editCopiesPerPkgStr.toIntOrNull() ?: 10
        val calculatedTotal = pkgs * perPkg

        Dialog(onDismissRequest = { editingItem = null }) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = BorderStroke(1.dp, AppBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "ضبط أرقام الرصيد الافتتاحي",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = CairoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                    )
                    Text(
                        text = "النوع: ${item.displayName}",
                        fontSize = 13.5.sp,
                        fontFamily = CairoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    // Package count & Copies per package
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = editPackagesStr,
                            onValueChange = {
                                editPackagesStr = it.filter { c -> c.isDigit() }
                                val p = it.toIntOrNull() ?: 0
                                editDirectTotalStr = (p * perPkg).toString()
                            },
                            label = { Text("عدد الطرود", fontFamily = CairoFontFamily) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("dialog_packages_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = editCopiesPerPkgStr,
                            onValueChange = {
                                editCopiesPerPkgStr = it.filter { c -> c.isDigit() }
                                val cpp = it.toIntOrNull() ?: 10
                                editDirectTotalStr = (pkgs * cpp).toString()
                            },
                            label = { Text("المصاحف/طرد", fontFamily = CairoFontFamily) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Direct Total Override
                    OutlinedTextField(
                        value = editDirectTotalStr,
                        onValueChange = { editDirectTotalStr = it.filter { c -> c.isDigit() } },
                        label = { Text("إجمالي الرصيد الأولي (نسخة) *", fontFamily = CairoFontFamily) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_initial_stock_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingItem = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إلغاء", fontFamily = CairoFontFamily)
                        }

                        Button(
                            onClick = {
                                val newStock = editDirectTotalStr.toIntOrNull() ?: calculatedTotal
                                viewModel.updateInitialStock(item.productId, item.variantId, newStock)
                                editingItem = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            modifier = Modifier.weight(1f).testTag("dialog_initial_stock_save"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("حفظ الأرقام", fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

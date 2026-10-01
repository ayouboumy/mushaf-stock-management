package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CalculatedProductStock
import com.example.data.model.StockStatus
import com.example.ui.AppScreen
import com.example.ui.StockViewModel
import com.example.ui.components.StockStatusBadge
import com.example.ui.theme.AppBackground
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.CairoFontFamily
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.StockEmpty
import com.example.ui.theme.StockLow
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StockScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val stockList by viewModel.filteredStockList.collectAsStateWithLifecycle()
    val searchQuery by viewModel.stockSearchQuery.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.stockCategoryFilter.collectAsStateWithLifecycle()
    val totalStockSum = stockList.sumOf { it.currentStock }

    val categories = listOf(
        "ALL" to "الكل",
        "المصحف المحمدي" to "المحمدي",
        "المصحف المجزأ" to "المجزأ",
        "مصحف التجويد" to "التجويد",
        "ترجمة وتفسير" to "المترجم",
        "طبعة برايل" to "برايل",
        "أجزاء وقراءات" to "الجيب"
    )

    Box(modifier = modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Stats & Title
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "المخزون",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFontFamily,
                                color = TextPrimary
                            )
                            Text(
                                text = "%,d نسخة • ${stockList.size} أصناف".format(totalStockSum),
                                fontSize = 12.5.sp,
                                fontFamily = CairoFontFamily,
                                color = TextMuted
                            )
                        }

                        // Subtle add button in header
                        Surface(
                            onClick = { viewModel.navigateTo(AppScreen.ADD_PRODUCT_FORM) },
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldPrimary,
                            modifier = Modifier.testTag("fab_add_product")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة صنف", tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("إضافة صنف", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CairoFontFamily)
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.stockSearchQuery.value = it },
                        placeholder = {
                            Text(
                                "البحث عن صنف...",
                                fontSize = 13.sp,
                                color = TextMuted,
                                fontFamily = CairoFontFamily
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "بحث",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.stockSearchQuery.value = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "مسح",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stock_search_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = AppSurface,
                            unfocusedContainerColor = AppSurface,
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = AppBorder
                        ),
                        textStyle = TextStyle(
                            fontFamily = CairoFontFamily,
                            fontSize = 13.5.sp,
                            color = TextPrimary
                        )
                    )
                }
            }

            // Subtle Horizontal Category Tabs (NOT giant pills)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { (catId, catLabel) ->
                        val isSelected = categoryFilter == catId || (catId == "ALL" && categoryFilter == "ALL")
                        Surface(
                            onClick = { viewModel.stockCategoryFilter.value = catId },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) EmeraldContainer else AppSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) EmeraldPrimary.copy(alpha = 0.3f) else AppBorder
                            )
                        ) {
                            Text(
                                text = catLabel,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) EmeraldPrimary else TextSecondary,
                                fontFamily = CairoFontFamily,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Clean Inventory Rows inside a single unified surface (NOT 10 Giant Cards)
            if (stockList.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "لا توجد أصناف مطابقة",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = CairoFontFamily
                            )
                            Button(
                                onClick = {
                                    viewModel.stockSearchQuery.value = ""
                                    viewModel.stockCategoryFilter.value = "ALL"
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("إعادة تعيين الفلتر", fontSize = 12.sp, fontFamily = CairoFontFamily, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = AppSurface,
                        border = BorderStroke(1.dp, AppBorder)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            stockList.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = AppBorder,
                                        thickness = 0.8.dp
                                    )
                                }
                                ProductInventoryRow(
                                    stockItem = item,
                                    onClick = { viewModel.selectProduct(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean Inventory Row (Minimalist, Hairline Separator, Product-First)
 */
@Composable
fun ProductInventoryRow(
    stockItem: CalculatedProductStock,
    onClick: () -> Unit
) {
    val progress = if (stockItem.minimumStock > 0) {
        (stockItem.currentStock.toFloat() / (stockItem.minimumStock * 2f)).coerceIn(0f, 1f)
    } else 0.5f

    val progressColor = when (stockItem.status) {
        StockStatus.AVAILABLE -> EmeraldPrimary
        StockStatus.LOW_STOCK -> StockLow
        StockStatus.OUT_OF_STOCK -> StockEmpty
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(14.dp)
            .testTag("product_stock_card_${stockItem.productId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Refined Thumbnail / Category Icon (44px)
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(8.dp),
            color = EmeraldContainer,
            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.15f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (stockItem.category.contains("أجزاء")) Icons.Default.AutoStories else Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Product Info Column
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stockItem.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = CairoFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "%,d".format(stockItem.currentStock),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when (stockItem.status) {
                            StockStatus.AVAILABLE -> TextPrimary
                            StockStatus.LOW_STOCK -> StockLow
                            StockStatus.OUT_OF_STOCK -> StockEmpty
                        },
                        fontFamily = CairoFontFamily
                    )
                    Text(
                        text = stockItem.unit,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontFamily = CairoFontFamily
                    )
                }
            }

            Text(
                text = "${stockItem.category} • الحد الأدنى ${stockItem.minimumStock}",
                fontSize = 11.5.sp,
                color = TextMuted,
                fontFamily = CairoFontFamily
            )

            // Clean Micro Progress Bar & Status Dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFECEFEF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(progressColor)
                    )
                }

                StockStatusBadge(status = stockItem.status)
            }
        }
    }
}

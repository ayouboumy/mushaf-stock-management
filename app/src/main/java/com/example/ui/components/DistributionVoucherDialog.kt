package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.StockMovementEntity
import com.example.data.model.CalculatedProductStock
import com.example.ui.theme.AppBorder
import com.example.ui.theme.AppSurface
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldBorder
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.TajawalFontFamily
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DistributionVoucherDialog(
    movement: StockMovementEntity,
    stockList: List<CalculatedProductStock> = emptyList(),
    productName: String = "",
    variantName: String? = null,
    orgName: String = "",
    deptName: String = "",
    destinationAddress: String = "إقليم الدريوش - جهة الشرق",
    onDismiss: () -> Unit,
    onExportPdf: () -> Unit
) {
    val matchedProduct = stockList.find { it.productId == movement.productId }
    val resolvedProdName = when {
        productName.isNotBlank() -> productName
        matchedProduct != null -> matchedProduct.displayName
        else -> "المصحف الشريف"
    }

    // Always render the formal voucher strictly from Right to Left (RTL)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                border = BorderStroke(1.dp, GoldBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Top Bar Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "معاينة السند الإداري الرسمي",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    fontFamily = TajawalFontFamily
                                )
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Formal Moroccan Administrative Document Container (Paper style)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .border(BorderStroke(1.5.dp, GoldBorder.copy(alpha = 0.7f)), RoundedCornerShape(10.dp)),
                        color = Color(0xFFFCFDFC),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // Official Administrative Header (الترويسة الإدارية الرسمية)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                // Right Side: Official Moroccan Administration Hierarchy
                                Column(
                                    modifier = Modifier.weight(1.3f),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = "المملكة المغربية",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontFamily = TajawalFontFamily
                                    )
                                    Text(
                                        text = "وزارة الأوقاف والشؤون الإسلامية",
                                        fontSize = 10.5.sp,
                                        color = EmeraldPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = TajawalFontFamily
                                    )
                                    Text(
                                        text = "المندوبية الإقليمية للشؤون الإسلامية بالدريوش",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldDark,
                                        fontFamily = TajawalFontFamily
                                    )
                                }

                                // Left Side: Sequential Voucher Number & Date
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Surface(
                                        color = EmeraldPrimary.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, GoldBorder.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "وصل تسليم رقم: ${movement.referenceNumber.ifBlank { "#${movement.id}" }}",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldPrimary,
                                            fontFamily = TajawalFontFamily,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "الدريوش في: ${movement.dateFormatted}",
                                        fontSize = 9.5.sp,
                                        color = TextSecondary,
                                        fontFamily = TajawalFontFamily
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Official Document Title Frame
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = EmeraldPrimary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "سـنـد تـوزيـع وتـسـلـيـم مـصـاحـف شـريـفـة",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = TajawalFontFamily,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Formal Body Content
                            Text(
                                text = "يشهد المكلف بالمستودع بالمندوبية الإقليمية بالدريوش بأنه تم تسليم المصاحف الشريفة للجهة المستفيدة المبينة أسفله:",
                                fontSize = 11.sp,
                                color = TextPrimary,
                                lineHeight = 17.sp,
                                fontFamily = TajawalFontFamily,
                                textAlign = TextAlign.Justify
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Formal Table Grid of Voucher Fields
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFD4E2D8)),
                                color = Color.White
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    VoucherItemRow("الجهة المستفيدة (المستلم):", movement.destinationName, isBold = true)
                                    HorizontalDivider(color = Color(0xFFF0F4F1), thickness = 0.8.dp)

                                    VoucherItemRow("نوع وصنف الجهة المستفيدة:", movement.destinationType)
                                    HorizontalDivider(color = Color(0xFFF0F4F1), thickness = 0.8.dp)

                                    val displayAddr = destinationAddress.ifBlank { "إقليم الدريوش - جهة الشرق" }
                                    VoucherItemRow("العنوان:", displayAddr)
                                    HorizontalDivider(color = Color(0xFFF0F4F1), thickness = 0.8.dp)

                                    val fullProdName = if (!variantName.isNullOrBlank()) "$resolvedProdName ($variantName)" else resolvedProdName
                                    VoucherItemRow("الصنف والطبعة المسلمة:", fullProdName)
                                    HorizontalDivider(color = Color(0xFFF0F4F1), thickness = 0.8.dp)

                                    VoucherItemRow(
                                        label = "الكمية المسلمة (بالأرقام):",
                                        value = "%,d نسخة".format(movement.quantity),
                                        isBold = true,
                                        valueColor = EmeraldPrimary
                                    )
                                    HorizontalDivider(color = Color(0xFFF0F4F1), thickness = 0.8.dp)

                                    VoucherItemRow(
                                        label = "الكمية المسلمة (بالحروف):",
                                        value = formatQuantityArabicWords(movement.quantity)
                                    )
                                    HorizontalDivider(color = Color(0xFFF0F4F1), thickness = 0.8.dp)

                                    VoucherItemRow("عدد الطرود / الكراتين:", if (movement.packageCount > 0) "${movement.packageCount} طرد" else "1 طرد")
                                    HorizontalDivider(color = Color(0xFFF0F4F1), thickness = 0.8.dp)

                                    VoucherItemRow("المكلف بالتوزيع والمستودع:", movement.responsiblePerson.ifBlank { "العتير محمد" })

                                    HorizontalDivider(color = Color(0xFFF0F4F1), thickness = 0.8.dp)
                                    VoucherItemRow("ملاحظات التسليم:", movement.notes.ifBlank { "تم التسليم بحالة سليمة ومطابقة للمواصفات الرسمية" })
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Legal confirmation clause
                            Text(
                                text = "أقر أنا الموقع أسفله بصفتي ممثلاً عن الجهة المستفيدة بتسلمي للمصاحف الشريفة المبينة أعلاه كاملة وسليمة.",
                                fontSize = 9.5.sp,
                                color = TextSecondary,
                                fontFamily = TajawalFontFamily,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // 2 Signature Boxes (Recipient on Right, Warehouse Officer on Left)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Right: Recipient
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFD4E2D8)),
                                    color = Color.White
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "توقيع وخاتم المستلم",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontFamily = TajawalFontFamily
                                        )
                                        Text(
                                            text = "ر.ب.ت.و: ...................",
                                            fontSize = 9.sp,
                                            color = TextMuted,
                                            fontFamily = TajawalFontFamily
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }

                                // Left: Warehouse Officer
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFFD4E2D8)),
                                    color = Color.White
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "المكلف بالمستودع والتسليم",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            fontFamily = TajawalFontFamily
                                        )
                                        Text(
                                            text = movement.responsiblePerson.ifBlank { "العتير محمد" },
                                            fontSize = 9.5.sp,
                                            color = TextSecondary,
                                            fontFamily = TajawalFontFamily
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dialog Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("إغلاق", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExportPdf,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("طباعة وتصدير PDF", fontWeight = FontWeight.Bold, fontFamily = TajawalFontFamily)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoucherItemRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            fontFamily = TajawalFontFamily
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
            fontFamily = TajawalFontFamily
        )
    }
}

/**
 * Converts numbers into formal Arabic administrative wording for official vouchers.
 */
fun formatQuantityArabicWords(quantity: Int): String {
    if (quantity <= 0) return "صفر نسخة"
    if (quantity == 1) return "نسخة واحدة"
    if (quantity == 2) return "نسختان اثنتان"

    val units = listOf("", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة")
    val teens = listOf("عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
    val tens = listOf("", "عشرة", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    val hundreds = listOf("", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة")
    val thousands = listOf("", "ألف", "ألفان", "ثلاثة آلاف", "أربعة آلاف", "خمسة آلاف", "ستة آلاف", "سبعة آلاف", "ثمانية آلاف", "تسعة آلاف")

    val parts = mutableListOf<String>()
    val th = quantity / 1000
    val remTh = quantity % 1000
    if (th in 1..9) {
        parts.add(thousands[th])
    } else if (th > 9) {
        parts.add("$th ألف")
    }

    val h = remTh / 100
    val remH = remTh % 100
    if (h in 1..9) {
        parts.add(hundreds[h])
    }

    if (remH in 1..9) {
        parts.add(units[remH])
    } else if (remH in 10..19) {
        parts.add(teens[remH - 10])
    } else if (remH in 20..99) {
        val u = remH % 10
        val t = remH / 10
        if (u > 0) {
            parts.add("${units[u]} و${tens[t]}")
        } else {
            parts.add(tens[t])
        }
    }

    val words = parts.joinToString(" و ")
    return if (words.isNotBlank()) "$words نسخة" else "$quantity نسخة"
}

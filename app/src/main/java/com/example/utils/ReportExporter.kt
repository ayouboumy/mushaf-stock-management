package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.entity.StockMovementEntity
import com.example.data.model.PeriodStockResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportExporter {

    /**
     * Generates a professional printable PDF stock status report.
     */
    fun generateStockReportPdf(
        context: Context,
        orgName: String,
        deptName: String,
        startDateFormatted: String,
        endDateFormatted: String,
        reportItems: List<PeriodStockResult>
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595x842 pt)
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        // Header Background
        paint.color = Color.rgb(15, 90, 62) // Emerald green
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Header Text (White)
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(orgName, 297f, 32f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText(deptName, 297f, 52f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = Color.rgb(251, 228, 179) // Warm gold
        canvas.drawText("تقرير وضعية وتوزيع المخزون", 297f, 75f, paint)

        // Subheader - Period & Date
        paint.color = Color.DKGRAY
        paint.textSize = 10f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الفترة: من $startDateFormatted إلى $endDateFormatted", 560f, 115f, paint)

        paint.textAlign = Paint.Align.LEFT
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        canvas.drawText("تاريخ الاستخراج: ${sdf.format(Date())}", 35f, 115f, paint)

        // Summary Statistics Box
        paint.color = Color.rgb(240, 247, 243)
        canvas.drawRect(35f, 130f, 560f, 175f, paint)
        paint.color = Color.rgb(15, 90, 62)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(35f, 130f, 560f, 175f, paint)
        paint.style = Paint.Style.FILL

        val totalOpening = reportItems.sumOf { it.openingStock }
        val totalIn = reportItems.sumOf { it.incoming }
        val totalOut = reportItems.sumOf { it.outgoing }
        val totalClosing = reportItems.sumOf { it.closingStock }

        paint.color = Color.BLACK
        paint.textSize = 9.5f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER

        canvas.drawText("الرصيد الأولي الإجمالي: %,d".format(totalOpening), 460f, 155f, paint)
        canvas.drawText("إجمالي الوارد: +%,d".format(totalIn), 335f, 155f, paint)
        canvas.drawText("إجمالي الموزع: -%,d".format(totalOut), 215f, 155f, paint)
        canvas.drawText("الرصيد النهائي: %,d".format(totalClosing), 95f, 155f, paint)

        // Table Header
        val tableTop = 195f
        paint.color = Color.rgb(220, 235, 227)
        canvas.drawRect(35f, tableTop, 560f, tableTop + 24f, paint)
        paint.color = Color.rgb(15, 90, 62)
        paint.style = Paint.Style.STROKE
        canvas.drawRect(35f, tableTop, 560f, tableTop + 24f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(15, 90, 62)
        paint.textSize = 9f
        paint.isFakeBoldText = true

        canvas.drawText("الصنف / النوع", 490f, tableTop + 16f, paint)
        canvas.drawText("الرصيد الأولي", 390f, tableTop + 16f, paint)
        canvas.drawText("الوارد (+)", 310f, tableTop + 16f, paint)
        canvas.drawText("الموزع (-)", 230f, tableTop + 16f, paint)
        canvas.drawText("التعديلات", 150f, tableTop + 16f, paint)
        canvas.drawText("الرصيد النهائي", 75f, tableTop + 16f, paint)

        // Table Rows
        var currentY = tableTop + 24f
        paint.isFakeBoldText = false
        paint.textSize = 8.5f

        for ((index, item) in reportItems.withIndex()) {
            if (currentY > 740f) break // Avoid overflowing single page

            // Alternating row color
            if (index % 2 == 1) {
                paint.color = Color.rgb(248, 250, 249)
                canvas.drawRect(35f, currentY, 560f, currentY + 20f, paint)
            }

            paint.color = Color.rgb(220, 225, 222)
            paint.strokeWidth = 0.5f
            canvas.drawLine(35f, currentY + 20f, 560f, currentY + 20f, paint)

            paint.color = Color.BLACK
            val nameDisplay = if (item.variantName.isNotBlank()) "${item.productName} (${item.variantName})" else item.productName
            canvas.drawText(nameDisplay, 490f, currentY + 14f, paint)
            canvas.drawText("%,d".format(item.openingStock), 390f, currentY + 14f, paint)

            paint.color = Color.rgb(22, 163, 74)
            canvas.drawText("+%,d".format(item.incoming), 310f, currentY + 14f, paint)

            paint.color = Color.rgb(220, 38, 38)
            canvas.drawText("-%,d".format(item.outgoing), 230f, currentY + 14f, paint)

            paint.color = Color.rgb(217, 119, 6)
            canvas.drawText("%+d".format(item.adjustments), 150f, currentY + 14f, paint)

            paint.color = Color.rgb(15, 90, 62)
            paint.isFakeBoldText = true
            canvas.drawText("%,d".format(item.closingStock), 75f, currentY + 14f, paint)
            paint.isFakeBoldText = false

            currentY += 20f
        }

        // Signatures area
        val sigY = 760f
        paint.color = Color.DKGRAY
        paint.textSize = 9.5f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("توقيع وتأشيرة المكلف بالمستودع:", 540f, sigY, paint)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("تأشيرة رئيس المصلحة / المدير:", 50f, sigY, paint)

        paint.color = Color.GRAY
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(370f, sigY + 30f, 540f, sigY + 30f, paint)
        canvas.drawLine(50f, sigY + 30f, 220f, sigY + 30f, paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "rapport_stock_${System.currentTimeMillis()}.pdf")
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        pdfDocument.close()

        return file
    }

    /**
     * Generates a printable Distribution Voucher (وصل تسليم / سند توزيع) PDF in formal Moroccan administrative style.
     */
    fun generateDistributionVoucherPdf(
        context: Context,
        orgName: String,
        deptName: String,
        movement: StockMovementEntity,
        productName: String,
        variantName: String?,
        destinationAddress: String = "إقليم الدريوش - جهة الشرق"
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595x842 pt)
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        val emeraldGreen = Color.rgb(15, 90, 62)
        val warmGold = Color.rgb(139, 100, 8)
        val lightBg = Color.rgb(250, 252, 250)
        val borderColor = Color.rgb(190, 210, 198)

        // Outer Decorative Double Border
        paint.color = lightBg
        canvas.drawRect(25f, 25f, 570f, 817f, paint)

        paint.color = emeraldGreen
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawRect(28f, 28f, 567f, 814f, paint)

        paint.color = warmGold
        paint.strokeWidth = 0.8f
        canvas.drawRect(32f, 32f, 563f, 810f, paint)
        paint.style = Paint.Style.FILL

        // 1. Administrative Header Hierarchy
        // Right side: Official Moroccan Institutions
        paint.textAlign = Paint.Align.RIGHT
        paint.color = Color.BLACK
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("المملكة المغربية", 545f, 75f, paint)

        paint.color = emeraldGreen
        paint.textSize = 10.5f
        canvas.drawText("وزارة الأوقاف والشؤون الإسلامية", 545f, 95f, paint)
        canvas.drawText("المندوبية الإقليمية للشؤون الإسلامية بالدريوش", 545f, 115f, paint)

        // Left side: Sequential Voucher Number & Date
        paint.textAlign = Paint.Align.LEFT
        val ref = if (movement.referenceNumber.isNotBlank()) movement.referenceNumber else "#${movement.id}"

        // Voucher number box
        paint.color = Color.rgb(235, 245, 239)
        canvas.drawRect(45f, 70f, 215f, 98f, paint)
        paint.color = emeraldGreen
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(45f, 70f, 215f, 98f, paint)
        paint.style = Paint.Style.FILL

        paint.color = emeraldGreen
        paint.textSize = 10.5f
        paint.isFakeBoldText = true
        canvas.drawText("وصل تسليم رقم: $ref", 55f, 88f, paint)

        paint.color = Color.DKGRAY
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        canvas.drawText("الدريوش في: ${movement.dateFormatted}", 55f, 115f, paint)

        // 2. Document Title Banner
        val bannerTop = 145f
        paint.color = emeraldGreen
        canvas.drawRect(45f, bannerTop, 550f, bannerTop + 32f, paint)

        paint.color = Color.WHITE
        paint.textSize = 13.5f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("سـنـد تـوزيـع وتـسـلـيـم مـصـاحـف شـريـفـة", 297f, bannerTop + 21f, paint)

        // Subtitle
        paint.color = Color.DKGRAY
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("يشهد المكلف بالمستودع بالمندوبية الإقليمية بالدريوش بأنه تم تسليم المصاحف الشريفة للجهة المستفيدة المبينة أسفله:", 545f, 200f, paint)

        // 3. Formal Data Table Box
        val tableTop = 215f
        val tableBottom = 530f
        paint.color = Color.WHITE
        canvas.drawRect(45f, tableTop, 550f, tableBottom, paint)
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawRect(45f, tableTop, 550f, tableBottom, paint)
        paint.style = Paint.Style.FILL

        // Table Rows Definition
        data class PdfVoucherRow(val label: String, val value: String, val isHighlight: Boolean = false)
        val fullProdName = if (!variantName.isNullOrBlank()) "$productName ($variantName)" else productName
        val displayAddr = destinationAddress.ifBlank { "إقليم الدريوش - جهة الشرق" }
        val rows = listOf(
            PdfVoucherRow("الجهة المستفيدة (المستلم):", movement.destinationName, true),
            PdfVoucherRow("نوع وصنف الجهة المستفيدة:", movement.destinationType),
            PdfVoucherRow("العنوان:", displayAddr),
            PdfVoucherRow("الصنف والطبعة المسلمة:", fullProdName),
            PdfVoucherRow("الكمية المسلمة (بالأرقام):", "%,d نسخة".format(movement.quantity), true),
            PdfVoucherRow("الكمية المسلمة (بالحروف):", formatQuantityWords(movement.quantity)),
            PdfVoucherRow("عدد الطرود / الكراتين:", if (movement.packageCount > 0) "${movement.packageCount} طرد" else "1 طرد"),
            PdfVoucherRow("المكلف بالتوزيع والمستودع:", movement.responsiblePerson.ifBlank { "المكلف بالمستودع والتسليم" }),
            PdfVoucherRow("ملاحظات التسليم:", movement.notes.ifBlank { "تم التسليم بحالة سليمة ومطابقة للمواصفات الرسمية" })
        )

        val rowHeight = (tableBottom - tableTop) / rows.size
        for ((idx, r) in rows.withIndex()) {
            val y = tableTop + idx * rowHeight

            // Alternating fill
            if (idx % 2 == 1) {
                paint.color = Color.rgb(246, 250, 247)
                canvas.drawRect(46f, y, 549f, y + rowHeight, paint)
            }

            // Row separator line
            if (idx > 0) {
                paint.color = borderColor
                paint.strokeWidth = 0.5f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(45f, y, 550f, y, paint)
                paint.style = Paint.Style.FILL
            }

            // Vertical divider between label and value
            paint.color = borderColor
            paint.strokeWidth = 0.5f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(380f, y, 380f, y + rowHeight, paint)
            paint.style = Paint.Style.FILL

            // Label (Right-aligned in RTL column)
            paint.color = Color.rgb(60, 70, 65)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(r.label, 535f, y + (rowHeight / 2) + 4f, paint)

            // Value
            paint.textAlign = Paint.Align.RIGHT
            paint.color = if (r.isHighlight) emeraldGreen else Color.BLACK
            paint.textSize = if (r.isHighlight) 10.5f else 9.5f
            paint.isFakeBoldText = r.isHighlight
            canvas.drawText(r.value, 365f, y + (rowHeight / 2) + 4f, paint)
        }

        // 4. Legal Acknowledgement
        val legalY = 560f
        paint.color = Color.DKGRAY
        paint.textSize = 9f
        paint.isFakeBoldText = false
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("أقر أنا الموقع أسفله بصفتي ممثلاً عن الجهة المستفيدة بتسلمي للمصاحف الشريفة المبينة أعلاه كاملة وسليمة.", 545f, legalY, paint)

        // 5. Signatures (2 Signature Boxes: Recipient on Right, Warehouse Officer on Left)
        val sigTop = 595f
        val sigBoxWidth = 190f
        val sigBoxHeight = 115f

        // Signature Box 1: Recipient (Right in RTL)
        val box1X = 330f
        paint.color = Color.WHITE
        canvas.drawRect(box1X, sigTop, box1X + sigBoxWidth, sigTop + sigBoxHeight, paint)
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(box1X, sigTop, box1X + sigBoxWidth, sigTop + sigBoxHeight, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("توقيع وخاتم المستلم", box1X + (sigBoxWidth / 2), sigTop + 24f, paint)
        paint.color = Color.GRAY
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        canvas.drawText("ر.ب.ت.و: .....................", box1X + (sigBoxWidth / 2), sigTop + 42f, paint)

        // Signature Box 2: Warehouse Officer (Left in RTL)
        val box2X = 75f
        paint.color = Color.WHITE
        canvas.drawRect(box2X, sigTop, box2X + sigBoxWidth, sigTop + sigBoxHeight, paint)
        paint.color = borderColor
        paint.style = Paint.Style.STROKE
        canvas.drawRect(box2X, sigTop, box2X + sigBoxWidth, sigTop + sigBoxHeight, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText("المكلف بالمستودع والتسليم", box2X + (sigBoxWidth / 2), sigTop + 24f, paint)
        paint.color = Color.DKGRAY
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText(movement.responsiblePerson.ifBlank { "المكلف بالمستودع والتسليم" }, box2X + (sigBoxWidth / 2), sigTop + 42f, paint)

        // Footer
        paint.color = Color.GRAY
        paint.textSize = 8f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("المندوبية الإقليمية للشؤون الإسلامية بالدريوش • منظومة إدارة وتوزيع المصحف الشريف", 297f, 790f, paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "bon_distribution_${movement.id}.pdf")
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        pdfDocument.close()

        return file
    }

    private fun formatQuantityWords(quantity: Int): String {
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
        if (th in 1..9) parts.add(thousands[th])
        else if (th > 9) parts.add("$th ألف")

        val h = remTh / 100
        val remH = remTh % 100
        if (h in 1..9) parts.add(hundreds[h])

        if (remH in 1..9) parts.add(units[remH])
        else if (remH in 10..19) parts.add(teens[remH - 10])
        else if (remH in 20..99) {
            val u = remH % 10
            val t = remH / 10
            if (u > 0) parts.add("${units[u]} و${tens[t]}") else parts.add(tens[t])
        }

        val words = parts.joinToString(" و ")
        return if (words.isNotBlank()) "$words نسخة" else "$quantity نسخة"
    }

    /**
     * Generates an Excel-compatible UTF-8 CSV file with BOM matching the selected report template.
     */
    /**
     * Generates an Excel-compatible UTF-8 CSV file with BOM matching the selected report template.
     */
    fun generateStockExcelCsv(
        context: Context,
        reportItems: List<PeriodStockResult>,
        movements: List<StockMovementEntity>,
        reportType: String = "وضعية المخزون الشاملة",
        startDateFormatted: String = "",
        endDateFormatted: String = "",
        productsMap: Map<Long, String> = emptyMap(),
        variantsMap: Map<Long, String> = emptyMap()
    ): File {
        val filePrefix = when (reportType) {
            "تقرير التوزيع للمساجد" -> "distribution_mosque"
            "تقرير شحنات الوارد" -> "receptions_stock"
            else -> "inventaire_stock"
        }
        val file = File(context.cacheDir, "${filePrefix}_${System.currentTimeMillis()}.csv")
        val fos = FileOutputStream(file)

        // Write UTF-8 BOM so Microsoft Excel recognizes Arabic correctly
        fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

        val writer = fos.bufferedWriter(Charsets.UTF_8)

        when (reportType) {
            "تقرير التوزيع للمساجد" -> {
                writer.write("تقرير توزيع المصاحف على المساجد والمؤسسات المستفيدة\n")
                if (startDateFormatted.isNotBlank() && endDateFormatted.isNotBlank()) {
                    writer.write("الفترة المعتمدة: من $startDateFormatted إلى $endDateFormatted\n")
                }
                writer.write("\n")
                writer.write("التاريخ,رقم السند / الحركة,اسم المسجد أو الوجهة,نوع الوجهة,الصنف,عدد الطرود,الكمية المسلمة (نسخ),المستلم / الإمام,رقم المرجع,ملاحظات\n")

                val outMovements = movements.filter { it.movementType == "STOCK_OUT" }
                var totalOutCopies = 0
                var totalOutPkgs = 0
                for (m in outMovements) {
                    totalOutCopies += m.quantity
                    totalOutPkgs += m.packageCount
                    val notes = (m.reason + " " + m.notes).trim()
                    val prodName = productsMap[m.productId] ?: "المصحف الشريف"
                    val varName = m.variantId?.let { variantsMap[it] }
                    val pName = if (!varName.isNullOrBlank()) "$prodName ($varName)" else prodName
                    writer.write("\"${m.dateFormatted}\",\"${m.id}\",\"${m.destinationName}\",\"${m.destinationType}\",\"$pName\",${m.packageCount},${m.quantity},\"${m.responsiblePerson}\",\"${m.referenceNumber}\",\"$notes\"\n")
                }
                writer.write("\n")
                writer.write("المجموع الإجمالي,,,,,${totalOutPkgs},${totalOutCopies},,,عدد المساجد والوجهات: ${outMovements.map { it.destinationName }.distinct().size}\n")
            }

            "تقرير شحنات الوارد" -> {
                writer.write("تقرير شحنات الوارد وتوريد المصاحف للمستودع\n")
                if (startDateFormatted.isNotBlank() && endDateFormatted.isNotBlank()) {
                    writer.write("الفترة المعتمدة: من $startDateFormatted إلى $endDateFormatted\n")
                }
                writer.write("\n")
                writer.write("التاريخ,رقم الشحنة,المصدر / المورد,الصنف,عدد الطرود,الكمية المستلمة (نسخ),المسؤول عن الاستلام,رقم السند,ملاحظات\n")

                val inMovements = movements.filter { it.movementType == "STOCK_IN" }
                var totalInCopies = 0
                var totalInPkgs = 0
                for (m in inMovements) {
                    totalInCopies += m.quantity
                    totalInPkgs += m.packageCount
                    val notes = (m.reason + " " + m.notes).trim()
                    val prodName = productsMap[m.productId] ?: "المصحف الشريف"
                    val varName = m.variantId?.let { variantsMap[it] }
                    val pName = if (!varName.isNullOrBlank()) "$prodName ($varName)" else prodName
                    writer.write("\"${m.dateFormatted}\",\"${m.id}\",\"${m.source}\",\"$pName\",${m.packageCount},${m.quantity},\"${m.responsiblePerson}\",\"${m.referenceNumber}\",\"$notes\"\n")
                }
                writer.write("\n")
                writer.write("المجموع الإجمالي,,,,${totalInPkgs},${totalInCopies},,,عدد الشحنات: ${inMovements.size}\n")
            }

            else -> {
                // Summary section: وضعية المخزون الشاملة
                writer.write("ملخص وضعية المخزون للفترة\n")
                if (startDateFormatted.isNotBlank() && endDateFormatted.isNotBlank()) {
                    writer.write("الفترة: من $startDateFormatted إلى $endDateFormatted\n")
                }
                writer.write("الصنف,النوع / اللغة,الرصيد الأولي,الوارد (+),الموزع (-),التعديلات,الرصيد النهائي\n")
                for (item in reportItems) {
                    writer.write("\"${item.productName}\",\"${item.variantName}\",${item.openingStock},${item.incoming},${item.outgoing},${item.adjustments},${item.closingStock}\n")
                }

                writer.write("\n\n")
                writer.write("سجل الحركات التفصيلي الكامل\n")
                writer.write("التاريخ,رقم الحركة,النوع,الصنف,الكمية,الطرود,الوجهة / المصدر,المسؤول,رقم المرجع,الملاحظات\n")

                for (m in movements) {
                    val typeStr = when (m.movementType) {
                        "STOCK_IN" -> "إدخال (وارد)"
                        "STOCK_OUT" -> "إخراج (موزع)"
                        "ADJUSTMENT" -> "تعديل مخزون"
                        else -> m.movementType
                    }
                    val place = if (m.movementType == "STOCK_IN") m.source else m.destinationName
                    val prodName = productsMap[m.productId] ?: "المصحف الشريف"
                    val varName = m.variantId?.let { variantsMap[it] }
                    val pName = if (!varName.isNullOrBlank()) "$prodName ($varName)" else prodName
                    val notes = (m.reason + " " + m.notes).trim()
                    writer.write("\"${m.dateFormatted}\",${m.id},\"$typeStr\",\"$pName\",${m.quantity},${m.packageCount},\"$place\",\"${m.responsiblePerson}\",\"${m.referenceNumber}\",\"$notes\"\n")
                }
            }
        }

        writer.flush()
        writer.close()
        return file
    }

    /**
     * Generates a printable PDF distribution report to mosques.
     */
    fun generateDistributionReportPdf(
        context: Context,
        orgName: String,
        deptName: String,
        startDateFormatted: String,
        endDateFormatted: String,
        movements: List<StockMovementEntity>,
        productsMap: Map<Long, String> = emptyMap(),
        variantsMap: Map<Long, String> = emptyMap()
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        // Header Background
        paint.color = Color.rgb(15, 90, 62)
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Header Text
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(orgName, 297f, 32f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText(deptName, 297f, 52f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = Color.rgb(251, 228, 179)
        canvas.drawText("تقرير توزيع المصاحف على المساجد والمؤسسات", 297f, 75f, paint)

        // Dates
        paint.color = Color.DKGRAY
        paint.textSize = 10f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الفترة: من $startDateFormatted إلى $endDateFormatted", 560f, 115f, paint)

        paint.textAlign = Paint.Align.LEFT
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        canvas.drawText("تاريخ الاستخراج: ${sdf.format(Date())}", 35f, 115f, paint)

        val outMovements = movements.filter { it.movementType == "STOCK_OUT" }
        val totalCopies = outMovements.sumOf { it.quantity }
        val totalPkgs = outMovements.sumOf { it.packageCount }
        val mosqueCount = outMovements.map { it.destinationName }.distinct().size

        // Summary Box
        paint.color = Color.rgb(240, 247, 243)
        canvas.drawRect(35f, 130f, 560f, 170f, paint)
        paint.color = Color.rgb(15, 90, 62)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(35f, 130f, 560f, 170f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("إجمالي المصاحف الموزعة: %,d نسخة".format(totalCopies), 450f, 154f, paint)
        canvas.drawText("عدد الطرود: %,d طرد".format(totalPkgs), 280f, 154f, paint)
        canvas.drawText("المساجد والوجهات: %,d مستفيد".format(mosqueCount), 110f, 154f, paint)

        // Table Header
        val tableTop = 185f
        paint.color = Color.rgb(220, 235, 227)
        canvas.drawRect(35f, tableTop, 560f, tableTop + 24f, paint)
        paint.color = Color.rgb(15, 90, 62)
        paint.textSize = 9f
        paint.isFakeBoldText = true

        canvas.drawText("التاريخ", 520f, tableTop + 16f, paint)
        canvas.drawText("المسجد / الوجهة المستفيدة", 390f, tableTop + 16f, paint)
        canvas.drawText("الصنف", 260f, tableTop + 16f, paint)
        canvas.drawText("الطرود", 175f, tableTop + 16f, paint)
        canvas.drawText("الكمية (نسخ)", 115f, tableTop + 16f, paint)
        canvas.drawText("المستلم", 55f, tableTop + 16f, paint)

        var currentY = tableTop + 24f
        paint.isFakeBoldText = false
        paint.textSize = 8.5f

        for ((index, m) in outMovements.withIndex()) {
            if (currentY > 740f) break

            if (index % 2 == 1) {
                paint.color = Color.rgb(248, 250, 249)
                canvas.drawRect(35f, currentY, 560f, currentY + 20f, paint)
            }

            paint.color = Color.rgb(220, 225, 222)
            paint.strokeWidth = 0.5f
            canvas.drawLine(35f, currentY + 20f, 560f, currentY + 20f, paint)

            paint.color = Color.BLACK
            canvas.drawText(m.dateFormatted, 520f, currentY + 14f, paint)

            val destDisp = if (m.destinationName.length > 25) m.destinationName.take(24) + "…" else m.destinationName
            canvas.drawText(destDisp, 390f, currentY + 14f, paint)

            val prodName = productsMap[m.productId] ?: "المصحف الشريف"
            val prodDisp = if (prodName.length > 18) prodName.take(17) + "…" else prodName
            canvas.drawText(prodDisp, 260f, currentY + 14f, paint)

            canvas.drawText("${m.packageCount}", 175f, currentY + 14f, paint)

            paint.color = Color.rgb(220, 38, 38)
            paint.isFakeBoldText = true
            canvas.drawText("%,d".format(m.quantity), 115f, currentY + 14f, paint)
            paint.isFakeBoldText = false

            paint.color = Color.DKGRAY
            val respDisp = if (m.responsiblePerson.length > 12) m.responsiblePerson.take(11) + "…" else m.responsiblePerson
            canvas.drawText(respDisp, 55f, currentY + 14f, paint)

            currentY += 20f
        }

        // Signatures area
        val sigY = 760f
        paint.color = Color.DKGRAY
        paint.textSize = 9.5f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("توقيع وتأشيرة المكلف بالتوزيع:", 540f, sigY, paint)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("تأشيرة رئيس المصلحة / المدير:", 50f, sigY, paint)

        paint.color = Color.GRAY
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(370f, sigY + 30f, 540f, sigY + 30f, paint)
        canvas.drawLine(50f, sigY + 30f, 220f, sigY + 30f, paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "rapport_distribution_${System.currentTimeMillis()}.pdf")
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        pdfDocument.close()
        return file
    }

    /**
     * Generates a printable PDF incoming stock shipments report.
     */
    fun generateIncomingReportPdf(
        context: Context,
        orgName: String,
        deptName: String,
        startDateFormatted: String,
        endDateFormatted: String,
        movements: List<StockMovementEntity>,
        productsMap: Map<Long, String> = emptyMap(),
        variantsMap: Map<Long, String> = emptyMap()
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
        }

        // Header Background
        paint.color = Color.rgb(15, 90, 62)
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Header Text
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(orgName, 297f, 32f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        canvas.drawText(deptName, 297f, 52f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = Color.rgb(251, 228, 179)
        canvas.drawText("تقرير شحنات الوارد وتوريد المصاحف للمستودع", 297f, 75f, paint)

        // Dates
        paint.color = Color.DKGRAY
        paint.textSize = 10f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("الفترة: من $startDateFormatted إلى $endDateFormatted", 560f, 115f, paint)

        paint.textAlign = Paint.Align.LEFT
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        canvas.drawText("تاريخ الاستخراج: ${sdf.format(Date())}", 35f, 115f, paint)

        val inMovements = movements.filter { it.movementType == "STOCK_IN" }
        val totalCopies = inMovements.sumOf { it.quantity }
        val totalPkgs = inMovements.sumOf { it.packageCount }

        // Summary Box
        paint.color = Color.rgb(240, 247, 243)
        canvas.drawRect(35f, 130f, 560f, 170f, paint)
        paint.color = Color.rgb(15, 90, 62)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(35f, 130f, 560f, 170f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.BLACK
        paint.textSize = 10f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("إجمالي المصاحف المستلمة: %,d نسخة".format(totalCopies), 450f, 154f, paint)
        canvas.drawText("عدد الطرود: %,d طرد".format(totalPkgs), 280f, 154f, paint)
        canvas.drawText("عدد الشحنات والتوريدات: %,d شحنة".format(inMovements.size), 110f, 154f, paint)

        // Table Header
        val tableTop = 185f
        paint.color = Color.rgb(220, 235, 227)
        canvas.drawRect(35f, tableTop, 560f, tableTop + 24f, paint)
        paint.color = Color.rgb(15, 90, 62)
        paint.textSize = 9f
        paint.isFakeBoldText = true

        canvas.drawText("التاريخ", 520f, tableTop + 16f, paint)
        canvas.drawText("المصدر / المطبعة / المورد", 390f, tableTop + 16f, paint)
        canvas.drawText("الصنف المستلم", 260f, tableTop + 16f, paint)
        canvas.drawText("الطرود", 175f, tableTop + 16f, paint)
        canvas.drawText("الكمية (نسخ)", 115f, tableTop + 16f, paint)
        canvas.drawText("المشرف", 55f, tableTop + 16f, paint)

        var currentY = tableTop + 24f
        paint.isFakeBoldText = false
        paint.textSize = 8.5f

        for ((index, m) in inMovements.withIndex()) {
            if (currentY > 740f) break

            if (index % 2 == 1) {
                paint.color = Color.rgb(248, 250, 249)
                canvas.drawRect(35f, currentY, 560f, currentY + 20f, paint)
            }

            paint.color = Color.rgb(220, 225, 222)
            paint.strokeWidth = 0.5f
            canvas.drawLine(35f, currentY + 20f, 560f, currentY + 20f, paint)

            paint.color = Color.BLACK
            canvas.drawText(m.dateFormatted, 520f, currentY + 14f, paint)

            val srcDisp = if (m.source.length > 25) m.source.take(24) + "…" else m.source
            canvas.drawText(srcDisp, 390f, currentY + 14f, paint)

            val prodName = productsMap[m.productId] ?: "المصحف الشريف"
            val prodDisp = if (prodName.length > 18) prodName.take(17) + "…" else prodName
            canvas.drawText(prodDisp, 260f, currentY + 14f, paint)

            canvas.drawText("${m.packageCount}", 175f, currentY + 14f, paint)

            paint.color = Color.rgb(22, 163, 74)
            paint.isFakeBoldText = true
            canvas.drawText("+%,d".format(m.quantity), 115f, currentY + 14f, paint)
            paint.isFakeBoldText = false

            paint.color = Color.DKGRAY
            val respDisp = if (m.responsiblePerson.length > 12) m.responsiblePerson.take(11) + "…" else m.responsiblePerson
            canvas.drawText(respDisp, 55f, currentY + 14f, paint)

            currentY += 20f
        }

        // Signatures area
        val sigY = 760f
        paint.color = Color.DKGRAY
        paint.textSize = 9.5f
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("توقيع وتأشيرة أمين المستودع:", 540f, sigY, paint)

        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("تأشيرة رئيس المصلحة:", 50f, sigY, paint)

        paint.color = Color.GRAY
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(370f, sigY + 30f, 540f, sigY + 30f, paint)
        canvas.drawLine(50f, sigY + 30f, 220f, sigY + 30f, paint)

        pdfDocument.finishPage(page)

        val file = File(context.cacheDir, "rapport_receptions_${System.currentTimeMillis()}.pdf")
        val outputStream = FileOutputStream(file)
        pdfDocument.writeTo(outputStream)
        outputStream.flush()
        outputStream.close()
        pdfDocument.close()
        return file
    }

    /**
     * Shares a file via Android system share sheet.
     */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, title))
    }
}

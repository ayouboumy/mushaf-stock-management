package com.example.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportRowRaw(
    val rowIndex: Int,
    val movementTypeRaw: String = "توزيع",
    val productNameRaw: String = "",
    val variantNameRaw: String = "",
    val quantityRaw: String = "",
    val packageRaw: String = "0",
    val dateRaw: String = "",
    val destinationRaw: String = "",
    val communeRaw: String = "",
    val responsibleRaw: String = "",
    val referenceRaw: String = "",
    val notesRaw: String = ""
)

enum class ImportRowStatus {
    VALID,          // صالح للاستيراد 🟢
    INCOMPLETE,     // بيانات ناقصة 🟠
    INVALID         // غير صالح 🔴
}

data class ValidatedImportRow(
    val rowIndex: Int,
    val status: ImportRowStatus,
    val issues: List<String>,
    val movementType: String, // STOCK_IN or STOCK_OUT
    val productName: String,
    val variantName: String?,
    val parsedDateFormatted: String,
    val parsedDateMillis: Long,
    val parsedDestination: String,
    val parsedCommune: String,
    val parsedQuantity: Int,
    val parsedPackages: Int,
    val parsedResponsible: String,
    val parsedReference: String,
    val parsedNotes: String
)

data class ImportSummary(
    val totalRows: Int,
    val validCount: Int,
    val incompleteCount: Int,
    val invalidCount: Int,
    val importedCount: Int = 0
)

object ExcelImportHelper {

    // Sheet presets from "توزيع المصحف المحمدي 2026.xlsx"
    val WORKBOOK_SHEETS = listOf(
        "المصحف المحمدي",
        "المصحف المجزأ",
        "الهدايا",
        "جزء عم",
        "جزء عم _ 5 أحزاب",
        "ضعاف البصر",
        "برايل",
        "المصحف الجيبي",
        "المصحف المترجم - الفرنسية",
        "المصحف المترجم - الإنجليزية",
        "المصحف المترجم - الإسبانية"
    )

    /**
     * Generates an official Excel/CSV Model Template that the user can download,
     * edit in Microsoft Excel / Google Sheets, and import back into the app!
     */
    fun generateExcelTemplate(context: Context): File {
        val file = File(context.cacheDir, "نموذج_استيراد_مخزون_المصاحف.csv")
        val fos = FileOutputStream(file)

        // Write UTF-8 BOM so Microsoft Excel opens Arabic text cleanly
        fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))

        val writer = fos.bufferedWriter(Charsets.UTF_8)

        // Header Row
        writer.write("\"رقم السطر\",\"نوع العملية (وارد/توزيع)\",\"اسم الصنف\",\"الصنف الفرعي / الحجم\",\"الكمية (نسخ)\",\"عدد الطرود\",\"التاريخ (YYYY-MM-DD)\",\"اسم المسجد أو المصدر\",\"الجماعة أو الإقليم\",\"المسؤول أو المستلم\",\"رقم الوصل أو المرجع\",\"ملاحظات\"\n")

        // Example 1: Incoming Shipment (وارد)
        writer.write("\"1\",\"وارد\",\"المصحف المحمدي\",\"عادي\",\"1000\",\"100\",\"2026-10-01\",\"مطبعة فضالة - المحمدية\",\"إقليم الدريوش\",\"عبد الله اليعقوبي\",\"BL-2026-891\",\"دفعة التوريد السنوية للمستودع\"\n")

        // Example 2: Distribution to Mosque (توزيع)
        writer.write("\"2\",\"توزيع\",\"المصحف المحمدي\",\"عادي\",\"50\",\"5\",\"2026-10-02\",\"مسجد النصر\",\"جماعة الدريوش\",\"إمام المسجد\",\"REC-2026-001\",\"تسليم مباشر للإمام\"\n")

        // Example 3: Distribution to Quranic School (توزيع)
        writer.write("\"3\",\"توزيع\",\"المصحف المجزأ\",\"أجزاء\",\"100\",\"10\",\"2026-10-03\",\"مدرسة الإمام نافع للتعليم العتيق\",\"جماعة ميضار\",\"مدير المدرسة\",\"REC-2026-002\",\"لطلبة حفظ القرآن الكريم\"\n")

        // Blank rows ready for user data
        for (i in 4..20) {
            writer.write("\"$i\",\"توزيع\",\"المصحف المحمدي\",\"عادي\",\"\",\"\",\"2026-10-05\",\"\",\"\",\"\",\"\",\"\"\n")
        }

        writer.flush()
        writer.close()
        return file
    }

    /**
     * Generates sample sheet data for demo / presets.
     */
    fun getSampleWorkbookDataForSheet(sheetName: String): List<ImportRowRaw> {
        return listOf(
            ImportRowRaw(1, "توزيع", sheetName, "عادي", "150", "15", "2026-01-12", "مسجد النصر - تمارة", "تمارة", "الحسن بناني", "REC-01", "تزويد سنوي"),
            ImportRowRaw(2, "توزيع", sheetName, "عادي", "300", "30", "2026-01-18", "المجلس العلمي المحلي", "سلا", "عبد الحق العلمي", "REC-02", "برنامج المساجد"),
            ImportRowRaw(3, "توزيع", sheetName, "عادي", "80", "8", "2026-02-05", "مسجد بدر", "القنيطرة", "محمد الناصري", "REC-03", "تجديد المصاحف"),
            ImportRowRaw(4, "وارد", sheetName, "عادي", "500", "50", "2026-02-15", "مطبعة فضالة", "المحمدية", "أمين المستودع", "BL-102", "شحنة واردة"),
            ImportRowRaw(5, "توزيع", sheetName, "عادي", "100", "10", "", "مسجد الرحمة", "الصخيرات", "إدريس", "REC-04", "تاريخ غير محدد")
        )
    }

    /**
     * Splits a CSV/Delimited line while respecting quoted strings containing delimiters.
     */
    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    sb.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == delimiter && !inQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens.map { it.removeSurrounding("\"").trim() }
    }

    /**
     * Parses raw CSV or pasted text into rows with support for both the 12-column
     * official template and legacy 6-column formats.
     */
    fun parseDelimitedText(text: String): List<ImportRowRaw> {
        val cleanText = text.removePrefix("\uFEFF")
        val lines = cleanText.lines().filter { it.isNotBlank() }
        val rows = mutableListOf<ImportRowRaw>()

        for ((index, line) in lines.withIndex()) {
            val delimiter = if (line.contains("\t")) '\t' else if (line.contains(";")) ';' else ','
            val parts = splitCsvLine(line, delimiter)

            // Detect and skip header row
            val firstCell = parts.getOrNull(0) ?: ""
            val secondCell = parts.getOrNull(1) ?: ""
            val thirdCell = parts.getOrNull(2) ?: ""
            if (firstCell.contains("رقم") || firstCell.contains("نوع") || firstCell.contains("التاريخ") ||
                secondCell.contains("نوع") || secondCell.contains("الصنف") || thirdCell.contains("الصنف") ||
                firstCell.contains("index", ignoreCase = true) || firstCell.contains("type", ignoreCase = true)
            ) {
                continue
            }

            if (parts.size >= 7) {
                // Official 12-column template format:
                // [0: index, 1: type, 2: product, 3: variant, 4: qty, 5: pkg, 6: date, 7: destination/source, 8: commune, 9: resp, 10: ref, 11: notes]
                val typeRaw = parts.getOrNull(1) ?: "توزيع"
                val prodRaw = parts.getOrNull(2) ?: ""
                val varRaw = parts.getOrNull(3) ?: ""
                val qtyRaw = parts.getOrNull(4) ?: ""
                val pkgRaw = parts.getOrNull(5) ?: "0"
                val dateRaw = parts.getOrNull(6) ?: ""
                val destRaw = parts.getOrNull(7) ?: ""
                val comRaw = parts.getOrNull(8) ?: ""
                val respRaw = parts.getOrNull(9) ?: ""
                val refRaw = parts.getOrNull(10) ?: ""
                val notesRaw = parts.getOrNull(11) ?: ""

                rows.add(
                    ImportRowRaw(
                        rowIndex = rows.size + 1,
                        movementTypeRaw = typeRaw,
                        productNameRaw = prodRaw,
                        variantNameRaw = varRaw,
                        quantityRaw = qtyRaw,
                        packageRaw = pkgRaw,
                        dateRaw = dateRaw,
                        destinationRaw = destRaw,
                        communeRaw = comRaw,
                        responsibleRaw = respRaw,
                        referenceRaw = refRaw,
                        notesRaw = notesRaw
                    )
                )
            } else {
                // Simplified / Legacy 6-column format:
                // [0: date, 1: destination, 2: qty, 3: pkg, 4: responsible, 5: notes]
                val dateRaw = parts.getOrNull(0) ?: ""
                val destRaw = parts.getOrNull(1) ?: ""
                val qtyRaw = parts.getOrNull(2) ?: ""
                val pkgRaw = parts.getOrNull(3) ?: "0"
                val respRaw = parts.getOrNull(4) ?: ""
                val notesRaw = parts.getOrNull(5) ?: ""

                rows.add(
                    ImportRowRaw(
                        rowIndex = rows.size + 1,
                        movementTypeRaw = "توزيع",
                        productNameRaw = "",
                        variantNameRaw = "",
                        quantityRaw = qtyRaw,
                        packageRaw = pkgRaw,
                        dateRaw = dateRaw,
                        destinationRaw = destRaw,
                        communeRaw = "",
                        responsibleRaw = respRaw,
                        referenceRaw = "",
                        notesRaw = notesRaw
                    )
                )
            }
        }
        return rows
    }

    /**
     * Validates raw rows against inventory business rules with clear Arabic diagnostics.
     */
    fun validateRows(rows: List<ImportRowRaw>): List<ValidatedImportRow> {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val defaultDateFormatted = sdf.format(Date(now))
        val results = mutableListOf<ValidatedImportRow>()

        for (row in rows) {
            // Skip entirely empty rows from template/spreadsheet blanks
            val isEntirelyEmpty = row.quantityRaw.isBlank() &&
                                  row.destinationRaw.isBlank() &&
                                  (row.productNameRaw.isBlank() || row.productNameRaw == "المصحف المحمدي") &&
                                  row.responsibleRaw.isBlank() &&
                                  row.referenceRaw.isBlank() &&
                                  row.notesRaw.isBlank()
            if (isEntirelyEmpty) continue

            val issues = mutableListOf<String>()

            // 1. Movement Type
            val isIncoming = row.movementTypeRaw.contains("وارد") ||
                             row.movementTypeRaw.contains("إدخال") ||
                             row.movementTypeRaw.contains("IN", ignoreCase = true)
            val movementType = if (isIncoming) "STOCK_IN" else "STOCK_OUT"

            // 2. Quantity validation
            val qty = row.quantityRaw.trim().replace(",", "").replace(" ", "").toIntOrNull()
            if (qty == null) {
                issues.add("الكمية غير رقمية أو فارغة")
            } else if (qty <= 0) {
                issues.add("الكمية يجب أن تكون أكبر من صفر")
            }

            // 3. Destination or Source validation
            val place = row.destinationRaw.trim()
            if (place.isBlank()) {
                issues.add(if (isIncoming) "المصدر / المورد غير محدد" else "اسم المسجد أو الوجهة غير محدد")
            }

            // 4. Date validation
            val dateStr = row.dateRaw.trim()
            var dateMillis = now
            var formattedDate = defaultDateFormatted

            if (dateStr.isBlank()) {
                issues.add("التاريخ مفقود (سيتم تعيين تاريخ اليوم)")
            } else {
                formattedDate = dateStr
                try {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)
                        ?: SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).parse(dateStr)
                        ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateStr)
                        ?: SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(dateStr)
                    if (parsed != null) {
                        dateMillis = parsed.time
                    }
                } catch (e: Exception) {
                    // keep default dateMillis
                }
            }

            val pkg = row.packageRaw.trim().toIntOrNull() ?: 0

            val status = when {
                qty == null || qty <= 0 -> ImportRowStatus.INVALID
                place.isBlank() || dateStr.isBlank() -> ImportRowStatus.INCOMPLETE
                else -> ImportRowStatus.VALID
            }

            val prodName = row.productNameRaw.trim().ifBlank { "المصحف المحمدي" }
            val fallbackPlace = if (place.isBlank()) (if (isIncoming) "مورد غير محدد" else "مسجد غير محدد") else place

            results.add(
                ValidatedImportRow(
                    rowIndex = row.rowIndex,
                    status = status,
                    issues = issues,
                    movementType = movementType,
                    productName = prodName,
                    variantName = row.variantNameRaw.trim().ifBlank { null },
                    parsedDateFormatted = formattedDate,
                    parsedDateMillis = dateMillis,
                    parsedDestination = fallbackPlace,
                    parsedCommune = row.communeRaw.trim().ifBlank { "إقليم الدريوش" },
                    parsedQuantity = qty ?: 0,
                    parsedPackages = pkg,
                    parsedResponsible = row.responsibleRaw.trim().ifBlank { "المشرف على التوزيع" },
                    parsedReference = row.referenceRaw.trim().ifBlank { "IMP-${row.rowIndex}" },
                    parsedNotes = row.notesRaw.trim()
                )
            )
        }

        return results
    }
}

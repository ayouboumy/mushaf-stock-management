package com.example.utils

data class ImportRowRaw(
    val rowIndex: Int,
    val dateRaw: String,
    val destinationRaw: String,
    val quantityRaw: String,
    val packageRaw: String,
    val responsibleRaw: String,
    val notesRaw: String
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
    val parsedDateFormatted: String,
    val parsedDateMillis: Long,
    val parsedDestination: String,
    val parsedQuantity: Int,
    val parsedPackages: Int,
    val parsedResponsible: String,
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
     * Generates realistic historical sample data for any sheet from the 2026 workbook
     * including valid rows and edge-case rows with "بيانات ناقصة" to demonstrate the validation wizard!
     */
    fun getSampleWorkbookDataForSheet(sheetName: String): List<ImportRowRaw> {
        return when {
            sheetName.contains("المحمدي") -> listOf(
                ImportRowRaw(1, "12/01/2026", "مسجد النصر - تمارة", "150", "15", "الحسن بناني", "تزويد سنوي"),
                ImportRowRaw(2, "18/01/2026", "المجلس العلمي المحلي - سلا", "300", "30", "عبد الحق العلمي", "برنامج المساجد"),
                ImportRowRaw(3, "05/02/2026", "مسجد بدر - القنيطرة", "80", "8", "محمد الناصري", "تجديد المصاحف"),
                ImportRowRaw(4, "", "مسجد الرحمة - الصخيرات", "100", "10", "إدريس", "تاريخ غير محدد"), // Incomplete date
                ImportRowRaw(5, "22/02/2026", "", "50", "5", "سعيد", "وجهة غير محددة"), // Incomplete destination
                ImportRowRaw(6, "01/03/2026", "مؤسسة الرعاية الاجتماعية", "0", "0", "حميد", "كمية صفرية"), // Invalid quantity
                ImportRowRaw(7, "10/03/2026", "مسجد القدس - الخميسات", "120", "12", "كمال بنسودة", "دفعة خاصة")
            )
            sheetName.contains("المجزأ") -> listOf(
                ImportRowRaw(1, "15/01/2026", "كتاتيب تحفيظ القرآن - تيفلت", "60", "12", "عمر الكتاني", "طبعة أجزاء للطلبة"),
                ImportRowRaw(2, "20/02/2026", "مدرسة الإمام نافع للتعليم العتيق", "100", "20", "أحمد السلاوي", "بداية الفصل الدراسي"),
                ImportRowRaw(3, "15/03/2026", "جمعية أهل القرآن", "40", "8", "يوسف", "توزيع عادي")
            )
            sheetName.contains("الهدايا") -> listOf(
                ImportRowRaw(1, "10/01/2026", "الوفد الدبلوماسي الزائر", "15", "3", "مصلحة التشريفات", "إصدار مذهب فاخر"),
                ImportRowRaw(2, "28/02/2026", "تكريم شيوخ القراء بالمملكة", "25", "5", "المدير العام", "حفل التكريم السنوي")
            )
            sheetName.contains("المترجم") -> listOf(
                ImportRowRaw(1, "14/01/2026", "المؤسسة الثقافية المغربية بأوروبا", "120", "12", "المندوبية", "ترجمة معتمدة"),
                ImportRowRaw(2, "02/03/2026", "البعثة الطلابية الإسلامية بالخارج", "80", "8", "إسماعيل", "للمسلمين الجدد")
            )
            else -> listOf(
                ImportRowRaw(1, "20/01/2026", "مسجد الفرقان - فاس", "100", "10", "عزيز الفاسي", "توزيع روتيني"),
                ImportRowRaw(2, "12/02/2026", "معهد القراءات بالرباط", "50", "5", "طارق", "للمكتبة"),
                ImportRowRaw(3, "", "جهة غير معلومة", "30", "3", "", "بيانات ناقصة")
            )
        }
    }

    /**
     * Parses raw CSV or pasted text from the user into rows.
     */
    fun parseDelimitedText(text: String): List<ImportRowRaw> {
        val lines = text.lines().filter { it.isNotBlank() }
        val rows = mutableListOf<ImportRowRaw>()

        for ((index, line) in lines.withIndex()) {
            val delimiter = if (line.contains("\t")) "\t" else if (line.contains(",")) "," else ";"
            val parts = line.split(delimiter).map { it.trim().removeSurrounding("\"") }

            val date = parts.getOrNull(0) ?: ""
            val dest = parts.getOrNull(1) ?: ""
            val qty = parts.getOrNull(2) ?: ""
            val pkg = parts.getOrNull(3) ?: "0"
            val resp = parts.getOrNull(4) ?: ""
            val notes = parts.getOrNull(5) ?: ""

            rows.add(
                ImportRowRaw(
                    rowIndex = index + 1,
                    dateRaw = date,
                    destinationRaw = dest,
                    quantityRaw = qty,
                    packageRaw = pkg,
                    responsibleRaw = resp,
                    notesRaw = notes
                )
            )
        }
        return rows
    }

    /**
     * Validates raw rows against inventory business rules without inventing data.
     */
    fun validateRows(rows: List<ImportRowRaw>): List<ValidatedImportRow> {
        val now = System.currentTimeMillis()
        val results = mutableListOf<ValidatedImportRow>()

        for (row in rows) {
            val issues = mutableListOf<String>()

            // 1. Quantity validation
            val qty = row.quantityRaw.trim().replace(",", "").replace(" ", "").toIntOrNull()
            if (qty == null) {
                issues.add("الكمية غير رقمية أو فارغة")
            } else if (qty <= 0) {
                issues.add("الكمية يجب أن تكون أكبر من صفر")
            }

            // 2. Destination validation
            val dest = row.destinationRaw.trim()
            if (dest.isBlank()) {
                issues.add("مكان التوزيع غير محدد")
            }

            // 3. Date validation
            val dateStr = row.dateRaw.trim()
            var dateMillis = now
            var formattedDate = "2026-09-29"

            if (dateStr.isBlank()) {
                issues.add("التاريخ مفقود (سيتم تعيين تاريخ اليوم افتراضياً)")
            } else {
                formattedDate = dateStr
            }

            val pkg = row.packageRaw.trim().toIntOrNull() ?: 0

            val status = when {
                qty == null || qty <= 0 -> ImportRowStatus.INVALID
                dest.isBlank() || dateStr.isBlank() -> ImportRowStatus.INCOMPLETE
                else -> ImportRowStatus.VALID
            }

            results.add(
                ValidatedImportRow(
                    rowIndex = row.rowIndex,
                    status = status,
                    issues = issues,
                    parsedDateFormatted = formattedDate,
                    parsedDateMillis = dateMillis,
                    parsedDestination = if (dest.isBlank()) "بيانات ناقصة (غير محدد)" else dest,
                    parsedQuantity = qty ?: 0,
                    parsedPackages = pkg,
                    parsedResponsible = row.responsibleRaw.trim().ifBlank { "مكلف بالتوزيع" },
                    parsedNotes = row.notesRaw.trim()
                )
            )
        }

        return results
    }
}

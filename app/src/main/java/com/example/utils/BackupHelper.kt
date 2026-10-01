package com.example.utils

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.DestinationEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ProductVariantEntity
import com.example.data.entity.StockMovementEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object BackupHelper {

    suspend fun exportDatabaseToJson(context: Context, database: AppDatabase): File {
        val root = JSONObject()

        val products = database.productDao().getAllProductsList()
        val variants = database.productVariantDao().getAllVariantsList()
        val movements = database.stockMovementDao().getAllMovementsList()
        val destinations = database.destinationDao().getAllDestinationsList()

        // Products
        val prodArray = JSONArray()
        for (p in products) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("nameArabic", p.nameArabic)
            obj.put("nameFrench", p.nameFrench)
            obj.put("category", p.category)
            obj.put("language", p.language)
            obj.put("formatType", p.formatType)
            obj.put("unit", p.unit)
            obj.put("packageQuantity", p.packageQuantity)
            obj.put("minimumStock", p.minimumStock)
            obj.put("initialStock", p.initialStock)
            obj.put("notes", p.notes)
            obj.put("active", p.active)
            prodArray.put(obj)
        }
        root.put("products", prodArray)

        // Variants
        val varArray = JSONArray()
        for (v in variants) {
            val obj = JSONObject()
            obj.put("id", v.id)
            obj.put("productId", v.productId)
            obj.put("nameArabic", v.nameArabic)
            obj.put("nameFrench", v.nameFrench)
            obj.put("code", v.code)
            obj.put("initialStock", v.initialStock)
            obj.put("minimumStock", v.minimumStock)
            obj.put("packageQuantity", v.packageQuantity)
            varArray.put(obj)
        }
        root.put("variants", varArray)

        // Movements
        val movArray = JSONArray()
        for (m in movements) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("productId", m.productId)
            obj.put("variantId", m.variantId ?: JSONObject.NULL)
            obj.put("movementType", m.movementType)
            obj.put("quantity", m.quantity)
            obj.put("packageCount", m.packageCount)
            obj.put("dateMillis", m.dateMillis)
            obj.put("dateFormatted", m.dateFormatted)
            obj.put("destinationName", m.destinationName)
            obj.put("destinationType", m.destinationType)
            obj.put("source", m.source)
            obj.put("responsiblePerson", m.responsiblePerson)
            obj.put("referenceNumber", m.referenceNumber)
            obj.put("reason", m.reason)
            obj.put("notes", m.notes)
            obj.put("isReversed", m.isReversed)
            obj.put("isDeleted", m.isDeleted)
            movArray.put(obj)
        }
        root.put("movements", movArray)

        // Destinations
        val destArray = JSONArray()
        for (d in destinations) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("type", d.type)
            obj.put("commune", d.commune)
            obj.put("province", d.province)
            obj.put("address", d.address)
            obj.put("contactPerson", d.contactPerson)
            obj.put("phone", d.phone)
            destArray.put(obj)
        }
        root.put("destinations", destArray)

        root.put("backupTimestamp", System.currentTimeMillis())
        root.put("version", 1)

        val file = File(context.cacheDir, "sauvegarde_mushaf_${System.currentTimeMillis()}.json")
        val fos = FileOutputStream(file)
        fos.write(root.toString(2).toByteArray(Charsets.UTF_8))
        fos.flush()
        fos.close()

        return file
    }

    suspend fun restoreDatabaseFromJson(jsonString: String, database: AppDatabase): Boolean {
        try {
            val root = JSONObject(jsonString)

            database.stockMovementDao().clearAllMovements()
            database.productVariantDao().clearAllVariants()
            database.productDao().clearAllProducts()
            database.destinationDao().clearAllDestinations()
            database.auditLogDao().clearLogs()

            // Restore products
            val prodArray = root.optJSONArray("products")
            if (prodArray != null) {
                val products = mutableListOf<ProductEntity>()
                for (i in 0 until prodArray.length()) {
                    val obj = prodArray.getJSONObject(i)
                    products.add(
                        ProductEntity(
                            id = obj.optLong("id", 0),
                            nameArabic = obj.getString("nameArabic"),
                            nameFrench = obj.optString("nameFrench", ""),
                            category = obj.optString("category", "مصحف شريف"),
                            language = obj.optString("language", "العربية"),
                            formatType = obj.optString("formatType", "عادي"),
                            unit = obj.optString("unit", "نسخة"),
                            packageQuantity = obj.optInt("packageQuantity", 10),
                            minimumStock = obj.optInt("minimumStock", 50),
                            initialStock = obj.optInt("initialStock", 0),
                            notes = obj.optString("notes", ""),
                            active = obj.optBoolean("active", true)
                        )
                    )
                }
                database.productDao().insertAll(products)
            }

            // Restore variants
            val varArray = root.optJSONArray("variants")
            if (varArray != null) {
                val variants = mutableListOf<ProductVariantEntity>()
                for (i in 0 until varArray.length()) {
                    val obj = varArray.getJSONObject(i)
                    variants.add(
                        ProductVariantEntity(
                            id = obj.optLong("id", 0),
                            productId = obj.getLong("productId"),
                            nameArabic = obj.getString("nameArabic"),
                            nameFrench = obj.optString("nameFrench", ""),
                            code = obj.optString("code", ""),
                            initialStock = obj.optInt("initialStock", 0),
                            minimumStock = obj.optInt("minimumStock", 20),
                            packageQuantity = obj.optInt("packageQuantity", 10)
                        )
                    )
                }
                database.productVariantDao().insertAll(variants)
            }

            // Restore destinations
            val destArray = root.optJSONArray("destinations")
            if (destArray != null) {
                val destinations = mutableListOf<DestinationEntity>()
                for (i in 0 until destArray.length()) {
                    val obj = destArray.getJSONObject(i)
                    destinations.add(
                        DestinationEntity(
                            id = obj.optLong("id", 0),
                            name = obj.getString("name"),
                            type = obj.optString("type", "مسجد"),
                            commune = obj.optString("commune", ""),
                            province = obj.optString("province", ""),
                            address = obj.optString("address", ""),
                            contactPerson = obj.optString("contactPerson", ""),
                            phone = obj.optString("phone", "")
                        )
                    )
                }
                database.destinationDao().insertAll(destinations)
            }

            // Restore movements
            val movArray = root.optJSONArray("movements")
            if (movArray != null) {
                val movements = mutableListOf<StockMovementEntity>()
                for (i in 0 until movArray.length()) {
                    val obj = movArray.getJSONObject(i)
                    movements.add(
                        StockMovementEntity(
                            id = obj.optLong("id", 0),
                            productId = obj.getLong("productId"),
                            variantId = if (obj.isNull("variantId")) null else obj.getLong("variantId"),
                            movementType = obj.getString("movementType"),
                            quantity = obj.getInt("quantity"),
                            packageCount = obj.optInt("packageCount", 0),
                            dateMillis = obj.optLong("dateMillis", System.currentTimeMillis()),
                            dateFormatted = obj.optString("dateFormatted", "2026-09-29"),
                            destinationName = obj.optString("destinationName", ""),
                            destinationType = obj.optString("destinationType", ""),
                            source = obj.optString("source", ""),
                            responsiblePerson = obj.optString("responsiblePerson", ""),
                            referenceNumber = obj.optString("referenceNumber", ""),
                            reason = obj.optString("reason", ""),
                            notes = obj.optString("notes", ""),
                            isReversed = obj.optBoolean("isReversed", false),
                            isDeleted = obj.optBoolean("isDeleted", false)
                        )
                    )
                }
                database.stockMovementDao().insertAll(movements)
            }

            database.auditLogDao().insertLog(
                AuditLogEntity(
                    action = "BACKUP_RESTORE",
                    entityName = "استعادة النسخة الاحتياطية",
                    details = "تمت استعادة قاعدة البيانات من ملف النسخ الاحتياطي بنجاح"
                )
            )
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

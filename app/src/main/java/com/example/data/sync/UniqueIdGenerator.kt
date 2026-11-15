package com.example.data.sync

import android.content.Context
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicLong

/**
 * Generates globally unique, positive 53-bit-safe Long IDs for locally created Room entities.
 * Prevents cross-device / multi-user primary key collisions (id = 1, 2, 3...) when multiple users
 * create products, variants, movements, or destinations concurrently or while offline.
 */
object UniqueIdGenerator {
    private const val EPOCH_OFFSET_MS = 1_700_000_000_000L
    private const val PREFS_NAME = "mushaf_sync_state"
    private const val KEY_DEVICE_SALT = "device_node_salt"

    private val random = SecureRandom()
    private val lastGeneratedId = AtomicLong(0L)

    @Volatile
    private var cachedDeviceSalt: Int? = null

    fun init(context: Context?) {
        if (context == null || cachedDeviceSalt != null) return
        synchronized(this) {
            if (cachedDeviceSalt != null) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getInt(KEY_DEVICE_SALT, -1)
            cachedDeviceSalt = if (existing in 10..99) {
                existing
            } else {
                val created = 10 + random.nextInt(90)
                prefs.edit().putInt(KEY_DEVICE_SALT, created).apply()
                created
            }
        }
    }

    private fun getDeviceSalt(): Int {
        cachedDeviceSalt?.let { return it }
        val generated = 10 + random.nextInt(90)
        cachedDeviceSalt = generated
        return generated
    }

    fun nextId(): Long {
        val elapsedMs = (System.currentTimeMillis() - EPOCH_OFFSET_MS).coerceAtLeast(1L)
        val salt = getDeviceSalt()
        val randSuffix = random.nextInt(100)
        val candidate = (elapsedMs * 10_000L) + (salt * 100L) + randSuffix
        return lastGeneratedId.updateAndGet { prev ->
            if (candidate > prev) candidate else prev + 1L
        }
    }
}

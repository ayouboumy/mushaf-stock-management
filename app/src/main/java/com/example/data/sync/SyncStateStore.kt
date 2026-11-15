package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences

enum class SyncEntityType(val keyPrefix: String) {
    PRODUCT("products"),
    VARIANT("variants"),
    MOVEMENT("movements"),
    DESTINATION("destinations")
}

/**
 * Persists lightweight sync ownership & pending state per collection so sync merges can distinguish:
 * 1) Records previously synced with Firestore (safe to prune if deleted on server by another user)
 * 2) Records created/modified locally that have NOT yet been confirmed uploaded (NEVER prune locally)
 * 3) Records deleted locally whose cloud deletion is still pending retry
 */
class SyncStateStore(context: Context?) {

    private val prefs: SharedPreferences? = context?.applicationContext
        ?.getSharedPreferences("mushaf_sync_state", Context.MODE_PRIVATE)

    private val lock = Any()
    private val inMemorySets = mutableMapOf<String, MutableSet<Long>>()

    private fun loadSetLocked(key: String): MutableSet<Long> {
        val current = inMemorySets[key]
        if (current != null) return current

        val loaded = mutableSetOf<Long>()
        val raw = prefs?.getStringSet(key, emptySet()) ?: emptySet()
        for (item in raw) {
            item.toLongOrNull()?.let { loaded.add(it) }
        }
        inMemorySets[key] = loaded
        return loaded
    }

    private fun saveSetLocked(key: String, set: Set<Long>) {
        prefs?.edit()?.putStringSet(key, set.map { it.toString() }.toSet())?.apply()
    }

    private fun knownKey(type: SyncEntityType) = "known_${type.keyPrefix}"
    private fun pendingPushKey(type: SyncEntityType) = "pending_push_${type.keyPrefix}"
    private fun pendingDeleteKey(type: SyncEntityType) = "pending_delete_${type.keyPrefix}"

    fun markPendingPush(type: SyncEntityType, id: Long) {
        if (id <= 0L) return
        synchronized(lock) {
            val pushSet = loadSetLocked(pendingPushKey(type))
            if (pushSet.add(id)) {
                saveSetLocked(pendingPushKey(type), pushSet)
            }
            val delSet = loadSetLocked(pendingDeleteKey(type))
            if (delSet.remove(id)) {
                saveSetLocked(pendingDeleteKey(type), delSet)
            }
        }
    }

    fun markPushSucceeded(type: SyncEntityType, id: Long) {
        if (id <= 0L) return
        synchronized(lock) {
            val pushSet = loadSetLocked(pendingPushKey(type))
            if (pushSet.remove(id)) {
                saveSetLocked(pendingPushKey(type), pushSet)
            }
            val knownSet = loadSetLocked(knownKey(type))
            if (knownSet.add(id)) {
                saveSetLocked(knownKey(type), knownSet)
            }
        }
    }

    fun markKnownCloud(type: SyncEntityType, id: Long) {
        if (id <= 0L) return
        synchronized(lock) {
            val knownSet = loadSetLocked(knownKey(type))
            if (knownSet.add(id)) {
                saveSetLocked(knownKey(type), knownSet)
            }
        }
    }

    fun markRemovedFromCloud(type: SyncEntityType, id: Long) {
        if (id <= 0L) return
        synchronized(lock) {
            val knownSet = loadSetLocked(knownKey(type))
            if (knownSet.remove(id)) {
                saveSetLocked(knownKey(type), knownSet)
            }
            val pushSet = loadSetLocked(pendingPushKey(type))
            if (pushSet.remove(id)) {
                saveSetLocked(pendingPushKey(type), pushSet)
            }
        }
    }

    fun markPendingDelete(type: SyncEntityType, id: Long) {
        if (id <= 0L) return
        synchronized(lock) {
            val pushSet = loadSetLocked(pendingPushKey(type))
            if (pushSet.remove(id)) {
                saveSetLocked(pendingPushKey(type), pushSet)
            }
            val knownSet = loadSetLocked(knownKey(type))
            if (knownSet.remove(id)) {
                saveSetLocked(knownKey(type), knownSet)
            }
            val delSet = loadSetLocked(pendingDeleteKey(type))
            if (delSet.add(id)) {
                saveSetLocked(pendingDeleteKey(type), delSet)
            }
        }
    }

    fun markDeleteSucceeded(type: SyncEntityType, id: Long) {
        if (id <= 0L) return
        synchronized(lock) {
            val delSet = loadSetLocked(pendingDeleteKey(type))
            if (delSet.remove(id)) {
                saveSetLocked(pendingDeleteKey(type), delSet)
            }
            val knownSet = loadSetLocked(knownKey(type))
            if (knownSet.remove(id)) {
                saveSetLocked(knownKey(type), knownSet)
            }
        }
    }

    fun isKnownCloud(type: SyncEntityType, id: Long): Boolean = synchronized(lock) {
        loadSetLocked(knownKey(type)).contains(id)
    }

    fun isPendingPush(type: SyncEntityType, id: Long): Boolean = synchronized(lock) {
        loadSetLocked(pendingPushKey(type)).contains(id)
    }

    fun isPendingDelete(type: SyncEntityType, id: Long): Boolean = synchronized(lock) {
        loadSetLocked(pendingDeleteKey(type)).contains(id)
    }

    fun getPendingPushIds(type: SyncEntityType): Set<Long> = synchronized(lock) {
        loadSetLocked(pendingPushKey(type)).toSet()
    }

    fun getPendingDeleteIds(type: SyncEntityType): Set<Long> = synchronized(lock) {
        loadSetLocked(pendingDeleteKey(type)).toSet()
    }

    fun clearAll() {
        synchronized(lock) {
            val editor = prefs?.edit()
            for (type in SyncEntityType.entries) {
                val keys = listOf(knownKey(type), pendingPushKey(type), pendingDeleteKey(type))
                for (k in keys) {
                    inMemorySets[k]?.clear()
                    editor?.remove(k)
                }
            }
            editor?.apply()
        }
    }
}

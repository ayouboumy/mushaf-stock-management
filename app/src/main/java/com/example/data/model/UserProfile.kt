package com.example.data.model

data class UserProfile(
    val id: String = "",
    val fullName: String = "",
    val role: String = "",
    val email: String = "",
    val phone: String = "",
    val secretResetCode: String = "",
    val isAdmin: Boolean = false,
    val canDeleteData: Boolean = false,
    val isCloudSynced: Boolean = false,
    val lastSyncTime: Long = 0L,
    val isRegistered: Boolean = false
)

enum class CloudSyncState {
    IDLE_SYNCED,
    SYNCING,
    OFFLINE_ONLY,
    ERROR
}

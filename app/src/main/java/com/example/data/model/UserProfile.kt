package com.example.data.model

data class UserProfile(
    val id: String = "user_default",
    val fullName: String = "عبد الحق المرابط",
    val role: String = "المكلف بالمستودع والتسليم",
    val email: String = "abdellah@habous.gov.ma",
    val phone: String = "0661002233",
    val isCloudSynced: Boolean = true,
    val lastSyncTime: Long = System.currentTimeMillis()
)

enum class CloudSyncState {
    IDLE_SYNCED,
    SYNCING,
    OFFLINE_ONLY,
    ERROR
}

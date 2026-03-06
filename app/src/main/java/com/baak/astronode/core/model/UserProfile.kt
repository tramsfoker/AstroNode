package com.baak.astronode.core.model

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val role: String = "observer",        // observer | organizer | super_admin
    val organizationId: String? = null,
    val organizationName: String? = null,
    val totalMeasurements: Int = 0,
    val totalSessions: Int = 0,
    val bestMpsas: Double? = null,         // En karanlık ölçüm
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis(),
    val settings: UserSettings = UserSettings()
)

data class UserSettings(
    val theme: String = "auto",           // auto | dark | light
    val unit: String = "mpsas"            // mpsas | bortle
)

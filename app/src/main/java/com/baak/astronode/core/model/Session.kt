package com.baak.astronode.core.model

import java.util.UUID

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val date: Long = System.currentTimeMillis(),
    val organizerName: String = "Baak Bilim Kulübü",
    val participantCount: Int? = null,
    val createdBy: String = "",
    val isActive: Boolean = true
)
